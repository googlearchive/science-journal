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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.os.Build;
import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.CurrentTimeClock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.GraphPopulator;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.review.ZoomPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.List;

public class ChartController {
    /**
     * Interface for chart loading status object. Things that want to load chart data must
     * pass in an object that implements this interface, which is used to track the status
     * of the load and also whether the data loaded matches the requested data to load. Mis-matches
     * may occur if many load requests are issued to the same ChartController very quickly, which
     * may happen when a user quickly scrolls through sensors in a run or scrolls through a list of
     * runs in an experiment.
     */
    public interface ChartLoadingStatus {
        int GRAPH_LOAD_STATUS_IDLE = 0;
        int GRAPH_LOAD_STATUS_LOADING = 1;

        int getGraphLoadStatus();
        void setGraphLoadStatus(int graphLoadStatus);
        String getRunId();
        String getSensorId();
    }

    public interface ChartDataLoadedCallback {
        void onChartDataLoaded(long firstTimestamp, long lastTimestamp);

        /**
         * Called when the chart starts trying to load more data.
         * @param chartHiddenForLoad True if the graph is hidden for the load, like when a full
         *                           refresh is done when loading at a new zoom level. False if the
         *                           graph is not hidden, like when loading more data at the edges.
         */
        void onLoadAttemptStarted(boolean chartHiddenForLoad);
    }

    private static final String TAG = "ChartController";

    /**
     * How many (minimum) screenfuls of data should we keep in memory?
     * Note: If this constant is changed, the tests in ScalarSensorTest will need to be updated!
     */
    private static final int KEEP_THIS_MANY_SCREENS = 3;

    /**
     * How long can the screen be off before we forget old data?
     */
    private static final long MAX_BLACKOUT_MILLIS_BEFORE_CLEARING = 5000;

    private static final long DEFAULT_DATA_LOAD_BUFFER_MILLIS =
            ExternalAxisController.DEFAULT_GRAPH_RANGE_IN_MILLIS / 4;

    private final ChartData mChartData;
    private List<Label> mDisplayableLabels = new ArrayList<>();
    private ChartOptions mChartOptions;
    private ChartView mChartView;
    private ExternalAxisController.InteractionListener mInteractionListener;
    private ProgressBar mProgressView;

    // Fields used for data loading and clearing during Observe/Record
    private long mDefaultGraphRange;
    private long mDataLoadBuffer;
    private final FailureListener mDataFailureListener;
    private long mResetTime = -1;
    private String mSensorId;

    private ZoomPresenter mZoomPresenter;
    // Need to keep track of min/max loaded separately from what is in ChartData,
    // because repeated async callbacks adding data can cause data to be added after
    // that region was meant to be cleared, causing bugs. Therefore mMinLoadedX and mMaxLoadedX
    // are used to track the min and max we know we've loaded during RunReview, to assist in
    // data loading during zoom and pan.
    private static final long NOTHING_LOADED = -1;
    private long mMinLoadedX = NOTHING_LOADED;
    private long mMaxLoadedX;
    private boolean mNeedsForwardLoad = false;
    private List<Long> mCurrentLoadIds = new ArrayList<>();
    private final Clock mUptimeClock;
    private final Clock mCurrentTimeClock;
    private List<ChartDataLoadedCallback> mChartDataLoadedCallbacks = new ArrayList<>();

    public ChartController(ChartOptions.ChartPlacementType type,
            ScalarDisplayOptions lineGraphOptions, Clock uptimeClock) {
        this(type, lineGraphOptions, ChartData.DEFAULT_THROWAWAY_THRESHOLD,
                ChartController.DEFAULT_DATA_LOAD_BUFFER_MILLIS, uptimeClock);
    }

    public ChartController(ChartOptions.ChartPlacementType chartPlacementType,
            ScalarDisplayOptions scalarDisplayOptions) {
        this(chartPlacementType, scalarDisplayOptions, ChartData.DEFAULT_THROWAWAY_THRESHOLD,
                DEFAULT_DATA_LOAD_BUFFER_MILLIS, new UptimeClock());
    }

