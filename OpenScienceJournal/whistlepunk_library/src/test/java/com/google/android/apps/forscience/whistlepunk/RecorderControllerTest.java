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

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FakeBleClient;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

public class RecorderControllerTest {
    private String mSensorId = "sensorId";
    private final ManualSensor mSensor = new ManualSensor(mSensorId, 100, 100);
    private final SensorRegistry mSensorRegistry = new SensorRegistry() {
        @Override
        public void withSensorChoice(String tag, String id, Consumer<SensorChoice> consumer) {
            assertEquals(mSensorId, id);
            consumer.take(mSensor);
        }
    };
    private final MemorySensorEnvironment mEnvironment = new MemorySensorEnvironment(
            new InMemorySensorDatabase().makeSimpleRecordingController(), new FakeBleClient(null),
            new MemorySensorHistoryStorage(), null);

    @Test
    public void multipleObservers() {
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mSensorRegistry, mEnvironment,
                new RecorderListenerRegistry(), null, null, null, Delay.ZERO);
        RecordingSensorObserver observer1 = new RecordingSensorObserver();
        RecordingSensorObserver observer2 = new RecordingSensorObserver();

        mSensor.pushValue(0, 0);
        String id1 = rc.startObserving(mSensorId, Collections.<SensorTrigger>emptyList(), observer1,
                new StubStatusListener(), null);
        mSensor.pushValue(1, 1);
        String id2 = rc.startObserving(mSensorId, Collections.<SensorTrigger>emptyList(), observer2,
                new StubStatusListener(), null);
        mSensor.pushValue(2, 2);
        rc.stopObserving(mSensorId, id1);
        mSensor.pushValue(3, 3);
        rc.stopObserving(mSensorId, id2);
        mSensor.pushValue(4, 4);

        TestData.allPointsBetween(1, 2, 1).checkObserver(observer1);
        TestData.allPointsBetween(2, 3, 1).checkObserver(observer2);
    }

    @Test
    public void layoutLogging() {
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mSensorRegistry, mEnvironment,
                new RecorderListenerRegistry(), null, null, null, Delay.ZERO);

        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "aa:bb:cc:dd";
        layout.cardView = GoosciSensorLayout.SensorLayout.GRAPH;
        layout.audioEnabled = false;
        String loggingId = BleSensorSpec.TYPE;

        assertEquals("bluetooth_le|graph|audioOff", rc.getLayoutLoggingString(loggingId, layout));

        layout.cardView = GoosciSensorLayout.SensorLayout.METER;
        assertEquals("bluetooth_le|meter|audioOff", rc.getLayoutLoggingString(loggingId, layout));

        layout.audioEnabled = true;
        assertEquals("bluetooth_le|meter|audioOn", rc.getLayoutLoggingString(loggingId, layout));

        layout.sensorId = "AmbientLight";
        assertEquals("AmbientLight|meter|audioOn",
                rc.getLayoutLoggingString("AmbientLight", layout));
    }

    @Test
    public void delayStopObserving() {
        TestTrigger trigger = new TestTrigger(mSensorId);
        ArrayList<SensorTrigger> triggerList = Lists.<SensorTrigger>newArrayList(trigger);
        MockScheduler scheduler = new MockScheduler();
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mSensorRegistry, mEnvironment,
                new RecorderListenerRegistry(), null, null, scheduler, Delay.seconds(15));
        String observeId1 = rc.startObserving(mSensorId, Lists.<SensorTrigger>newArrayList(),
                new RecordingSensorObserver(), new RecordingStatusListener(), null);
        rc.stopObserving(mSensorId, observeId1);

        // Redundant call should have no effect.
        rc.stopObserving(mSensorId, observeId1);
        scheduler.incrementTime(1000);

        // Should still be observing 1s later.
        assertTrue(mSensor.isObserving());

        // Start observing again before the delay is hit
        String observeId2 = rc.startObserving(mSensorId, triggerList, new RecordingSensorObserver(),
                new RecordingStatusListener(), null);

        // Make sure there's no delayed stop commands waiting to strike
        scheduler.incrementTime(30000);
        assertTrue(mSensor.isObserving());

        // And we have correctly picked up the new trigger list.
        trigger.clearTestCount();
        mSensor.pushValue(0, 0);
        assertEquals(1, trigger.getTestCount());

        // Finally, after appropriate delay, sensor stops.
        rc.stopObserving(mSensorId, observeId2);
        assertTrue(mSensor.isObserving());
        scheduler.incrementTime(16000);
        assertFalse(mSensor.isObserving());
    }

    @Test
    public void dontScheduleIfDelayIs0() {
        MockScheduler scheduler = new MockScheduler();
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mSensorRegistry, mEnvironment,
                new RecorderListenerRegistry(), null, null, scheduler, Delay.ZERO);
        String observeId1 = rc.startObserving(mSensorId, null, new RecordingSensorObserver(),
                new RecordingStatusListener(), null);
        rc.stopObserving(mSensorId, observeId1);
        assertEquals(0, scheduler.getScheduleCount());
    }

    @Test
    public void reboot() {
        MockScheduler scheduler = new MockScheduler();
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mSensorRegistry, mEnvironment,
                new RecorderListenerRegistry(), null, null, scheduler, Delay.ZERO);
        rc.startObserving(mSensorId, null, new RecordingSensorObserver(),
                new RecordingStatusListener(), null);
        mSensor.simulateExternalEventPreventingObservation();
        assertFalse(mSensor.isObserving());
        rc.reboot(mSensorId);
        assertTrue(mSensor.isObserving());
    }

    private class TestTrigger extends SensorTrigger {
        int mTestCount = 0;

        public TestTrigger(String sensorId) {
            super("triggerId", sensorId, 0, 0, 0);
        }

        public void clearTestCount() {
            mTestCount = 0;
        }

        public int getTestCount() {
            return mTestCount;
        }

        @Override
        public boolean isTriggered(double newValue) {
            mTestCount++;
            return false;
        }
    }
}