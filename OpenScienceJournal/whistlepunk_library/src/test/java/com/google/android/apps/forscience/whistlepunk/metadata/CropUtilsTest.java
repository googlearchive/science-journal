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

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Tests for {@link CropUtils}
 */
public class CropUtilsTest {

    private DataController mDataController;
    private MemoryMetadataManager mMetadataManager;
    private Run mRun;
    private CropUtils.CropRunListener mCropRunListener;
    private boolean mCropCompleted = false;
    private boolean mCropFailed = false;
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
        resetCropRunListener();
    }

    private void resetCropRunListener() {
        mCropCompleted = false;
        mCropFailed = false;
        mCropRunListener = new CropUtils.CropRunListener() {

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
        CropUtils.cropRun(mDataController, run, -1, 10, mCropRunListener);
        assertTrue(mCropFailed);
        assertFalse(mCropCompleted);

        resetCropRunListener();
        CropUtils.cropRun(mDataController, run, 1, 20, mCropRunListener);
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
        mMetadataManager.newRun(new Experiment(42L), "0",
                Collections.<GoosciSensorLayout.SensorLayout>emptyList());
        mDataController.getExperimentRun("0", new LoggingConsumer<ExperimentRun>("test", "test") {
            @Override
            public void success(ExperimentRun run) {
                CropUtils.cropRun(mDataController, run, 4, 6, mCropRunListener);
                assertTrue(mCropCompleted);
                assertEquals(run.getFirstTimestamp(), 4);
                assertEquals(run.getLastTimestamp(), 6);
                assertEquals(run.getOriginalFirstTimestamp(), 0);
                assertEquals(run.getOriginalLastTimestamp(), 10);
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
        mMetadataManager.newRun(new Experiment(42L), "0",
                Collections.<GoosciSensorLayout.SensorLayout>emptyList());
        mDataController.getExperimentRun("0", new LoggingConsumer<ExperimentRun>("test", "test") {
            @Override
            public void success(ExperimentRun run) {
                CropUtils.cropRun(mDataController, run, 2, 8, mCropRunListener);
                assertTrue(mCropCompleted);
                assertEquals(run.getFirstTimestamp(), 2);
                assertEquals(run.getLastTimestamp(), 8);
                assertEquals(run.getOriginalFirstTimestamp(), 0);
                assertEquals(run.getOriginalLastTimestamp(), 10);
            }
        });
    }
}