    @VisibleForTesting
    public ChartController(ChartOptions.ChartPlacementType chartPlacementType,
            ScalarDisplayOptions scalarDisplayOptions, int chartDataThrowawayThreshold,
            long dataLoadBuffer, Clock uptimeClock) {
        this(chartPlacementType, scalarDisplayOptions, chartDataThrowawayThreshold, dataLoadBuffer,
                uptimeClock, LoggingConsumer.expectSuccess(TAG, "loading readings"));
    }


    @VisibleForTesting
    public ChartController(ChartOptions.ChartPlacementType chartPlacementType,
            ScalarDisplayOptions scalarDisplayOptions, int chartDataThrowawayThreshold,
            long dataLoadBuffer, Clock uptimeClock, FailureListener dataFailureListener) {
        mUptimeClock = uptimeClock;
        mChartData = new ChartData(chartDataThrowawayThreshold,
                ChartData.DEFAULT_THROWAWAY_TIME_THRESHOLD);
        mChartOptions = new ChartOptions(chartPlacementType);
        mChartOptions.setScalarDisplayOptions(scalarDisplayOptions);
        mDataFailureListener = dataFailureListener;
        mDefaultGraphRange = ExternalAxisController.DEFAULT_GRAPH_RANGE_IN_MILLIS;
        mDataLoadBuffer = dataLoadBuffer;
        mCurrentTimeClock = new CurrentTimeClock();
    }

    public void setDefaultGraphRange(long defaultGraphRange) {
        mDefaultGraphRange = defaultGraphRange;
    }

    public void setChartView(ChartView view) {
        mChartView = view;
        mChartView.clearInteractionListeners();
        if (mInteractionListener != null) {
            mChartView.addInteractionListener(mInteractionListener);
        }
        mChartView.initialize(mChartOptions, mChartData);
    }

    public void setProgressView(ProgressBar progress) {
        mProgressView = progress;
    }

    // Adds a single point to the end of the path. Assumes points are ordered as they arrive.
    public void addPoint(ChartData.DataPoint point) {
        // TODO: extract as a testable object
        if (mResetTime != -1) {
            if (point.getX() < mResetTime) {
                // straggling datapoint from before the reset, ignore
                return;
            } else {
                mResetTime = -1;
            }
        }
        if (!mChartData.isEmpty()) {
            // Get rid of data too old to be interesting for "now", but too new to be likely
            // seen by scrolling from the current view.  If we're recording, we'll swap
            // this data back in when we scroll to it.  If not, then we have no data
            // retention guarantees.
            // TODO: Is it possible to call throwAwayBetween less frequently for performance?
            // no need to do so many binary searches in ChartData...
            // TODO: This throwAwayBetween is causing b/28614204.
            long throwawayBefore = point.getX() - (KEEP_THIS_MANY_SCREENS * mDefaultGraphRange);
            long throwawayAfter = mChartOptions.getRenderedXMax() + mDefaultGraphRange;
            mChartData.throwAwayBetween(throwawayAfter, throwawayBefore);
        }

        mChartData.addPoint(point);
        if (mChartView != null && mChartView.isDrawn()) {
            mChartView.addPointToEndOfPath(point);
        }
    }

    // Assume this is an ordered list.
    public void setData(List<ChartData.DataPoint> points) {
        mChartData.clear();
        mChartData.setPoints(points);
        mChartOptions.reset();
        mChartOptions.setPinnedToNow(false);
    }

    private void addOrderedGroupOfPoints(List<ChartData.DataPoint> points, long requestId) {
        if (mCurrentLoadIds.contains(requestId)) {
            mChartData.addOrderedGroupOfPoints(points);
        }
    }

    // Clears just the line data, but does not reset the options. This is useful if we need
    // to update zoom levels on the same sensor in the same range, for example.
    private void clearLineData() {
        mChartData.clear();
        mCurrentLoadIds.clear();
        if (mChartView != null) {
            mChartView.clear();
        }
    }

    public void clearData() {
        mChartData.clear();
        mCurrentLoadIds.clear();
        mChartOptions.reset();
        if (mChartView != null) {
            mChartView.clear();
        }
    }

    public void onDestroy() {
        onViewRecycled();
        mChartData.clear();
        mCurrentLoadIds.clear();
        mChartDataLoadedCallbacks.clear();
    }

    public void onViewRecycled() {
        if (mChartView != null) {
            mChartView.clearInteractionListeners();
            mInteractionListener = null;
            mChartView = null;
        }
    }

