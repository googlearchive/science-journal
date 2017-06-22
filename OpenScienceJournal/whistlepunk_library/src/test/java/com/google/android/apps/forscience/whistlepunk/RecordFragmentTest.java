/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.FakeUnitAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FakeBleClient;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import io.reactivex.observers.TestObserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecordFragmentTest {
    @Test
    public void addSnapshotLabel() throws InterruptedException {
        InMemorySensorDatabase db = new InMemorySensorDatabase();
        ManualSensorRegistry reg = new ManualSensorRegistry();
        DataControllerImpl dc = db.makeSimpleController();
        ManualSensor sensor = reg.addSensor("id");
        final long now = Math.abs(Arbitrary.longInteger());

        MemorySensorEnvironment env =
                new MemorySensorEnvironment(db.makeSimpleRecordingController(),
                        new FakeBleClient(null), new MemorySensorHistoryStorage(), () -> now);

        final RecorderControllerImpl rc =
                new RecorderControllerImpl(null, reg, env,
                        new RecorderListenerRegistry(), null, dc, null, Delay.ZERO,
                        new FakeUnitAppearanceProvider());

        rc.startObserving("id", new ArrayList<SensorTrigger>(), new RecordingSensorObserver(),
                new RecordingStatusListener(), null);
        sensor.pushValue(now - 10, 55);

        StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        dc.createExperiment(cExperiment);
        Experiment exp = cExperiment.getValue();

        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "id";
        exp.getSensorLayouts().add(layout);

        TestObserver<Label> test =
                RecordFragment.addSnapshotLabelToExperiment(exp, rc, dc, sensorId -> sensorId)
                              .test();
        assertTrue(test.await(2, TimeUnit.SECONDS));
        test.assertComplete();

        assertEquals("id has value 55.0", exp.getLabels().get(0).getTextLabelValue().text);
    }
}