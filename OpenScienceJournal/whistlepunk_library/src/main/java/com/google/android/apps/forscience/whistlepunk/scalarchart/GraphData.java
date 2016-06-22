/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.util.IndexXYMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public class GraphData {
    private static final String TAG = "GraphData";
    private final int DATA_INDEX = 0;
    private final int LABEL_INDEX = 1;
    private final int LEADING_EDGE_INDEX = 2;
    private final int ENDPOINTS_INDEX = 3;

    static public class ReadonlyDataPoint {
        private long mX;
        private double mY;

        public ReadonlyDataPoint(long x, double y) {
            mX = x;
            mY = y;
        }

        public long getX() {
            return mX;
        }

        public double getY() {
            return mY;
        }

        // Below are for testing only

        @VisibleForTesting
        @Override
        public String toString() {
            return "(" + mX + "," + mY + ")";
        }

        @VisibleForTesting
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ReadonlyDataPoint that = (ReadonlyDataPoint) o;

            if (mX != that.mX) return false;
            return (Double.compare(that.mY, mY) == 0);
        }

        @VisibleForTesting
        @Override
        public int hashCode() {
            int result;
            long temp;
            result = (int) (mX ^ (mX >>> 32));
            temp = Double.doubleToLongBits(mY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }

    private boolean mIsLive;

    // Track the raw data and labels separately so that they can be re-evaluated if
    // the data is re-graphed.
    private List<ReadonlyDataPoint> mRawData = new ArrayList();
    private List<? extends Label> mLabels = new ArrayList<>();

    private final XYSeries mDataSeries = new XYSeries("Main data");
    private final XYSeries mLabelSeries = new XYSeries("Labels");
    private final XYSeries mLeadingEdge = new XYSeries("Leading edge");
    private final XYSeries mEndpoints = new XYSeries("Endpoints");
    private final XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();

    // Labels that currently have a datapoint before, but no datapoint after, so the label is
    // shown, but interpolation is not complete.
    private List<Long> mPendingLabelTimestampsNeedAfter = new ArrayList<>();

    // Labels that currently have a datapoint after, but no datapoint before, so the label is
    // shown, but interpolation is not complete.
    private List<Long> mPendingLabelTimestampsNeedBefore = new ArrayList<>();

    // Labels added before any datapoints, so they are not even shown.
    private List<Long> mPendingLabelTimestampsNeedBoth = new ArrayList<>();

    // The number of points to average when smoothing.
    private int mWindow;

    private @ScalarDisplayOptions.BlurType
    int mBlurType;
    private double[] mFilter;

    // The standard deviation to use in the gaussian calculation.
    private float mGaussianSigma;

    private long mRecordingStart = RecordingMetadata.NOT_RECORDING;

    private static final int THROWAWAY_DATA_SIZE_THRESHOLD = 100;
    private int mThrowawayDataSizeThreshold = THROWAWAY_DATA_SIZE_THRESHOLD;

    @VisibleForTesting
    public GraphData(int throwawayDataSizeThreshold) {
        this(ScalarDisplayOptions.DEFAULT_WINDOW, ScalarDisplayOptions.DEFAULT_BLUR_TYPE,
                ScalarDisplayOptions.DEFAULT_GAUSSIAN_SIGMA, throwawayDataSizeThreshold);
    }

    @VisibleForTesting
    public GraphData(int windowSize, @ScalarDisplayOptions.BlurType int blurType, float sigma,
            int throwawayDataSizeThreshold) {
        this(windowSize, blurType, sigma);
        mThrowawayDataSizeThreshold = throwawayDataSizeThreshold;
    }

    public GraphData(int windowSize, @ScalarDisplayOptions.BlurType int blurType, float sigma) {
        mDataset.addSeries(DATA_INDEX, mDataSeries);
        mDataset.addSeries(LABEL_INDEX, mLabelSeries);
        mDataset.addSeries(LEADING_EDGE_INDEX, mLeadingEdge);
        mDataset.addSeries(ENDPOINTS_INDEX, mEndpoints);
        mWindow = windowSize;
        mBlurType = blurType;
        mGaussianSigma = sigma;
        setBlurKernel(blurType);
    }

    public void clear() {
        mDataSeries.clear();
        mRawData.clear();
        mRecordingStart = RecordingMetadata.NOT_RECORDING;
    }

    // Live graphs show their leading edge point. Graphs that are not live show their endpoints.
    public void setIsLive(boolean isLive) {
        mIsLive = isLive;
    }

    /**
     * @param labels new value for labels to keep track of.
     */
    public void setLabels(List<? extends Label> labels) {
        mLabels = labels;
        refreshLabels();
    }

    public void refreshLabels() {
        mLabelSeries.clear();
        for (final Label label : mLabels) {
            // Only add the labels which the user has created during the current recording session,
            // or if this is not a live graph then show all the labels.
            if (!mIsLive || isDisplayable(label, mRecordingStart,
                    mRecordingStart != RecordingMetadata.NOT_RECORDING)) {
                addLabelAtX(label.getTimeStamp());
            }
        }
    }

    // A label is displayable if:
    //   - It is not an "application" label (including recording start/stop)
    //   - It is after the first valid timestamp (if there is one)
    public static boolean isDisplayable(Label label, long firstValidTimestamp,
                                        boolean isRecording) {
        return isRecording && label.getTag() != ApplicationLabel.TAG &&
                label.getTimeStamp() >= firstValidTimestamp;
    }

    public void setRecording(long recordingStart) {
        mRecordingStart = recordingStart;
    }

    /**
     * The moment the user presses the + button is unlikely to be at the exact same moment
     * that a data point has been collected.  Therefore, we do a linear interpolation
     * between the data point collected immediately before the button press and the one
     * collected immediately after (with special cases for when either does not exist.)
     * <p/>
     * If there's not enough data to interpolate a point (we have a value for before, but
     * not after), then we remember the x value in order to re-interpolate when we have an after
     * value.
     */
    private void addLabelAtX(long x) {
        IndexXYMap<Double, Double> xyMap = mDataSeries.getXYMap();
        // Get the previous and next data points by time in the current data series.
        final Map.Entry<Double, Double> beforeEntry = xyMap.floorEntry((double) x);
        final Map.Entry<Double, Double> afterEntry = xyMap.ceilingEntry((double) x);

        if (beforeEntry == null && afterEntry == null) {
            mPendingLabelTimestampsNeedBoth.add(x);
            return;
        }

        if (afterEntry == null) {
            mPendingLabelTimestampsNeedAfter.add(x);
            addLabel(x, beforeEntry.getValue());
            return;
        }

        if (beforeEntry == null) {
            mPendingLabelTimestampsNeedBefore.add(x);
            addLabel(x, afterEntry.getValue());
            return;
        }

        double beforeY = beforeEntry.getValue();
        double beforeX = beforeEntry.getKey();
        double afterX = afterEntry.getKey();
        if (beforeX == afterX) {
            // Lucky user managed to select exactly a recorded timepoint.
            addLabel(x, beforeY);
            return;
        }

        // Ratio can vary from 0.0 = right at time before to 1.0 = right at time after.
        final double ratioBeforeToAfter = (x - beforeX) / (afterX - beforeX);

        double afterY = afterEntry.getValue();
        final double y = beforeY + ((afterY - beforeY) * ratioBeforeToAfter);
        addLabel(x, y);
    }

    private void addLabel(long x, double y) {
        mLabelSeries.add(x, y);
    }

    public boolean isEmpty() {
        return mDataSeries.getItemCount() == 0;
    }

    // Adds a new data point to the series based on the raw data at the current index.
    // Takes into account the size of the sliding window and averages raw data within
    // that window to produce data at the given index.
    private void updateDataSeriesAtIndex(int index) {
        double resultTimestamp = 0;
        double resultValue = 0;
        int windowEnd = index;
        int windowStart = Math.max(index - mWindow + 1, 0);
        int windowSize = windowEnd - windowStart + 1;

        if (windowSize == mWindow) {
            for (int i = windowStart; i <= windowEnd; i++) {
                double blurValue = mFilter[windowEnd - i];
                resultTimestamp += mRawData.get(i).getX() * blurValue;
                resultValue += mRawData.get(i).getY() * blurValue;
            }
        } else {
            // If there are fewer than WINDOW_SIZE points available, use as much of the filter
            // as possible and scale up by the remaining points.
            // usedScale tracks how much of the scaling factor we've already "used" when calculating
            // the result.
            // For example, if the window size is only 2 but the filter is [.5, .35, .10, .5],
            // usedScale will be .85, so the resultValue ends up being only .85 of what it should
            // be in total. Dividing by .85 gives that estimate back.
            double usedScale = 0;
            for (int i = windowStart; i <= windowEnd; i++) {
                double blurValue = mFilter[windowEnd - i];
                resultTimestamp += mRawData.get(i).getX() * blurValue;
                resultValue += mRawData.get(i).getY() * blurValue;
                usedScale += blurValue;
            }
            resultTimestamp *= (1 / usedScale);
            resultValue *= (1 / usedScale);
        }
        mDataSeries.add(index, resultTimestamp, resultValue);
    }

    /**
     * Add a new datapoint.  May re-interpolate any pending label positions.
     *
     * @return the timestamp of the _previous_ most recent data point.
     */
    public double addDataPoint(long timestampMillis, double value) {
        // Add all data to mRawData.
        // TODO: this does weird things when loading backwards, because it puts older values at
        // the end of rawdata
        mRawData.add(new ReadonlyDataPoint(timestampMillis, value));

        double lastMaxValue = mDataSeries.getMaxX();

        int index = mRawData.size() - 1;
        updateDataSeriesAtIndex(index);
        double addedTimestamp = mDataSeries.getX(index);

        // Remove the previous leading-edge dot at the previous timestamp, and add another.
        if (mIsLive && (mLeadingEdge.getItemCount() == 0 ||
                addedTimestamp > mLeadingEdge.getX(0))) {
            double addedValue = mDataSeries.getY(index);
            mLeadingEdge.clear();
            mLeadingEdge.add(addedTimestamp, addedValue);
        }

        recomputePendingLabels((long) addedTimestamp);
        return lastMaxValue;
    }

    // TODO: test
    public void updateEndpoints() {
        mEndpoints.clear();
        final Map.Entry<Double, Double> minEntry = mDataSeries.getXYMap().firstEntry();
        final Map.Entry<Double, Double> maxEntry = mDataSeries.getXYMap().lastEntry();
        mEndpoints.add(minEntry.getKey(), minEntry.getValue());
        mEndpoints.add(maxEntry.getKey(), maxEntry.getValue());
    }

    private void recomputePendingLabels(long timestampMillis) {
        if (!mPendingLabelTimestampsNeedBoth.isEmpty()) {
            addLabelsFromListAndClear(mPendingLabelTimestampsNeedBoth);
        }

        final List<Long> needBefore = mPendingLabelTimestampsNeedBefore;
        if (!needBefore.isEmpty() && needBefore.get(needBefore.size() - 1) > timestampMillis) {
            final int pendingCount = needBefore.size();

            for (int i = 0; i < pendingCount; i++) {
                if (mLabelSeries.getItemCount() == 0) {
                    return;
                }
                mLabelSeries.remove(0);
            }
            addLabelsFromListAndClear(needBefore);
        }

        final List<Long> needAfter = mPendingLabelTimestampsNeedAfter;
        if (!needAfter.isEmpty() && needAfter.get(0) < timestampMillis) {
            final int labelCount = mLabelSeries.getXYMap().size();
            final int pendingCount = needAfter.size();

            final int targetCount = labelCount - pendingCount;
            if (targetCount >= 0) {
                for (int i = labelCount - 1; i >= targetCount; i--) {
                    mLabelSeries.remove(i);
                }
            } else {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    // Not sure how this happens, but we're probably tearing down anyway:
                    // b/26545261
                    Log.e(TAG, "target annotation count below 0, labels may be stale");
                }
            }
            addLabelsFromListAndClear(needAfter);
        }
    }

    private void addLabelsFromListAndClear(List<Long> listToReadd) {
        List<Long> readd = Lists.newArrayList(listToReadd);
        listToReadd.clear();
        for (Long readdTimestamp : readd) {
            addLabelAtX(readdTimestamp);
        }
    }

    public SortedMap<Double, Double> getDataRange(double xMin, double xMax) {
        return mDataSeries.getRange(xMin, xMax, false);
    }

    public XYMultipleSeriesDataset getDataset() {
        return mDataset;
    }

    public List<? extends Label> getLabels() {
        return mLabels;
    }

    public double getMaxX() {
        return mDataSeries.getMaxX();
    }

    public double getMinX() {
        return mDataSeries.getMinX();
    }

    public ReadonlyDataPoint getClosestDataPointToTimestamp(long timestamp) {
        IndexXYMap<Double, Double> map = mDataSeries.getXYMap();
        Map.Entry<Double, Double> floor = map.floorEntry(Double.valueOf((double) timestamp));
        Map.Entry<Double, Double> ceil = map.ceilingEntry(Double.valueOf((double) timestamp));
        Map.Entry<Double, Double> result;
        // If either is null, return the other if possible.
        if (floor == null && ceil != null) {
            result = ceil;
        } else if (floor != null && ceil == null) {
            result = floor;
        } else if (floor == null && ceil == null) {
            // In this case, there are no data points loaded at all.
            return null;
        } else if (ceil.getKey() - timestamp < timestamp - floor.getKey()) {
            // Otherwise return the closest to the selected timestamp.
            result = ceil;
        } else {
            result = floor;
        }
        return new ReadonlyDataPoint(result.getKey().longValue(), result.getValue());
    }

    public int getIndexForTimestamp(long timestamp) {
        return mDataSeries.getIndexForKey(timestamp);
    }

    public int getLineDataIndex() {
        return DATA_INDEX;
    }

    public int getLabelDataIndex() {
        return LABEL_INDEX;
    }

    public int getLeadingEdgeIndex() {
        return LEADING_EDGE_INDEX;
    }

    public int getEndpointsIndex() {
        return ENDPOINTS_INDEX;
    }

    public void setWindowAndBlur(int windowSize, @ScalarDisplayOptions.BlurType int blurType,
                                 float gaussianSigma) {
        if (mWindow != windowSize || mBlurType != blurType || mGaussianSigma != gaussianSigma) {
            mWindow = windowSize;
            mGaussianSigma = gaussianSigma;
            setBlurKernel(blurType);
            mDataSeries.clear();
            int size = mRawData.size();
            for (int i = 0; i < size; i++) {
                updateDataSeriesAtIndex(i);
            }
            int dataSize = mDataSeries.getItemCount();
            if (mIsLive && dataSize > 0) {
                mLeadingEdge.clear();
                mLeadingEdge.add(mDataSeries.getX(dataSize - 1), mDataSeries.getY(dataSize - 1));
            }
            if (mLabels != null && mLabels.size() > 0) {
                setLabels(mLabels);
            }
        }
    }

    private void setBlurKernel(@ScalarDisplayOptions.BlurType int blurType) {
        mBlurType = blurType;
        mFilter = new double[mWindow];
        if (blurType == ScalarDisplayOptions.BLUR_TYPE_GAUSSIAN) {
            // A gaussian kernel is 1 / sqrt(2 * pi * sigma ^ 2) * e ^ (-x ^ 2 / (2 * pi * sigma)
            // In this case we are only using one side of the kernel, so we will normalize it at the
            // end.
            double multiplier = 1 / Math.sqrt(2 * Math.PI * Math.pow(mGaussianSigma, 2));
            double sum = 0;
            for (int i = 0; i < mWindow; i++) {
                double exponent = -1 * Math.pow(i, 2) / (2 * Math.pow(mGaussianSigma, 2));
                double result = multiplier * Math.pow(Math.E, exponent);
                mFilter[i] = result;
                sum += result;
            }
            // Normalize the filter.
            for (int i = 0; i < mWindow; i++) {
                mFilter[i] /= sum;
            }
        } else if (blurType == ScalarDisplayOptions.BLUR_TYPE_AVERAGE) {
            double value = 1.0 / mWindow;
            for (int i = 0; i < mWindow; i++) {
                mFilter[i] = value;
            }
        }
    }

    public List<ReadonlyDataPoint> getRawData() {
        return mRawData;
    }

    public void throwAwayBefore(long throwawayThreshhold) {
        throwAwayBetween(Long.MIN_VALUE, throwawayThreshhold);
    }

    public void throwAwayBetween(long throwAwayMinX, long throwAwayMaxX) {
        if (throwAwayMaxX <= throwAwayMinX) {
            return;
        }

        // Only throw away in bulk once we reach a threshold, so that all the work is not done on
        // every iteration.
        SortedMap<Double, Double> rangeToToss = mDataSeries.getRange(throwAwayMinX, throwAwayMaxX,
                false);
        if (rangeToToss.size() < mThrowawayDataSizeThreshold) {
            return;
        }

        // Must go through all data, because it may not be in order.
        Iterator<ReadonlyDataPoint> iterator = mRawData.iterator();
        while (iterator.hasNext()) {
            long x = iterator.next().mX;
            if (throwAwayMinX < x && x < throwAwayMaxX) {
                iterator.remove();
            }
        }
        rangeToToss.clear();
    }
}