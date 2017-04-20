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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataControllerImpl;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link CropHelper}
 */
public class CropHelperTest extends AndroidTestCase {
    private DataControllerImpl mDataController;
    private MemoryMetadataManager mMetadataManager;
    private CropHelper.CropRunListener mCropRunListener;
    private boolean mCropCompleted = false;
    private boolean mCropFailed = false;
    private List<GoosciSensorLayout.SensorLayout> mSensorLayouts;
    private MaybeConsumer<ApplicationLabel> mAddLabelConsumer =
            new MaybeConsumer<ApplicationLabel>() {
                @Override
                public void success(ApplicationLabel value) {

                }

                @Override
                public void fail(Exception e) {

                }
            };

    @Before
    public void setUp() {
        mMetadataManager = new MemoryMetadataManager();
        mDataController = new InMemorySensorDatabase().makeSimpleController(mMetadataManager);
        mSensorLayouts = new ArrayList<>();
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "sensor";
        mSensorLayouts.add(layout);
        resetCropRunListener();
    }

    private void resetCropRunListener() {
        mCropCompleted = false;
        mCropFailed = false;
        mCropRunListener = new CropHelper.CropRunListener() {

            @Override
            public void onCropCompleted() {
                mCropCompleted = true;
            }

            @Override
            public void onCropFailed(int errorId) {
                mCropFailed = true;
            }
        };
    }

    // Adds stats to the MemoryMetadataManager for any test that has a getStats and expects
    // there to exist stats for this sensor.
    // In a real trial, stats are set when recording stops, so any crop should have some sensor
    // stats already set and this kind of thing is unnecessary. But we don't need to set legit stats
    // because we aren't checking the "pre-crop" values, so empty stats are fine here.
    private void setEmptyStats() {
        mDataController.setStats("0", "sensor", new TrialStats("sensor"),
                TestConsumers.<Success>expectingSuccess());
    }

    @Test
    public void testCropRun_failsOutsideBounds() {
        GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
        trialProto.trialId = "runId";
        trialProto.creationTimeMs = 42;
        trialProto.recordingRange = new GoosciTrial.Range();
        trialProto.recordingRange.startMs = 0;
        trialProto.recordingRange.endMs = 10;
        Trial trial = Trial.fromTrial(trialProto);

        ExperimentRun run = ExperimentRun.fromLabels(trial, "experimentId",
                Collections.<ApplicationLabel>emptyList());
        CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), mDataController);
        cropHelper.cropRun(null, run, -1, 10, mCropRunListener);
        assertTrue(mCropFailed);
        assertFalse(mCropCompleted);

        resetCropRunListener();
        cropHelper.cropRun(null, run, 1, 20, mCropRunListener);
        assertTrue(mCropFailed);
        assertFalse(mCropCompleted);
    }

    @Test
    public void testCropRun_alreadyCroppedRun() {
        mDataController.addCropApplicationLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "0", "0", 0),
                mAddLabelConsumer);
        mDataController.addCropApplicationLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "2000", "0", 2000),
                mAddLabelConsumer);
        mDataController.addCropApplicationLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_CROP_START, "2", "0", 2),
                mAddLabelConsumer);
        mDataController.addCropApplicationLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_CROP_END, "1008", "0", 1008),
                mAddLabelConsumer);
        mDataController.addScalarReading("sensor", 0, 50, 50);
        setEmptyStats();
        mMetadataManager.newTrial(new Experiment(42L), "0", 0, mSensorLayouts);
        mDataController.getExperimentRun("experiment", "0",
                new LoggingConsumer<ExperimentRun>("test", "test") {
                    @Override
                    public void success(ExperimentRun run) {
                        CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(),
                                mDataController);
                        cropHelper.cropRun(null, run, 4, 1006, mCropRunListener);
                        assertTrue(mCropCompleted);
                        assertEquals(run.getFirstTimestamp(), 4);
                        assertEquals(run.getLastTimestamp(), 1006);
                        assertEquals(run.getOriginalFirstTimestamp(), 0);
                        assertEquals(run.getOriginalLastTimestamp(), 2000);

                        assertTrue(mMetadataManager.getStats("0", "sensor").statsAreValid());
                    }
                });

    }

    @Test
    public void testCropRun_onUncroppedRun() {
        mDataController.addCropApplicationLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "0", "0", 0),
                mAddLabelConsumer);
        mDataController.addCropApplicationLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "2000", "0", 2000),
                mAddLabelConsumer);
        mMetadataManager.newTrial(new Experiment(42L), "0", 0, mSensorLayouts);
        mDataController.addScalarReading("sensor", 0, 1, 1); // This gets cropped out
        mDataController.addScalarReading("sensor", 0, 50, 50);
        mDataController.addScalarReading("sensor", 0, 60, 60);
        mDataController.addScalarReading("sensor", 0, 70, 70);
        setEmptyStats();
        mDataController.getExperimentRun("experiment", "0",
                new LoggingConsumer<ExperimentRun>("test", "test") {
                    @Override
                    public void success(ExperimentRun run) {
                        CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(),
                                mDataController);
                        cropHelper.cropRun(null, run, 2, 1008, mCropRunListener);
                        assertTrue(mCropCompleted);
                        assertEquals(run.getFirstTimestamp(), 2);
                        assertEquals(run.getLastTimestamp(), 1008);
                        assertEquals(run.getOriginalFirstTimestamp(), 0);
                        assertEquals(run.getOriginalLastTimestamp(), 2000);

                        TrialStats stats = mMetadataManager.getStats("0", "sensor");
                        assertTrue(stats.statsAreValid());
                        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.MINIMUM, -1), 50.0);
                        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.AVERAGE, -1), 60.0);
                        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, -1), 70.0);
                        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.NUM_DATA_POINTS, -1),
                                3.0);
                    }
                });
    }

    @Test
    public void testCropRun_sensorWithNoDataStatsInvalid() {
        mDataController.addCropApplicationLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "0", "0", 0),
                mAddLabelConsumer);
        mDataController.addCropApplicationLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "2000", "0", 2000),
                mAddLabelConsumer);
        mMetadataManager.newTrial(new Experiment(42L), "0", 0, mSensorLayouts);
        setEmptyStats();
        mDataController.getExperimentRun("experiment", "0",
                new LoggingConsumer<ExperimentRun>("test", "test") {
                    @Override
                    public void success(ExperimentRun run) {
                        CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(),
                                mDataController);
                        cropHelper.cropRun(null, run, 2, 1008, mCropRunListener);
                        assertTrue(mCropCompleted);
                        assertFalse(mMetadataManager.getStats("0", "sensor").statsAreValid());
                    }
                });
    }
}
