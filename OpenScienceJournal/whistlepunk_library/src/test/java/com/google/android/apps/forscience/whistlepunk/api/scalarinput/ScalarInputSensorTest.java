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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.RemoteException;

import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.RecordingStatusListener;
import com.google.android.apps.forscience.whistlepunk.TestData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

public class ScalarInputSensorTest {
    private final MockScheduler mScheduler = new MockScheduler();
    private final SensorBehavior mBehavior = new SensorBehavior();

    @Test
    public void useRefresherWhenRight() throws RemoteException {
        final TestFinder serviceFinder = new TestFinder("serviceId");
        mBehavior.expectedSamplesPerSecond = 0.1f;
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, new TestStringSource(), spec, mScheduler);
        RecordingSensorObserver observer = new RecordingSensorObserver();
        SensorRecorder recorder = makeRecorder(sis, observer, new RecordingStatusListener());
        recorder.startObserving();
        serviceFinder.observer.onNewData(0, 0.0);
        mScheduler.schedule(Delay.millis(2500), new Runnable() {
            @Override
            public void run() {
                try {
                    serviceFinder.observer.onNewData(2500, 2.5);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        mScheduler.incrementTime(6000);

        TestData testData = new TestData();
        testData.addPoint(0, 0.0);
        testData.addPoint(1000, 0.0);
        testData.addPoint(2000, 0.0);
        testData.addPoint(2500, 2.5);
        testData.addPoint(3500, 2.5);
        testData.addPoint(4500, 2.5);
        testData.addPoint(5500, 2.5);
        testData.checkObserver(observer);
        assertEquals(8, mScheduler.getScheduleCount());

        recorder.stopObserving();
        mScheduler.incrementTime(6000);
        assertEquals(8, mScheduler.getScheduleCount());
    }

    @Test
    public void backwardCompatibleServiceId() throws RemoteException {
        final TestFinder serviceFinder = new TestFinder("serviceId/ServiceClassName");
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, new TestStringSource(), spec, mScheduler);
        RecordingSensorObserver observer = new RecordingSensorObserver();
        SensorRecorder recorder = makeRecorder(sis, observer, new RecordingStatusListener());
        recorder.startObserving();
        serviceFinder.observer.onNewData(0, 0.0);

        TestData testData = new TestData();
        testData.addPoint(0, 0.0);
        testData.checkObserver(observer);
    }

    @Test
    public void connectedOnDataPoint() throws RemoteException {
        final TestFinder serviceFinder = new TestFinder("serviceId");
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ExplicitExecutor uiThread = new ExplicitExecutor();
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", uiThread, serviceFinder,
                new TestStringSource(), spec, mScheduler);
        RecordingSensorObserver observer = new RecordingSensorObserver();
        RecordingStatusListener listener = new RecordingStatusListener();
        SensorRecorder recorder = makeRecorder(sis, observer, listener);
        recorder.startObserving();
        uiThread.drain();
        serviceFinder.observer.onNewData(0, 0.0);

        // Make sure we aren't updated until the UI thread fires
        assertTrue(listener.mostRecentStatuses.isEmpty());
        uiThread.drain();

        assertEquals(SensorStatusListener.STATUS_CONNECTED,
                (int) listener.mostRecentStatuses.get("sensorId"));
    }

    @Test public void stopObservingAndListening() throws RemoteException {
        final TestFinder serviceFinder = new TestFinder("serviceId");
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, new TestStringSource(), spec, mScheduler);
        RecordingSensorObserver observer = new RecordingSensorObserver();
        RecordingStatusListener listener = new RecordingStatusListener();
        SensorRecorder recorder = makeRecorder(sis, observer, listener);
        recorder.startObserving();
        serviceFinder.observer.onNewData(0, 0.0);
        recorder.stopObserving();

        // More stuff after we stop observing
        serviceFinder.observer.onNewData(1, 1.0);
        serviceFinder.listener.onSensorError("Error after disconnect!");

        TestData testData = new TestData();
        testData.addPoint(0, 0.0);
        testData.checkObserver(observer);

        listener.assertNoErrors();
    }

    private SensorRecorder makeRecorder(ScalarInputSensor sis, RecordingSensorObserver observer,
            RecordingStatusListener listener) {
        return sis.createRecorder(null, observer, listener,
                new MemorySensorEnvironment(
                        new InMemorySensorDatabase().makeSimpleRecordingController(), null, null,
                        mScheduler.getClock()));
    }

}