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

package com.google.android.apps.forscience.whistlepunk.review;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.ScalarDataLoader;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ZoomRecorder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Controls the zoom level at which to load data points into a line graph, based on the zoom levels
 * available and the ideal number of data points to display
 */
public class ZoomPresenter {
    // Experimentally, this seems to produce decent results on Nexus 5x.  We could adjust.
    private static final int IDEAL_NUMBER_OF_DISPLAYED_DATAPOINTS = 500;

    /**
     * How far does our ideal zoom level need to be from the current zoom level before we change?
     * Current zoom level is always an int, so a value here of 0.5 means that we always switch to
     * the ideal zoom level, and ignore the current level.  The downside is that if one is close
     * to the midpoint, and keeps zooming back and forth, every small change triggers a reload.
     *
     * Too big a value here, and data can get noticeably sparse when zooming in before a reload.
     *
     * Experimentally, 0.6 seems a decent compromise at the moment.
     */
    private static final double THRESHOLD_TO_CHANGE_ZOOM_LEVEL = 0.6;

    private static final Runnable NOOP = new Runnable() {
        @Override
        public void run() {

        }
    };
    private static final String TAG = "ZoomPresenter";

    private final ChartController mChartController;
    private final DataController mDataController;
    private final int mIdealNumberOfDisplayedDatapoints;
    private final FailureListener mFailureListener;
    private RunStats mRunStats;
    private int mCurrentTier;
    private String mSensorId;
    private Runnable mOnLoadFinish;
    private IncrementalLoader mIncrementalLoader;

    public ZoomPresenter(ChartController chartController, DataController dataController) {
        this(chartController, dataController, IDEAL_NUMBER_OF_DISPLAYED_DATAPOINTS,
                LoggingConsumer.expectSuccess(TAG, "loading readings"));
    }

    @VisibleForTesting
    public ZoomPresenter(ChartController chartController, DataController dataController,
            int idealNumberOfDisplayedDatapoints, FailureListener failureListener) {
        mChartController = Preconditions.checkNotNull(chartController);
        mDataController = dataController;
        mIdealNumberOfDisplayedDatapoints = idealNumberOfDisplayedDatapoints;
        mFailureListener = failureListener;
    }

    /**
     * @param onInitialLoad should be run after data is loaded in for the first time (null for
     *                      no-op)
     * @param onEveryLoad   should be run every time additional data is loaded on zoom or pan (null
     *                      for no-op)
     */
    public void loadInitialReadings(long firstTimestamp, long lastTimestamp, RunStats runStats,
            final Runnable onInitialLoad, final Runnable onEveryLoad, String sensorId) {
        long loadedRange = lastTimestamp - firstTimestamp;
        mRunStats = runStats;
        mCurrentTier = updateTier(loadedRange);
        mSensorId = sensorId;

        final Runnable safeInitialLoad = safeRunnable(onInitialLoad);
        final Runnable safeEveryLoad = safeRunnable(onEveryLoad);

        mOnLoadFinish = new Runnable() {
            @Override
            public void run() {
                // After first run, only do every-load work
                mOnLoadFinish = safeEveryLoad;

                safeInitialLoad.run();
                safeEveryLoad.run();
            }
        };

        getIncrementalLoader().loadIn(firstTimestamp, lastTimestamp);
    }

    private Runnable safeRunnable(Runnable runnable) {
        return runnable == null ? NOOP : runnable;
    }

    private IncrementalLoader getIncrementalLoader() {
        if (mIncrementalLoader == null) {
            mIncrementalLoader = new IncrementalLoader();
        }
        return mIncrementalLoader;
    }

    @VisibleForTesting
    public int updateTier(long loadedRange) {
        mCurrentTier = computeTier(mCurrentTier, mIdealNumberOfDisplayedDatapoints, mRunStats,
                loadedRange);
        return mCurrentTier;
    }

