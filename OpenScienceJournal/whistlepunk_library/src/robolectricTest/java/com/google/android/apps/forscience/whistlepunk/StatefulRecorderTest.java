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
package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertFalse;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class StatefulRecorderTest {
    private final ManualSensor mSensor = new ManualSensor("sensorId", 100, 100);
    private final RecordingDataController mDataController =
            new InMemorySensorDatabase().makeSimpleRecordingController();
    private final RecordingSensorObserver mObserver = new RecordingSensorObserver();
    private final SensorRecorder mRecorder =
            mSensor.createRecorder(null, mDataController, mObserver);

    @Test
    public void doStartWhileStillRecording() {
        StatefulRecorder sr = new StatefulRecorder(mRecorder, null, null);

        sr.startObserving();
        sr.startRecording("runId");

        // Something goes wrong
        mSensor.simulateExternalEventPreventingObservation();

        // Try to reset
        sr.reboot();

        // New value comes in.
        mSensor.pushValue(0, 0);

        new TestData().addPoint(0, 0).checkObserver(mObserver);
    }

    @Test
    public void stopObservingAfterStopRecording() {
        StatefulRecorder sr = new StatefulRecorder(mRecorder, null, null);
        sr.startObserving();
        sr.startRecording("runId");
        sr.stopObserving();
        sr.stopRecording(Trial.newTrial(100, new GoosciSensorLayout.SensorLayout[0],
                new FakeAppearanceProvider(), null));
        assertFalse(mSensor.isObserving());
    }
}