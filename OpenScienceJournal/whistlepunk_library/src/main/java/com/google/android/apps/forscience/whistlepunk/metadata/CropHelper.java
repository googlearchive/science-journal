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

import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
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
    private static final int DATAPOINTS_PER_LOAD = 500;

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

    public CropHelper() {
        this(Executors.newSingleThreadExecutor(new ProcessPriorityThreadFactory(
                android.os.Process.THREAD_PRIORITY_BACKGROUND)));
    }

    @VisibleForTesting
    public CropHelper(Executor executor) {
        mCropStatsExecutor = executor;
    }

    public void cropRun(final DataController dc, final ExperimentRun run, long startTimestamp,
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
            dc.editLabel(cropLabels.cropStartLabel,
                    new LoggingConsumer<Label>(TAG, "edit crop start label") {
                        @Override
                        public void success(Label value) {
                            dc.editLabel(cropLabels.cropEndLabel,
                                    new LoggingConsumer<Label>(TAG,"edit crop end label") {
                                        @Override
                                        public void success(Label value) {
                                            markRunStatsForAdjustment(dc, run, listener);
                                        }
                            });
                        }
            });
        } else if (cropLabels.cropStartLabel == null && cropLabels.cropStartLabel == null) {
            // Otherwise we make new crop labels.
            ApplicationLabel cropStartLabel = new ApplicationLabel(
                    ApplicationLabel.TYPE_CROP_START, dc.generateNewLabelId(), run.getRunId(),
                    startTimestamp);
            final ApplicationLabel cropEndLabel = new ApplicationLabel(
                    ApplicationLabel.TYPE_CROP_END, dc.generateNewLabelId(), run.getRunId(),
                    endTimestamp);
            cropStartLabel.setExperimentId(run.getExperimentId());
            cropEndLabel.setExperimentId(run.getExperimentId());

            // Update the run.
            cropLabels.cropStartLabel = cropStartLabel;
            cropLabels.cropEndLabel = cropEndLabel;

            // Add new crop labels to the database.
            dc.addLabel(cropStartLabel, new LoggingConsumer<Label>(TAG, "add crop start label") {
                @Override
                public void success(Label value) {
                    dc.addLabel(cropEndLabel,
                            new LoggingConsumer<Label>(TAG, "Add crop end label") {
                                @Override
                                public void success(Label value) {
                                    markRunStatsForAdjustment(dc, run, listener);
                                }
                            });
                }
            });
        } else {
            // One crop label is set and the other is not. This is an error!
            listener.onCropFailed();
        }
    }

    private void markRunStatsForAdjustment(final DataController dc, final ExperimentRun run,
            final CropRunListener listener) {
        // First delete the min/max/avg stats, but leave the rest available, because they are used
        // in loading data by ZoomPresenter. At this point, we can go back to RunReview.
        final int statsToUpdate = run.getSensorLayouts().size();
        mStatsUpdated = 0;
        for (GoosciSensorLayout.SensorLayout layout : run.getSensorLayouts()) {
            final String sensorId = layout.sensorId;
            dc.setSensorStatsStatus(run.getRunId(), sensorId,
                    StatsAccumulator.STATUS_NEEDS_UPDATE,
                    new LoggingConsumer<Success>(TAG, "update stats") {
                        @Override
                        public void success(Success success) {
                            mStatsUpdated++;
                            if (mStatsUpdated == statsToUpdate) {
                                listener.onCropCompleted();
                            }
                            adjustRunStats(dc, run, sensorId);
                        }
                    });
        }
    }

    private void adjustRunStats(final DataController dc, final ExperimentRun run,
            final String sensorId) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // Moves the current Thread into the background
                StatsAdjuster adjuster = new StatsAdjuster(sensorId);
                adjuster.recalculateStats(dc, run);
            }
        };
        // Update the stats in the background, without blocking anything.
        mCropStatsExecutor.execute(runnable);
    }

    // A class that recalculates and resaves the stats in a run.
    private class StatsAdjuster {
        private final String mSensorId;
        private StatsAccumulator mStatsAccumulator;
        StreamConsumer mStreamConsumer;

        public StatsAdjuster(String sensorId) {
            mStatsAccumulator = new StatsAccumulator();
            mSensorId = sensorId;
            mStreamConsumer = new StreamConsumer() {
                @Override
                public void addData(long timestampMillis, double value) {
                    mStatsAccumulator.updateRecordingStreamStats(timestampMillis, value);
                }
            };
        }

        public void recalculateStats(DataController dc, ExperimentRun run) {
            TimeRange range = TimeRange.oldest(Range.closed(run.getFirstTimestamp() - 1,
                    run.getLastTimestamp()));
            addReadingsToStats(dc, run, range);
        }

        private void addReadingsToStats(final DataController dc, final ExperimentRun run,
                final TimeRange range) {
            dc.getScalarReadings(mSensorId, /* tier 0 */ 0, range,
                    DATAPOINTS_PER_LOAD, new MaybeConsumer<ScalarReadingList>() {
                        @Override
                        public void success(ScalarReadingList list) {
                            list.deliver(mStreamConsumer);
                            if (list.size() == 0 || list.size() < DATAPOINTS_PER_LOAD ||
                                    mStatsAccumulator.getLatestTimestamp() >=
                                            run.getLastTimestamp()) {
                                // Done! Save back to the database.
                                // Note that we only need to save the stats we have changed, because
                                // each stat is stored seperately. We do not need to update stats
                                // like zoom tiers and zoom levels.
                                RunStats runStats = mStatsAccumulator.makeSaveableStats();
                                runStats.putStat(StatsAccumulator.KEY_STATUS,
                                        StatsAccumulator.STATUS_VALID);
                                dc.updateRunStats(run.getRunId(), mSensorId, runStats,
                                        new LoggingConsumer<Success>(TAG, "update stats") {
                                            @Override
                                            public void success(Success value) {
                                                // TODO: Use a Broadcast to tell RunReviewFragment
                                                // or ExperimentDetailsFragment or anyone who uses
                                                // stats that the stats are updated!
                                            }
                                        });
                            } else {
                                TimeRange nextRange = TimeRange.oldest(
                                        Range.openClosed(mStatsAccumulator.getLatestTimestamp(),
                                                run.getLastTimestamp()));
                                addReadingsToStats(dc, run, nextRange);
                            }
                        }

                        @Override
                        public void fail(Exception e) {
                            Log.e(TAG, "Error loading data to adjust stats after crop");
                        }
                    });
        }
    }

    public void throwAwayDataOutsideCroppedRegion(DataController dc, ExperimentRun run) {
        // TODO
    }
}