    @VisibleForTesting
    public static int computeTier(int currentTier, int idealNumberOfDisplayedDatapoints,
            RunStats runStats, long loadedRange) {
        if (!hasRequiredStats(runStats)) {
            // Must be an old run from before we started saving zoom info, so only base tier exists.
            return 0;
        }

        double idealTier = computeIdealTier(idealNumberOfDisplayedDatapoints, runStats,
                loadedRange);

        if (Math.abs(idealTier - currentTier) < THRESHOLD_TO_CHANGE_ZOOM_LEVEL) {
            return currentTier;
        }

        int actualTier = (int) Math.round(idealTier);
        if (actualTier < 0) {
            actualTier = 0;
        }
        int maxTier = runStats.getIntStat(ZoomRecorder.STATS_KEY_TIER_COUNT) - 1;
        if (actualTier > maxTier) {
            actualTier = maxTier;
        }

        return actualTier;
    }

    @VisibleForTesting
    public static double computeIdealTier(int idealNumberOfDisplayedDatapoints,
            RunStats runStats, long loadedRange) {
        double meanMillisPerDataPoint = runStats.getStat(StatsAccumulator.KEY_TOTAL_DURATION)
                / runStats.getStat(StatsAccumulator.KEY_NUM_DATA_POINTS);
        double expectedTierZeroDatapointsInRange = loadedRange / meanMillisPerDataPoint;
        double idealTierZeroDatapointsPerDisplayedPoint =
                expectedTierZeroDatapointsInRange / idealNumberOfDisplayedDatapoints;

        int zoomLevelBetweenTiers = runStats.getIntStat(
                ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS);
        return Math.log(idealTierZeroDatapointsPerDisplayedPoint) / Math.log(
                zoomLevelBetweenTiers);
    }

    private static boolean hasRequiredStats(RunStats stats) {
        return stats.hasStat(StatsAccumulator.KEY_TOTAL_DURATION) && stats.hasStat(
                StatsAccumulator.KEY_NUM_DATA_POINTS) && stats.hasStat(
                ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS) && stats.hasStat(
                ZoomRecorder.STATS_KEY_TIER_COUNT);
    }

    public void setXAxis(long xMin, long xMax) {
        if (mRunStats != null) {
            int oldTier = mCurrentTier;
            updateTier(xMax - xMin);
            if (mCurrentTier != oldTier) {
                clearLineData(/* don't reset y axis */ false);
            }
        }
        if (mSensorId != null) {
            getIncrementalLoader().loadIn(xMin, xMax);
        }
        mChartController.setXAxis(xMin, xMax);
    }

    public void clearLineData(boolean resetYAxis) {
        mChartController.clearData();
        mIncrementalLoader = null;
    }

    // TODO: combine with similar code in ScalarSensor?
    private class IncrementalLoader {
        private boolean mAnythingLoaded = false;
        private long mMinLoadedX = Long.MAX_VALUE;
        private long mMaxLoadedX = Long.MIN_VALUE;

        public void loadIn(long xMin, long xMax) {
            if (!mAnythingLoaded) {
                loadSensorReadings(xMin, xMax);
                mMinLoadedX = xMin;
                mMaxLoadedX = xMax;
            }
            if (xMin < mMinLoadedX) {
                loadSensorReadings(xMin, mMinLoadedX);
                mMinLoadedX = xMin;
            }
            if (xMax > mMaxLoadedX) {
                loadSensorReadings(mMaxLoadedX, xMax);
                mMaxLoadedX = xMax;
            }

            mAnythingLoaded = true;
            mMinLoadedX = Math.min(xMin, mMinLoadedX);
            mMaxLoadedX = Math.max(xMax, mMaxLoadedX);
        }

        private void loadSensorReadings(long firstTimestamp, long lastTimestamp) {
            mChartController.setShowProgress(true);
            ScalarDataLoader.loadSensorReadings(mSensorId, mDataController, firstTimestamp,
                    lastTimestamp, mCurrentTier, mOnLoadFinish, mFailureListener,
                    mChartController);
        }
    }
}