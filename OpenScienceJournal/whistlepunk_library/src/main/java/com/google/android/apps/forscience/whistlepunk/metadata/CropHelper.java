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

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Helper class for cropping.
 */
public class CropHelper {
    public static final String TAG = "CropHelper";

    // Time buffer for non-overlapping crop: We do not allow crop less than 1 second.
    public static final long MINIMUM_CROP_MILLIS = 1000;

    private static final int DATAPOINTS_PER_LOAD = 500;

    public static final String ACTION_CROP_STATS_RECALCULATED = "action_crop_stats_recalculated";
    public static final String EXTRA_SENSOR_ID = "extra_sensor_id";
    public static final String EXTRA_RUN_ID = "extra_run_id";

    public static final IntentFilter STATS_INTENT_FILTER = new IntentFilter(
            ACTION_CROP_STATS_RECALCULATED);

    public static class CropLabels {
        public ApplicationLabel cropStartLabel;
        public ApplicationLabel cropEndLabel;

        public CropLabels() {

        }
    }

    private static class ProcessPriorityThreadFactory implements ThreadFactory {
        private final int mThreadPriority;

        public ProcessPriorityThreadFactory(int threadPriority) {
            super();
            mThreadPriority = threadPriority;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setPriority(mThreadPriority);
            return thread;
        }
    }

    public interface CropRunListener {
        void onCropCompleted();
        void onCropFailed();
    }

    private int mStatsUpdated = 0;
    private Executor mCropStatsExecutor;
    private DataController mDataController;

    public CropHelper(DataController dataController) {
        this(Executors.newSingleThreadExecutor(new ProcessPriorityThreadFactory(
                android.os.Process.THREAD_PRIORITY_BACKGROUND)), dataController);
    }

    @VisibleForTesting
    public CropHelper(Executor executor, DataController dataController) {
        mCropStatsExecutor = executor;
        mDataController = dataController;
    }

    public void cropRun(final Context context, final ExperimentRun run, long startTimestamp,
            long endTimestamp, final CropRunListener listener) {

        // Are we trying to crop too wide? Are the timestamps valid?
        if (startTimestamp < run.getOriginalFirstTimestamp() ||
                run.getOriginalLastTimestamp() < endTimestamp || endTimestamp < startTimestamp) {
            listener.onCropFailed();
            return;
        }

        final CropLabels cropLabels = run.getCropLabels();
        if (cropLabels.cropStartLabel != null && cropLabels.cropStartLabel != null) {
            // It is already cropped, so we can edit the old crop labels.
            cropLabels.cropStartLabel.setTimestamp(startTimestamp);
            cropLabels.cropEndLabel.setTimestamp(endTimestamp);
            mDataController.editLabel(cropLabels.cropStartLabel,
                    new LoggingConsumer<Label>(TAG, "edit crop start label") {
                        @Override
                        public void success(Label value) {
                            mDataController.editLabel(cropLabels.cropEndLabel,
                                    new LoggingConsumer<Label>(TAG,"edit crop end label") {
                                        @Override
                                        public void success(Label value) {
                                            markRunStatsForAdjustment(context, run, listener);
                                        }
                                    });
                        }
                    });
        } else if (cropLabels.cropStartLabel == null && cropLabels.cropStartLabel == null) {
            // Otherwise we make new crop labels.
            ApplicationLabel cropStartLabel = new ApplicationLabel(
                    ApplicationLabel.TYPE_CROP_START, mDataController.generateNewLabelId(),
                    run.getRunId(), startTimestamp);
            final ApplicationLabel cropEndLabel = new ApplicationLabel(
                    ApplicationLabel.TYPE_CROP_END, mDataController.generateNewLabelId(),
                    run.getRunId(), endTimestamp);
            cropStartLabel.setExperimentId(run.getExperimentId());
            cropEndLabel.setExperimentId(run.getExperimentId());

            // Update the run.
            cropLabels.cropStartLabel = cropStartLabel;
            cropLabels.cropEndLabel = cropEndLabel;

            // Add new crop labels to the database.
            mDataController.addLabel(cropStartLabel,
                    new LoggingConsumer<Label>(TAG, "add crop start label") {
                        @Override
                        public void success(Label value) {
                            mDataController.addLabel(cropEndLabel,
                                    new LoggingConsumer<Label>(TAG, "Add crop end label") {
                                        @Override
                                        public void success(Label value) {
                                            markRunStatsForAdjustment(context, run, listener);
                                        }
                                    });
                        }
                    });
        } else {
            // One crop label is set and the other is not. This is an error!
            listener.onCropFailed();
        }
    }