    private void setPinnedToNow(boolean isPinnedToNow) {
        mChartOptions.setPinnedToNow(isPinnedToNow);
    }

    public boolean isPinnedToNow() {
        return mChartOptions.isPinnedToNow();
    }

    public void setTriggers(List<SensorTrigger> triggers) {
        List<Double> values = new ArrayList<>();
        for (SensorTrigger trigger : triggers) {
            values.add(trigger.getValueToTrigger());
        }
        mChartOptions.setTriggerValues(values);
    }

    public void setLabels(List<Label> labels) {
        mDisplayableLabels.clear();
        for (Label label : labels) {
            if (mChartOptions.isDisplayable(label, mChartOptions.getRecordingStartTime())) {
                mDisplayableLabels.add(label);
            }
        }
        mChartData.setDisplayableLabels(mDisplayableLabels);
        if (mChartView != null) {
            mChartView.postInvalidateOnAnimation();
        }
    }

    private void refreshLabels() {
        mChartData.setDisplayableLabels(mDisplayableLabels);
        if (mChartView != null) {
            mChartView.postInvalidateOnAnimation();
        }
    }

    public List<ChartData.DataPoint> getData() {
        return mChartData.getPoints();
    }

    public void setXAxis(long xMin, long xMax) {
        mChartOptions.setRenderedXRange(xMin, xMax);
        if (mChartOptions.isPinnedToNow() && !mChartData.isEmpty()) {
            mChartOptions.adjustYAxisStep(
                    mChartData.getPoints().get(mChartData.getNumPoints() - 1));
        }
        if (mChartView != null) {
            mChartView.onAxisLimitsAdjusted();
        }
    }

    public void setXAxisWithBuffer(long xMin, long xMax) {
        long buffer = (long) (ExternalAxisController.EDGE_POINTS_BUFFER_FRACTION * (xMax - xMin));
        setXAxis(xMin - buffer, xMax + buffer);
    }

    public void setYAxis(double yMin, double yMax) {
        mChartOptions.setRenderedYRange(yMin, yMax);
        if (mChartView != null) {
            mChartView.onAxisLimitsAdjusted();
        }
    }

    private void updateYRangeFromValueRange(Range<Double> valueRange) {
        mChartOptions.updateYMinAndMax(Math.min(valueRange.lowerEndpoint(),
                mChartOptions.getYMinLimit()), Math.max(valueRange.upperEndpoint(),
                mChartOptions.getYMaxLimit()));
    }

    public void clearReviewYAxis() {
        mChartOptions.resetYAxisLimits();
    }

    public void setReviewYAxis(double min, double max, boolean hasBuffer) {
        min = Math.min(mChartOptions.getYMinLimit(), min);
        max = Math.max(mChartOptions.getYMaxLimit(), max);
        double buffer = 0;
        if (hasBuffer) {
            buffer = ChartOptions.getYBuffer(min, max);
        }
        mChartOptions.updateYMinAndMax(min - buffer, max + buffer);
        setYAxis(min - buffer, max + buffer);
        refreshChartView();
    }

    public boolean hasData() {
        return !mChartData.isEmpty();
    }

    public long getXMin() {
        return mChartData.getXMin();
    }

    public long getXMax() {
        return mChartData.getXMax();
    }

    public long getRenderedXMin() {
        return mChartOptions.getRenderedXMin();
    }

    public long getRenderedXMax() {
        return mChartOptions.getRenderedXMax();
    }

    public double getRenderedYMin() {
        return mChartOptions.getRenderedYMin();
    }

    public double getRenderedYMax() {
        return mChartOptions.getRenderedYMax();
    }

    public void refreshChartView() {
        if (mChartView != null) {
            mChartView.redraw();
        }
    }

    /**
     * Gets the closest data point to a given time stamp.
     * @param timestamp The timestamp to search for
     * @return A data point that is closest to this timestamp
     */
    public ChartData.DataPoint getClosestDataPointToTimestamp(long timestamp) {
        return mChartData.getClosestDataPointToTimestamp(timestamp);
    }

