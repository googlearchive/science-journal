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

import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;

import org.junit.Test;

public class StatefulRecorderTest {
    @Test
    public void doStartWhileStillRecording() {
        ManualSensor sensor = new ManualSensor("sensorId", 100, 100);
        RecordingDataController rdc = new InMemorySensorDatabase().makeSimpleRecordingController();
        RecordingSensorObserver rso = new RecordingSensorObserver();
        SensorRecorder recorder = sensor.createRecorder(null, rdc, rso);
        StatefulRecorder sr = new StatefulRecorder(recorder, null, null);

        sr.startObserving();
        sr.startRecording("runId");

        // Something goes wrong
        sensor.simulateExternalEventPreventingObservation();

        // Try to reset
        sr.stopObserving();
        sr.startObserving();

        // New value comes in.
        sensor.pushValue(0, 0);

        new TestData().addPoint(0, 0).checkObserver(rso);
    }
}