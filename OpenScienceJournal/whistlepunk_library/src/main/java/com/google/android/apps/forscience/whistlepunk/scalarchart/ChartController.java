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

import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.os.Build;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.ScalarDataLoader;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.review.ZoomPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

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

        GoosciSensorLayout.SensorLayout getSensorLayout();
    }

    public interface ChartLoadedCallback {
        void onChartLoaded(long firstTimestamp, long lastTimestamp);
        void onLoadAttemptStarted();
    }

    public static final String TAG = "ChartController";

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
    private ChartOptions mChartOptions;
    private ChartView mChartView;
    private ExternalAxisController.InteractionListener mInteractionListener;
    private long mRecordingStartTime = RecordingMetadata.NOT_RECORDING;
    private ProgressBar mProgressView;

    // Fields used for data loading and clearing during Observe/Record
    private long mDefaultGraphRange;
    private long mDataLoadBuffer;
    private final FailureListener mDataFailureListener;
    private final Runnable mOnDataLoadFinishListener;
    private long mResetTime = -1;
    private String mSensorId;

    public ChartController(ChartOptions.ChartPlacementType chartPlacementType,
            ScalarDisplayOptions scalarDisplayOptions) {
        this(chartPlacementType, scalarDisplayOptions, ChartData.DEFAULT_THROWAWAY_THRESHOLD,
                DEFAULT_DATA_LOAD_BUFFER_MILLIS);
    }

    @VisibleForTesting
    public ChartController(ChartOptions.ChartPlacementType chartPlacementType,
            ScalarDisplayOptions scalarDisplayOptions, int chartDataThrowawayThreshold,
            long dataLoadBuffer) {
        mChartData = new ChartData(chartDataThrowawayThreshold);
        mChartOptions = new ChartOptions(chartPlacementType);
        mChartOptions.setScalarDisplayOptions(scalarDisplayOptions);
        mDataFailureListener = LoggingConsumer.expectSuccess(TAG, "loading readings");
        mOnDataLoadFinishListener = new Runnable() {
            @Override
            public void run() {
                refreshChartView();
            }
        };
        mDefaultGraphRange = ExternalAxisController.DEFAULT_GRAPH_RANGE_IN_MILLIS;
        mDataLoadBuffer = dataLoadBuffer;
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
        if (mChartOptions.isPinnedToNow()) {
            mChartOptions.adjustYAxisStep(point);
        }
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

    public void addOrderedGroupOfPoints(List<ChartData.DataPoint> points) {
        mChartData.addOrderedGroupOfPoints(points);
    }

    public void clearData() {
        mChartData.clear();
        mChartOptions.reset();
        if (mChartView != null) {
            mChartView.redraw();
        }
    }

    public void onDestroy() {
        if (mChartView != null) {
            mChartView.clearInteractionListeners();
            mInteractionListener = null;
            mChartView = null;
        }
        mChartData.clear();
    }

    public void onViewRecycled() {
        if (mChartView != null) {
            mChartView.clearInteractionListeners();
            mChartView = null;
        }
    }

    public void setPinnedToNow(boolean isPinnedToNow) {
        mChartOptions.setPinnedToNow(isPinnedToNow);
    }

    public boolean isPinnedToNow() {
        return mChartOptions.isPinnedToNow();
    }

    public void setLabels(List<Label> labels) {
        List<Label> displayableLabels = new ArrayList<>();
        for (Label label : labels) {
            if (mChartOptions.isDisplayable(label, mRecordingStartTime)) {
                displayableLabels.add(label);
            }
        }
        mChartData.setDisplayableLabels(displayableLabels);
        if (mChartView != null) {
            mChartView.invalidate();
        }
    }

    public void addLabel(Label label) {
        mChartData.addLabel(label);
    }

    public List<ChartData.DataPoint> getData() {
        return mChartData.getPoints();
    }

    public void setXAxis(long xMin, long xMax) {
        mChartOptions.setRenderedXRange(xMin, xMax);
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

    public void setYAxisWithBuffer(double min, double max) {
        double buffer = ChartOptions.getYBuffer(min, max);
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

    public ChartData.DataPoint getClosestDataPointToTimestamp(long timestamp) {
        return mChartData.getClosestDataPointToTimestamp(timestamp);
    }

    public int getClosestIndexToTimestamp(long timestamp) {
        return mChartData.getClosestIndexToTimestamp(timestamp);
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
            ExternalAxisController.InteractionListener interactionListener, String sensorId) {
        mInteractionListener = interactionListener;
        mChartOptions.setLineColor(graphColor);
        mChartOptions.setScalarDisplayOptions(scalarDisplayOptions);
        mSensorId = sensorId;
        if (mChartView != null) {
            mChartView.clearInteractionListeners();
            mChartView.addInteractionListener(interactionListener);
            mChartView.redraw(); // Full redraw in case the options caused computational changes.
        }
    }

    public void setSensorId(String sensorId) {
        mSensorId = sensorId;
    }

    public void setRecordingStartTime(long recordingStartTime) {
        mRecordingStartTime = recordingStartTime;
        mChartOptions.setRecordingStartTime(recordingStartTime);
        if (mChartView != null) {
            mChartView.invalidate();
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
            mChartView.invalidate();
        }
    }

    // Tries to load data into the chart with the given parameters.
    public void loadRunData(ExperimentRun run, GoosciSensorLayout.SensorLayout sensorLayout,
            DataController dc, ChartLoadingStatus status, RunStats stats,
            ChartLoadedCallback chartLoadedCallback) {
        setShowProgress(true);
        clearData();
        final long firstTimestamp = run.getFirstTimestamp();
        final long lastTimestamp = run.getLastTimestamp();
        tryLoadingChartData(run.getRunId(), sensorLayout, dc, firstTimestamp, lastTimestamp, status,
                stats, chartLoadedCallback);
    }

    private void tryLoadingChartData(final String runId,
            final GoosciSensorLayout.SensorLayout sensorLayout,
            final DataController dc, final long firstTimestamp, final long lastTimestamp,
            final ChartLoadingStatus status, final RunStats stats,
            final ChartLoadedCallback chartLoadedCallback) {
        // If we are currently trying to load something, don't try and load something else.
        // Instead, when loading is completed a callback will check that the correct data
        // was loaded and re-call this function.
        // The status is always set loading before loadSensorReadings, and always set to idle
        // when loading is completed (regardless of whether the correct data was loaded).
        if (status.getGraphLoadStatus() != ChartLoadingStatus.GRAPH_LOAD_STATUS_IDLE) {
            return;
        }
        updateColor(sensorLayout.color);
        status.setGraphLoadStatus(ChartLoadingStatus.GRAPH_LOAD_STATUS_LOADING);
        chartLoadedCallback.onLoadAttemptStarted();
        final ZoomPresenter zp = new ZoomPresenter(this, dc);
        zp.loadInitialReadings(firstTimestamp,
                lastTimestamp, stats, new Runnable() {
                    public void run() {
                        status.setGraphLoadStatus(ChartLoadingStatus.GRAPH_LOAD_STATUS_IDLE);
                        if (!runId.equals(status.getRunId()) ||
                                !sensorLayout.sensorId.equals(status.getSensorLayout().sensorId)) {
                            // The wrong run or the wrong sensor ID was loaded into this
                            // chartController. Clear and try again with the updated
                            // run and sensor values from the holder.
                            zp.clearLineData(/* reset Y axis */ true);
                            tryLoadingChartData(status.getRunId(),
                                    status.getSensorLayout(), dc, firstTimestamp, lastTimestamp,
                                    status, stats, chartLoadedCallback);
                            return;
                        }
                        chartLoadedCallback.onChartLoaded(firstTimestamp, lastTimestamp);
                        setShowProgress(false);
                    }
                }, null, sensorLayout.sensorId);
    }

    public void onResume(long resetTime) {
        mResetTime = resetTime;
        if (hasData() && mResetTime > mChartData.getXMax() + MAX_BLACKOUT_MILLIS_BEFORE_CLEARING) {
            clearData();
        }
    }

    public void onGlobalXAxisChanged(long xMin, long xMax, boolean isPinnedToNow,
            DataController dataController) {
        if (isRecording()) {
            long minLoadedX = Long.MAX_VALUE;
            long maxLoadedX = Long.MIN_VALUE;
            if (!mChartData.isEmpty()) {
                minLoadedX = mChartData.getXMin();
                maxLoadedX = mChartData.getXMax();
            }
            if (mChartData.isEmpty()) {
                // Don't load anything before the recording start time if we got here
                // from resume.
                xMin = Math.max(xMin, mRecordingStartTime);
                loadReadings(dataController, xMin, xMax);
                minLoadedX = xMin;
                maxLoadedX = xMax;
            }
            // Load with a buffer to make scrolling more smooth
            if (xMin < minLoadedX && minLoadedX >= mRecordingStartTime) {
                long minToLoad = Math.max(xMin - mDataLoadBuffer, mRecordingStartTime);
                loadReadings(dataController, minToLoad, minLoadedX);
            }
            if (xMax > maxLoadedX) {
                if (!isPinnedToNow) {
                    // if it's pinned to now, then we don't expect to find data magically
                    // appearing in front of old data
                    loadReadings(dataController, maxLoadedX + mDataLoadBuffer, xMax);
                }
            }
        }

        setPinnedToNow(isPinnedToNow);
        setXAxis(xMin, xMax);

        long throwawayThreshold = xMin - (KEEP_THIS_MANY_SCREENS - 1) * mDefaultGraphRange;
        // TODO: Should this be a throwAwayBetween, depending on which way the x axis changed??
        mChartData.throwAwayBefore(throwawayThreshold);
    }

    private boolean isRecording() {
        return mRecordingStartTime != RecordingMetadata.NOT_RECORDING;
    }

    private void loadReadings(DataController dataController, long minToLoad, long maxToLoad) {
        // TODO: Does this do double-loading if a user is scrolling too fast or if loading is slow?
        ScalarDataLoader.loadSensorReadings(mSensorId, dataController, minToLoad, maxToLoad, 0,
                mOnDataLoadFinishListener, mDataFailureListener, this);
    }
}