    /**
     * Gets the closest data point to the given timestamp, so long as it is above or equal to
     * the aboveTimestamp. The aboveTimestamp cannot be more than one index away from
     * the closest data point's index.
     * @param timestamp The timestamp to search for.
     * @param aboveTimestamp This may not be more than one index away in ChartData.
     * @return A data point, or null if none is available.
     */
    public ChartData.DataPoint getClosestDataPointToTimestampAbove(long timestamp,
            long aboveTimestamp) {
        if (mChartData.isEmpty()) {
            return null;
        }
        int closestIndex = mChartData.getClosestIndexToTimestamp(timestamp);
        ChartData.DataPoint closestPoint = mChartData.getPoints().get(closestIndex);
        // Check if we are above the aboveTimestamp.
        if (closestPoint.getX() >= aboveTimestamp) {
            return closestPoint;
        }
        if (closestIndex + 1 < mChartData.getNumPoints() - 1) {
            return mChartData.getPoints().get(closestIndex + 1);
        }
        return null;
    }

    /**
     * Gets the closest data point to the given timestamp, so long as it is below or equal to
     * the belowTimestamp. The belowTimestamp cannot be more than one index away from
     * the closest data point's index.
     * @param timestamp The timestamp to search for.
     * @param belowTimestamp This may not be more than one index away in ChartData.
     * @return A data point, or null if none is available.
     */
    public ChartData.DataPoint getClosestDataPointToTimestampBelow(long timestamp,
            long belowTimestamp) {
        if (mChartData.isEmpty()) {
            return null;
        }
        int closestIndex = mChartData.getClosestIndexToTimestamp(timestamp);
        ChartData.DataPoint closestPoint = mChartData.getPoints().get(closestIndex);
        // Check if we are above the aboveTimestamp.
        if (closestPoint.getX() <= belowTimestamp) {
            return closestPoint;
        }
        if (closestIndex - 1 >= 0) {
            return mChartData.getPoints().get(closestIndex - 1);
        }
        return null;
    }

    public boolean hasDrawnChart() {
        return mChartView != null && mChartView.isDrawn();
    }

    public boolean hasScreenPoints() {
        return mChartView != null && mChartView.isDrawn() && !mChartData.isEmpty();
    }

    public PointF getScreenPoint(long timestamp, double value) {
        return new PointF(mChartView.getScreenX(timestamp), mChartView.getScreenY(value));
    }

    public ViewTreeObserver getChartViewTreeObserver() {
        if (mChartView == null) {
            return null;
        }
        return mChartView.getViewTreeObserver();
    }

    private void updateColor(int colorIndex, Context context) {
        if (context == null) {
            return;
        }
        int color = context.getResources().getIntArray(R.array.graph_colors_array)[colorIndex];
        updateColor(color);
    }

    public void updateColor(int color) {
        mChartOptions.setLineColor(color);
        if (mChartView != null) {
            mChartView.updateColorOptions();
        }
    }