    private void markRunStatsForAdjustment(final Context context, final ExperimentRun run,
            final CropRunListener listener) {
        // First delete the min/max/avg stats, but leave the rest available, because they are used
        // in loading data by ZoomPresenter. At this point, we can go back to RunReview.
        final int statsToUpdate = run.getSensorLayouts().size();
        mStatsUpdated = 0;
        for (GoosciSensorLayout.SensorLayout layout : run.getSensorLayouts()) {
            final String sensorId = layout.sensorId;
            mDataController.setSensorStatsStatus(run.getRunId(), sensorId,
                    StatsAccumulator.STATUS_NEEDS_UPDATE,
                    new LoggingConsumer<Success>(TAG, "update stats") {
                        @Override
                        public void success(Success success) {
                            mStatsUpdated++;
                            if (mStatsUpdated == statsToUpdate) {
                                listener.onCropCompleted();
                            }
                            adjustRunStats(context, run, sensorId);
                        }
                    });
        }
    }

    private void adjustRunStats(final Context context, final ExperimentRun run,
            final String sensorId) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // Moves the current Thread into the background
                StatsAdjuster adjuster = new StatsAdjuster(sensorId, run, context);
                adjuster.recalculateStats(mDataController);
            }
        };
        // Update the stats in the background, without blocking anything.
        mCropStatsExecutor.execute(runnable);
    }

    // A class that recalculates and resaves the stats in a run.
    private class StatsAdjuster {
        private final String mSensorId;
        private final ExperimentRun mExperimentRun;
        private StatsAccumulator mStatsAccumulator;
        StreamConsumer mStreamConsumer;
        private Context mContext;

        public StatsAdjuster(String sensorId, ExperimentRun run, Context context) {
            mStatsAccumulator = new StatsAccumulator();
            mSensorId = sensorId;
            mExperimentRun = run;
            mStreamConsumer = new StreamConsumer() {
                @Override
                public void addData(long timestampMillis, double value) {
                    mStatsAccumulator.updateRecordingStreamStats(timestampMillis, value);
                }
            };
            mContext = context;
        }

        public void recalculateStats(DataController dc) {
            TimeRange range = TimeRange.oldest(Range.closed(mExperimentRun.getFirstTimestamp() - 1,
                    mExperimentRun.getLastTimestamp()));
            addReadingsToStats(dc, range);
        }

        private void addReadingsToStats(final DataController dc, final TimeRange range) {
            dc.getScalarReadings(mSensorId, /* tier 0 */ 0, range,
                    DATAPOINTS_PER_LOAD, new MaybeConsumer<ScalarReadingList>() {
                        @Override
                        public void success(ScalarReadingList list) {
                            list.deliver(mStreamConsumer);
                            if (list.size() == 0 || list.size() < DATAPOINTS_PER_LOAD ||
                                    mStatsAccumulator.getLatestTimestamp() >=
                                            mExperimentRun.getLastTimestamp()) {
                                // Done! Save back to the database.
                                // Note that we only need to save the stats we have changed, because
                                // each stat is stored seperately. We do not need to update stats
                                // like zoom tiers and zoom levels.
                                RunStats runStats = mStatsAccumulator.makeSaveableStats();
                                runStats.putStat(StatsAccumulator.KEY_STATUS,
                                        StatsAccumulator.STATUS_VALID);
                                dc.updateRunStats(mExperimentRun.getRunId(), mSensorId, runStats,
                                        new LoggingConsumer<Success>(TAG, "update stats") {
                                            @Override
                                            public void success(Success value) {
                                                sendStatsUpdatedBroadcast(mContext, mSensorId,
                                                        mExperimentRun.getRunId());
                                            }
                                        });
                            } else {
                                TimeRange nextRange = TimeRange.oldest(
                                        Range.openClosed(mStatsAccumulator.getLatestTimestamp(),
                                                mExperimentRun.getLastTimestamp()));
                                addReadingsToStats(dc, nextRange);
                            }
                        }

                        @Override
                        public void fail(Exception e) {
                            Log.e(TAG, "Error loading data to adjust stats after crop");
                        }
                    });
        }
    }

    // Use a Broadcast to tell RunReviewFragment or ExperimentDetailsFragment or anyone who uses
    // stats that the stats are updated for this sensor on this run.
    private static void sendStatsUpdatedBroadcast(Context context, String sensorId, String runId) {
        if (context == null) {
            return;
        }
        // Use a LocalBroadcastManager, because we do not need this broadcast outside the app.
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent();
        intent.setAction(ACTION_CROP_STATS_RECALCULATED);
        intent.putExtra(EXTRA_SENSOR_ID, sensorId);
        intent.putExtra(EXTRA_RUN_ID, runId);
        lbm.sendBroadcast(intent);
    }

    public static void registerStatsBroadcastReceiver(Context context, BroadcastReceiver receiver) {
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, STATS_INTENT_FILTER);
    }

    public static void unregisterBroadcastReceiver(Context context, BroadcastReceiver receiver) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
    }

    public static boolean experimentIsLongEnoughForCrop(ExperimentRun experimentRun) {
        // TODO: Update this to use getOriginal(First/Last)Timestamp when cropping wider is
        // implemented.
        return experimentRun.getLastTimestamp() - experimentRun.getFirstTimestamp() >
                CropHelper.MINIMUM_CROP_MILLIS;
    }

    public void throwAwayDataOutsideCroppedRegion(DataController dc, ExperimentRun run) {
        // TODO
    }
}
