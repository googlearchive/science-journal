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
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link CropHelper}
 */
public class CropHelperTest extends AndroidTestCase {

    private DataController mDataController;
    private MemoryMetadataManager mMetadataManager;
    private Run mRun;
    private CropHelper.CropRunListener mCropRunListener;
    private boolean mCropCompleted = false;
    private boolean mCropFailed = false;
    private List<GoosciSensorLayout.SensorLayout> mSensorLayouts;
    private MaybeConsumer<Label> mAddLabelConsumer = new MaybeConsumer<Label>() {
        @Override
        public void success(Label value) {

        }

        @Override
        public void fail(Exception e) {

        }
    };

    @Before
    public void setUp() {
        mMetadataManager = new MemoryMetadataManager();
        mDataController = new InMemorySensorDatabase().makeSimpleController(mMetadataManager);
        mRun = new Run("runId", 42, null, false);
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
            public void onCropFailed() {
                mCropFailed = true;
            }
        };
    }

    @Test
    public void testCropRun_failsOutsideBounds() {
        ArrayList<Label> labels = new ArrayList<>();
        labels.add(new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "0", "0", 0));
        labels.add(new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "10", "0", 10));

        ExperimentRun run = ExperimentRun.fromLabels(mRun, labels);
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
        mDataController.addLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "0", "0", 0),
                mAddLabelConsumer);
        mDataController.addLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "10", "0", 10),
                mAddLabelConsumer);
        mDataController.addLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_CROP_START, "2", "0", 2),
                mAddLabelConsumer);
        mDataController.addLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_CROP_END, "8", "0", 8),
                mAddLabelConsumer);
        mMetadataManager.newRun(new Experiment(42L), "0", mSensorLayouts);
        mDataController.getExperimentRun("0", new LoggingConsumer<ExperimentRun>("test", "test") {
            @Override
            public void success(ExperimentRun run) {
                CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(),
                        mDataController);
                cropHelper.cropRun(null, run, 4, 6, mCropRunListener);
                assertTrue(mCropCompleted);
                assertEquals(run.getFirstTimestamp(), 4);
                assertEquals(run.getLastTimestamp(), 6);
                assertEquals(run.getOriginalFirstTimestamp(), 0);
                assertEquals(run.getOriginalLastTimestamp(), 10);

                // TODO: Add run data and check stats are updated properly

                assertEquals(mMetadataManager.getStats("0", "sensor").getIntStat(
                        StatsAccumulator.KEY_STATUS, StatsAccumulator.STATUS_NEEDS_UPDATE),
                        StatsAccumulator.STATUS_VALID);
            }
        });

    }

    @Test
    public void testCropRun_onUncroppedRun() {
        mDataController.addLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "0", "0", 0),
                mAddLabelConsumer);
        mDataController.addLabel(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "10", "0", 10),
                mAddLabelConsumer);
        mMetadataManager.newRun(new Experiment(42L), "0", mSensorLayouts);
        mDataController.getExperimentRun("0", new LoggingConsumer<ExperimentRun>("test", "test") {
            @Override
            public void success(ExperimentRun run) {
                CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(),
                        mDataController);
                cropHelper.cropRun(null, run, 2, 8, mCropRunListener);
                assertTrue(mCropCompleted);
                assertEquals(run.getFirstTimestamp(), 2);
                assertEquals(run.getLastTimestamp(), 8);
                assertEquals(run.getOriginalFirstTimestamp(), 0);
                assertEquals(run.getOriginalLastTimestamp(), 10);

                // TODO: Add run data and check stats are updated properly

                assertEquals(mMetadataManager.getStats("0", "sensor").getIntStat(
                        StatsAccumulator.KEY_STATUS, StatsAccumulator.STATUS_NEEDS_UPDATE),
                        StatsAccumulator.STATUS_VALID);
            }
        });
    }
}