    public void setShowProgress(boolean showProgress) {
        if (mChartView != null) {
            mChartView.setVisibility(showProgress ? View.GONE: View.VISIBLE);
        }
        if (mProgressView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mProgressView.setIndeterminateTintList(ColorStateList.valueOf(
                        mChartOptions.getLineColor()));
            }
            mProgressView.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        }
    }

    public void setInteractionListener(
            ExternalAxisController.InteractionListener interactionListener) {
        mInteractionListener = interactionListener;
        if (mChartView != null) {
            mChartView.addInteractionListener(interactionListener);
        }
    }

    public void updateOptions(int graphColor, ScalarDisplayOptions scalarDisplayOptions,
            String sensorId) {
        mChartOptions.setLineColor(graphColor);
        mChartOptions.setScalarDisplayOptions(scalarDisplayOptions);
        mSensorId = sensorId;
        if (mChartView != null) {
            mChartView.redraw(); // Full redraw in case the options caused computational changes.
        }
    }

    public void setSensorId(String sensorId) {
        mSensorId = sensorId;
    }

    public void setRecordingStartTime(long recordingStartTime) {
        mChartOptions.setRecordingStartTime(recordingStartTime);
        if (mChartView != null) {
            mChartView.postInvalidateOnAnimation();
        }
    }

    public void updateStats(List<StreamStat> stats) {
        mChartData.updateStats(stats);
        if (mChartView != null) {
            mChartView.postInvalidateOnAnimation();
        }
    }

    public void setShowStatsOverlay(boolean showStatsOverlay) {
        mChartOptions.setShowStatsOverlay(showStatsOverlay);
        if (mChartView != null) {
            mChartView.postInvalidateOnAnimation();
        }
    }

    /**
     * Tries to load data into the chart with the given parameters.
     * @param fullChartLoadDataCallback A callback which is used just during this load, but is
     *                                  not saved for future data loads.
     */
    // TODO: Seems like this loads the whole run data even if we are really zoomed in, so
    // this should be revisited to get a range for the original load.
    public void loadRunData(Trial trial, GoosciSensorLayout.SensorLayout sensorLayout,
            DataController dc, ChartLoadingStatus status, TrialStats stats,
            ChartDataLoadedCallback fullChartLoadDataCallback, Context context) {
        updateColor(sensorLayout.colorIndex, context);
        setShowProgress(true);
        clearData();
        final long firstTimestamp = trial.getFirstTimestamp();
        final long lastTimestamp = trial.getLastTimestamp();
        mChartOptions.setRecordingTimes(firstTimestamp, lastTimestamp,
                trial.getOriginalFirstTimestamp(), trial.getOriginalLastTimestamp());
        mSensorId = sensorLayout.sensorId;
        tryLoadingChartData(trial.getTrialId(), sensorLayout, dc,
                mChartOptions.getRecordingStartTime(), mChartOptions.getRecordingEndTime(), status,
                stats, fullChartLoadDataCallback, context);
    }

    // TODO: remove duplication with loadReadings?
    private void tryLoadingChartData(final String runId,
            final GoosciSensorLayout.SensorLayout sensorLayout,
            final DataController dc, final long firstTimestamp, final long lastTimestamp,
            final ChartLoadingStatus status, final TrialStats stats,
            final ChartDataLoadedCallback fullChartLoadDataCallback, Context context) {
        Preconditions.checkNotNull(runId);

        // If we are currently trying to load something, don't try and load something else.
        // Instead, when loading is completed a callback will check that the correct data
        // was loaded and re-call this function.
        // The status is always set loading before loadSensorReadings, and always set to idle
        // when loading is completed (regardless of whether the correct data was loaded).
        if (status.getGraphLoadStatus() != ChartLoadingStatus.GRAPH_LOAD_STATUS_IDLE) {
            return;
        }
        updateColor(sensorLayout.colorIndex, context);
        status.setGraphLoadStatus(ChartLoadingStatus.GRAPH_LOAD_STATUS_LOADING);
        addChartDataLoadedCallback(fullChartLoadDataCallback);
        callChartDataStartLoadingCallbacks(true);
        final ZoomPresenter zp = getZoomPresenter(stats);
        mMinLoadedX = firstTimestamp;
        mMaxLoadedX = lastTimestamp;
        int currentTier = zp.updateTier(lastTimestamp - firstTimestamp);

        // Populate the initial graph
        GraphPopulator graphPopulator = new GraphPopulator(new GraphPopulator.ObservationDisplay() {
            @Override
            public void addRange(ScalarReadingList observations, Range<Double> valueRange,
                    long requestId) {
                updateYRangeFromValueRange(valueRange);
                addOrderedGroupOfPoints(observations.asDataPoints(), requestId);
            }

            @Override
            public void onFinish(long requestId) {
                status.setGraphLoadStatus(ChartLoadingStatus.GRAPH_LOAD_STATUS_IDLE);

                if (!runId.equals(status.getRunId()) ||
                        !sensorLayout.sensorId.equals(status.getSensorId()) ||
                        !mCurrentLoadIds.contains(requestId)) {
                    // The wrong run or the wrong sensor ID was loaded into this
                    // chartController, or this is the wrong request ID.
                    // Clear and try again with the updated run and sensor values from the holder.
                    clearData();
                    tryLoadingChartData(status.getRunId(),
                            sensorLayout, dc, firstTimestamp, lastTimestamp,
                            status, stats, fullChartLoadDataCallback, context);
                } else {
                    mCurrentLoadIds.remove(requestId);
                    callChartDataLoadedCallbacks(firstTimestamp, lastTimestamp);
                    if (fullChartLoadDataCallback != null) {
                        removeChartDataLoadedCallback(fullChartLoadDataCallback);
                    }
                    setShowProgress(false);
                }
            }
        }, mUptimeClock);

        mCurrentLoadIds.add(graphPopulator.getRequestId());
        graphPopulator.requestObservations(
                GraphPopulator.constantGraphStatus(firstTimestamp, lastTimestamp), dc,
                mDataFailureListener, currentTier, mSensorId);
    }

    private ZoomPresenter getZoomPresenter(TrialStats stats) {
        if (mZoomPresenter == null) {
            mZoomPresenter = new ZoomPresenter();
        }
        mZoomPresenter.setRunStats(stats);
        return mZoomPresenter;
    }

    public void onPause() {
        if (isRecording()) {
            mNeedsForwardLoad = true;
        }
    }

    public void onResume(long resetTime) {
        mResetTime = resetTime;
        if (hasData() && mResetTime > mChartData.getXMax() + MAX_BLACKOUT_MILLIS_BEFORE_CLEARING) {
            clearLineData();
            mNeedsForwardLoad = false;
        }
    }

    public void setShowOriginalRun(boolean showOriginalRun) {
        mChartOptions.setShowOriginalRun(showOriginalRun);
    }

    public void onGlobalXAxisChanged(long xMin, long xMax, boolean isPinnedToNow,
            DataController dataController) {
        boolean isRunReview = mChartOptions.getChartPlacementType() ==
                ChartOptions.ChartPlacementType.TYPE_RUN_REVIEW;
        if (isRunReview && mZoomPresenter == null) {
            // Then we aren't loaded all the way, so don't try to load anything else.
            return;
        }
        boolean isRecording = isObserving() && isRecording();
        if (isRunReview || isRecording) {
            long range = xMax - xMin;
            long buffer = isRecording ? mDataLoadBuffer : range / 8;

            if (isRunReview) {
                int oldTier = mZoomPresenter.getCurrentTier();
                int newTier = mZoomPresenter.updateTier(range);
                if (oldTier != newTier) {
                    reloadAtNewZoomLevel(xMin, xMax, dataController, buffer);
                    return;
                }
            }

            // If something is already loaded...
            if (mMinLoadedX != NOTHING_LOADED && !mChartData.isEmpty()) {
                // Load new data and throw away data that is too far off screen.
                // Note that xMin may be less than what is possible to load, because we often
                // load the chart with some buffer.
                long minPossibleToLoad = Math.max(xMin, mChartOptions.getRecordingStartTime());
                if (minPossibleToLoad < mMinLoadedX) {
                    long prevMinLoadedX = mMinLoadedX;
                    mMinLoadedX = Math.max(xMin - buffer, mChartOptions.getRecordingStartTime());
                    loadReadings(dataController, mMinLoadedX, prevMinLoadedX, false);
                }
                long maxPossibleToLoad = isRecording ? xMax :
                        Math.min(xMax, mChartOptions.getRecordingEndTime());
                if (maxPossibleToLoad > mMaxLoadedX) {
                    long prevMaxLoadedX = mMaxLoadedX;
                    mMaxLoadedX = isRecording? xMax :
                            Math.min(xMax + buffer, mChartOptions.getRecordingEndTime());
                    // If it's pinned to now, then we don't expect to find data magically
                    // appearing in front of old data.
                    if (mNeedsForwardLoad || isRunReview || !isPinnedToNow) {
                        loadReadings(dataController, prevMaxLoadedX, mMaxLoadedX, false);
                        mNeedsForwardLoad = false;
                    }
                }
            } else if (isRecording && mCurrentLoadIds.size() == 0) {
                // If we haven't loaded anything, and it is recorded, try loading data that
                // was already recorded.
                // Don't load anything before the recording start time if we got here
                // from resume.
                mMinLoadedX = Math.max(xMin, mChartOptions.getRecordingStartTime());
                mMaxLoadedX = xMax;
                loadReadings(dataController, mMinLoadedX, mMaxLoadedX, false);
            } else if (mChartData.isEmpty()) {
                // If we haven't loaded anything, and we are not recording.
                mMinLoadedX = Math.max(xMin, mChartOptions.getRecordingStartTime());
                mMaxLoadedX = Math.min(xMax, mChartOptions.getRecordingEndTime());
                loadReadings(dataController, mMinLoadedX, mMaxLoadedX, false);
            }
        }
        setXAxis(xMin, xMax);

        if (isRunReview) {
            mChartData.throwAwayBefore(mMinLoadedX);
            mChartData.throwAwayAfter(mMaxLoadedX);
        } else {
            setPinnedToNow(isPinnedToNow);
            long throwawayThreshold = xMin - (KEEP_THIS_MANY_SCREENS - 1) * mDefaultGraphRange;
            if (!isPinnedToNow && !isRecording && xMax >
                    mCurrentTimeClock.getNow() - ExternalAxisController.LEADING_EDGE_BUFFER_TIME) {
                // Don't throw away data on a big scroll forward
                throwawayThreshold = mCurrentTimeClock.getNow() - KEEP_THIS_MANY_SCREENS *
                        mDefaultGraphRange;
            }
            if (mMinLoadedX < throwawayThreshold) {
                mMinLoadedX = throwawayThreshold;
            }
            // TODO: Should this be a throwAwayBetween or throwAwayafter, depending on which way
            // the x axis changed??
            mChartData.throwAwayBefore(throwawayThreshold);
        }
    }

    private boolean isObserving() {
        return mChartOptions.getChartPlacementType() ==
                ChartOptions.ChartPlacementType.TYPE_OBSERVE;
    }

    private void reloadAtNewZoomLevel(long xMin, long xMax, DataController dataController,
            long buffer) {
        setShowProgress(true);
        clearLineData();
        mMinLoadedX = Math.max(xMin - buffer, mChartOptions.getRecordingStartTime());
        mMaxLoadedX = Math.min(xMax + buffer, mChartOptions.getRecordingEndTime());
        mCurrentLoadIds.clear();
        loadReadings(dataController, mMinLoadedX, mMaxLoadedX, true);
        setXAxis(xMin, xMax);
    }

    private boolean isRecording() {
        return mChartOptions.getRecordingStartTime() != RecordingMetadata.NOT_RECORDING;
    }

    @VisibleForTesting
    public void loadReadings(DataController dataController, final long minToLoad,
            final long maxToLoad, final boolean chartHiddenForLoad) {
        int currentTier = mZoomPresenter == null ? 0 : mZoomPresenter.getCurrentTier();
        GraphPopulator graphPopulator = new GraphPopulator(new GraphPopulator.ObservationDisplay() {
            @Override
            public void addRange(ScalarReadingList observations, Range<Double> valueRange,
                    long requestId) {
                updateYRangeFromValueRange(valueRange);
                addOrderedGroupOfPoints(observations.asDataPoints(), requestId);
            }

            @Override
            public void onFinish(long requestId) {
                if (mCurrentLoadIds.contains(requestId)) {
                    mCurrentLoadIds.remove(requestId);
                }
                if (mCurrentLoadIds.size() == 0) {
                    refreshLabels();
                }
                if (chartHiddenForLoad) {
                    setShowProgress(false);
                }
                refreshChartView();
                callChartDataLoadedCallbacks(minToLoad, maxToLoad);
            }
        }, mUptimeClock);
        mCurrentLoadIds.add(graphPopulator.getRequestId());
        graphPopulator.requestObservations(GraphPopulator.constantGraphStatus(minToLoad, maxToLoad),
                dataController, mDataFailureListener, currentTier, mSensorId);

        callChartDataStartLoadingCallbacks(chartHiddenForLoad);
    }

    public void addChartDataLoadedCallback(ChartDataLoadedCallback callback) {
        if (callback != null) {
            mChartDataLoadedCallbacks.add(callback);
        }
    }

    public void removeChartDataLoadedCallback(ChartDataLoadedCallback callback) {
        if (mChartDataLoadedCallbacks.contains(callback)) {
            mChartDataLoadedCallbacks.remove(callback);
        }
    }

    private void callChartDataLoadedCallbacks(long firstTimestamp, long lastTimestamp) {
        if (mChartOptions.getRequestResetZoomInY()) {
            setYAxis(mChartOptions.getYMinLimit(), mChartOptions.getYMaxLimit());
        }
        for (ChartDataLoadedCallback callback : mChartDataLoadedCallbacks) {
            callback.onChartDataLoaded(firstTimestamp, lastTimestamp);
        }
    }

    private void callChartDataStartLoadingCallbacks(boolean chartHiddenForLoad) {
        for (ChartDataLoadedCallback callback : mChartDataLoadedCallbacks) {
            callback.onLoadAttemptStarted(chartHiddenForLoad);
        }
    }
}
