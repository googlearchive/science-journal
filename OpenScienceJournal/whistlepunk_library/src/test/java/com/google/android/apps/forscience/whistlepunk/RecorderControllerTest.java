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

import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.FakeUnitAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FakeBleClient;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import io.reactivex.Maybe;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecorderControllerTest {
    private final MockScheduler mScheduler = new MockScheduler();
    private String mSensorId = "sensorId";
    private final ManualSensorRegistry mSensorRegistry = new ManualSensorRegistry();
    private final ManualSensor mSensor = mSensorRegistry.addSensor(mSensorId);
    private final InMemorySensorDatabase mDatabase = new InMemorySensorDatabase();
    private final DataControllerImpl mDataController = mDatabase.makeSimpleController();
    private final MemorySensorEnvironment mEnvironment = new MemorySensorEnvironment(
            mDatabase.makeSimpleRecordingController(), new FakeBleClient(null),
            new MemorySensorHistoryStorage(), null);

    @Test
    public void multipleObservers() {
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mEnvironment,
                new RecorderListenerRegistry(), null, null, null, Delay.ZERO,
                new FakeUnitAppearanceProvider());
        RecordingSensorObserver observer1 = new RecordingSensorObserver();
        RecordingSensorObserver observer2 = new RecordingSensorObserver();

        mSensor.pushValue(0, 0);
        String id1 = rc.startObserving(mSensorId, Collections.<SensorTrigger>emptyList(), observer1,
                new StubStatusListener(), null, mSensorRegistry);
        mSensor.pushValue(1, 1);
        String id2 = rc.startObserving(mSensorId, Collections.<SensorTrigger>emptyList(), observer2,
                new StubStatusListener(), null, mSensorRegistry);
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
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mEnvironment,
                new RecorderListenerRegistry(), null, null, null, Delay.ZERO,
                new FakeUnitAppearanceProvider());

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
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mEnvironment,
                new RecorderListenerRegistry(), null, null, mScheduler, Delay.seconds(15),
                new FakeUnitAppearanceProvider());
        String observeId1 = rc.startObserving(mSensorId, Lists.<SensorTrigger>newArrayList(),
                new RecordingSensorObserver(), new RecordingStatusListener(), null,
                mSensorRegistry);
        rc.stopObserving(mSensorId, observeId1);

        // Redundant call should have no effect.
        rc.stopObserving(mSensorId, observeId1);
        mScheduler.incrementTime(1000);

        // Should still be observing 1s later.
        assertTrue(mSensor.isObserving());

        // Start observing again before the delay is hit
        String observeId2 = rc.startObserving(mSensorId, triggerList, new RecordingSensorObserver(),
                new RecordingStatusListener(), null, mSensorRegistry);

        // Make sure there's no delayed stop commands waiting to strike
        mScheduler.incrementTime(30000);
        assertTrue(mSensor.isObserving());

        // And we have correctly picked up the new trigger list.
        trigger.clearTestCount();
        mSensor.pushValue(0, 0);
        assertEquals(1, trigger.getTestCount());

        // Finally, after appropriate delay, sensor stops.
        rc.stopObserving(mSensorId, observeId2);
        assertTrue(mSensor.isObserving());
        mScheduler.incrementTime(16000);
        assertFalse(mSensor.isObserving());
    }

    @Test
    public void dontScheduleIfDelayIs0() {
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mEnvironment,
                new RecorderListenerRegistry(), null, null, mScheduler, Delay.ZERO,
                new FakeUnitAppearanceProvider());
        String observeId1 = rc.startObserving(mSensorId, null, new RecordingSensorObserver(),
                new RecordingStatusListener(), null, mSensorRegistry);
        rc.stopObserving(mSensorId, observeId1);
        assertEquals(0, mScheduler.getScheduleCount());
    }

    @Test
    public void reboot() {
        RecorderControllerImpl rc = new RecorderControllerImpl(null, mEnvironment,
                new RecorderListenerRegistry(), null, null, mScheduler, Delay.ZERO,
                new FakeUnitAppearanceProvider());
        rc.startObserving(mSensorId, null, new RecordingSensorObserver(),
                new RecordingStatusListener(), null, mSensorRegistry);
        mSensor.simulateExternalEventPreventingObservation();
        assertFalse(mSensor.isObserving());
        rc.reboot(mSensorId);
        assertTrue(mSensor.isObserving());
    }

    @Test
    public void takeSnapshot() {
        final RecorderControllerImpl rc =
                new RecorderControllerImpl(null, mEnvironment,
                        new RecorderListenerRegistry(), null, mDataController, mScheduler,
                        Delay.ZERO, new FakeUnitAppearanceProvider());

        rc.startObserving(mSensorId, new ArrayList<SensorTrigger>(), new RecordingSensorObserver(),
                new RecordingStatusListener(), null, mSensorRegistry);

        Maybe<String> snapshot =
                rc.generateSnapshotText(Lists.<String>newArrayList(mSensorId),
                        s -> "sensor");
        mSensor.pushValue(10, 50);
        assertEquals("[sensor has value 50.0]",
                snapshot.test().assertNoErrors().values().toString());
    }

    @Test
    public void snapshotWithNoSensors() {
        final RecorderControllerImpl rc =
                new RecorderControllerImpl(null, mEnvironment,
                        new RecorderListenerRegistry(), null, mDataController, mScheduler,
                        Delay.ZERO, new FakeUnitAppearanceProvider());

        Maybe<String> snapshot =
                rc.generateSnapshotText(Lists.<String>newArrayList(mSensorId), null);
        assertEquals("[No sensors observed]", snapshot.test().values().toString());
    }

    @Test
    public void snapshotWithThreeSensors() {
        ManualSensor s2 = mSensorRegistry.addSensor("s2");
        ManualSensor s3 = mSensorRegistry.addSensor("s3");

        final RecorderControllerImpl rc =
                new RecorderControllerImpl(null, mEnvironment,
                        new RecorderListenerRegistry(), null, mDataController, mScheduler,
                        Delay.ZERO, new FakeUnitAppearanceProvider());

        ArrayList<SensorTrigger> triggers = new ArrayList<>();
        rc.startObserving(mSensorId, triggers, new RecordingSensorObserver(),
                new RecordingStatusListener(), null, mSensorRegistry);
        rc.startObserving(s2.getId(), triggers, new RecordingSensorObserver(),
                new RecordingStatusListener(), null, mSensorRegistry);
        rc.startObserving(s3.getId(), triggers, new RecordingSensorObserver(),
                new RecordingStatusListener(), null, mSensorRegistry);

        // Some sensors may publish earlier
        s2.pushValue(12, 52);
        s3.pushValue(13, 53);
        final ImmutableMap<String, String> names =
                ImmutableMap.of(mSensorId, "A1", s2.getId(), "B2", s3.getId(), "C3");
        Maybe<String> snapshot =
                rc.generateSnapshotText(Lists.newArrayList(mSensorId, s2.getId(), s3.getId()),
                        s -> names.get(s));
        // Some may publish later
        mSensor.pushValue(11, 51);
        assertEquals("[A1 has value 51.0, B2 has value 52.0, C3 has value 53.0]",
                snapshot.test().assertNoErrors().values().toString());
    }

    @Test
    public void dontCacheSensorValuesBetweenObservation() {
        final RecorderControllerImpl rc =
                new RecorderControllerImpl(null, mEnvironment,
                        new RecorderListenerRegistry(), null, mDataController, mScheduler,
                        Delay.ZERO, new FakeUnitAppearanceProvider());

        String observerId = rc.startObserving(mSensorId, new ArrayList<SensorTrigger>(),
                new RecordingSensorObserver(), new RecordingStatusListener(), null,
                mSensorRegistry);
        mSensor.pushValue(10, 50);

        // This should clear the latest value
        rc.stopObserving(mSensorId, observerId);

        // Restart observing
        rc.startObserving(mSensorId, new ArrayList<SensorTrigger>(), new RecordingSensorObserver(),
                new RecordingStatusListener(), null, mSensorRegistry);

        Maybe<String> snapshot = rc.generateSnapshotText(Lists.<String>newArrayList(mSensorId),
                s -> "sensor");
        snapshot.test().assertNotComplete();
        mSensor.pushValue(20, 60);
        assertEquals("[sensor has value 60.0]",
                snapshot.test().assertNoErrors().values().toString());
    }

    private class TestTrigger extends SensorTrigger {
        int mTestCount = 0;

        public TestTrigger(String sensorId) {
            super(sensorId, 0, 0, 0);
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