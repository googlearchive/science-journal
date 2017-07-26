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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.DataControllerImpl;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link CropHelper}
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class CropHelperTest {
    private DataControllerImpl mDataController;
    private MemoryMetadataManager mMetadataManager;
    private CropHelper.CropTrialListener mCropTrialListener;
    private boolean mCropCompleted = false;
    private boolean mCropFailed = false;
    private GoosciSensorLayout.SensorLayout[] mSensorLayouts;
    private final double DELTA = 0.01;

    @Before
    public void setUp() {
        mMetadataManager = new MemoryMetadataManager();
        mDataController = new InMemorySensorDatabase().makeSimpleController(mMetadataManager);
        mSensorLayouts = new GoosciSensorLayout.SensorLayout[1];
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "sensor";
        mSensorLayouts[0] = layout;
        resetCropRunListener();
    }

    private void resetCropRunListener() {
        mCropCompleted = false;
        mCropFailed = false;
        mCropTrialListener = new CropHelper.CropTrialListener() {

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
    private void setEmptyStats(Experiment experiment, String trialId) {
        experiment.getTrial(trialId).setStats(new TrialStats("sensor"));
        mMetadataManager.updateExperiment(experiment);
    }

    private Trial makeCommonTrial() {
        GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
        trialProto.trialId = "0";
        trialProto.sensorLayouts = mSensorLayouts;
        GoosciTrial.Range recRange = new GoosciTrial.Range();
        recRange.startMs = 0;
        recRange.endMs = 2000;
        trialProto.recordingRange = recRange;
        return Trial.fromTrial(trialProto);
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

        Experiment experiment = Experiment.newExperiment(10, "experimentId", 0);
        experiment.addTrial(trial);

        CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), mDataController);
        cropHelper.cropTrial(null, experiment, "runId", -1, 10, mCropTrialListener);
        assertTrue(mCropFailed);
        assertFalse(mCropCompleted);

        resetCropRunListener();
        cropHelper.cropTrial(null, experiment, "runId", 1, 20, mCropTrialListener);
        assertTrue(mCropFailed);
        assertFalse(mCropCompleted);
    }

    @Test
    public void testCropRun_alreadyCroppedRun() {
        StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        mDataController.createExperiment(cExperiment);
        Experiment experiment = cExperiment.getValue();
        Trial trial = makeCommonTrial();
        GoosciTrial.Range cropRange = new GoosciTrial.Range();
        cropRange.startMs = 2;
        cropRange.endMs = 1008;
        trial.setCropRange(cropRange);
        experiment.addTrial(trial);
        mDataController.updateExperiment(experiment.getExperimentId(),
                TestConsumers.<Success>expectingSuccess());

        mDataController.addScalarReading("sensor", 0, 50, 50);
        setEmptyStats(experiment, trial.getTrialId());

        CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), mDataController);
        cropHelper.cropTrial(null, experiment, trial.getTrialId(), 4, 1006, mCropTrialListener);
        assertTrue(mCropCompleted);
        assertEquals(trial.getFirstTimestamp(), 4);
        assertEquals(trial.getLastTimestamp(), 1006);
        assertEquals(trial.getOriginalFirstTimestamp(), 0);
        assertEquals(trial.getOriginalLastTimestamp(), 2000);
        assertTrue(mMetadataManager.getExperimentById(experiment.getExperimentId())
                .getTrial(trial.getTrialId()).getStatsForSensor("sensor").statsAreValid());
    }

    @Test
    public void testCropRun_onUncroppedRun() {
        StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        mDataController.createExperiment(cExperiment);
        Experiment experiment = cExperiment.getValue();
        Trial trial = makeCommonTrial();
        experiment.addTrial(trial);
        mDataController.updateExperiment(experiment.getExperimentId(),
                TestConsumers.<Success>expectingSuccess());

        mDataController.addScalarReading("sensor", 0, 1, 1); // This gets cropped out
        mDataController.addScalarReading("sensor", 0, 50, 50);
        mDataController.addScalarReading("sensor", 0, 60, 60);
        mDataController.addScalarReading("sensor", 0, 70, 70);
        setEmptyStats(experiment, trial.getTrialId());
        CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), mDataController);
        cropHelper.cropTrial(null, experiment, trial.getTrialId(), 2, 1008, mCropTrialListener);
        assertTrue(mCropCompleted);
        assertEquals(trial.getFirstTimestamp(), 2);
        assertEquals(trial.getLastTimestamp(), 1008);
        assertEquals(trial.getOriginalFirstTimestamp(), 0);
        assertEquals(trial.getOriginalLastTimestamp(), 2000);

        TrialStats stats = mMetadataManager.getExperimentById(experiment.getExperimentId())
                .getTrial(trial.getTrialId()).getStatsForSensor("sensor");
        assertTrue(stats.statsAreValid());
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.MINIMUM, -1), 50.0, DELTA);
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.AVERAGE, -1), 60.0, DELTA);
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, -1), 70.0, DELTA);
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.NUM_DATA_POINTS, -1), 3.0, DELTA);
    }

    @Test
    public void testCropRun_sensorWithNoDataStatsInvalid() {
        StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        mDataController.createExperiment(cExperiment);
        Experiment experiment = cExperiment.getValue();
        final Trial trial = makeCommonTrial();
        experiment.addTrial(trial);
        mDataController.updateExperiment(experiment.getExperimentId(),
                TestConsumers.<Success>expectingSuccess());
        setEmptyStats(experiment, trial.getTrialId());
        CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), mDataController);
        cropHelper.cropTrial(null, experiment, trial.getTrialId(), 2, 1008, mCropTrialListener);
        assertTrue(mCropCompleted);
        assertFalse(mMetadataManager.getExperimentById(experiment.getExperimentId())
                .getTrial(trial.getTrialId()).getStatsForSensor("sensor").statsAreValid());
    }
}
