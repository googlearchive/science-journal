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
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.whistlepunk.BuildConfig;
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
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class ScalarInputSensorTest {
    private final MockScheduler mScheduler = new MockScheduler();
    private final SensorBehavior mBehavior = new SensorBehavior();
    private final RecordingSensorObserver mObserver = new RecordingSensorObserver();
    private final RecordingStatusListener mListener = new RecordingStatusListener();

    @Test
    public void useRefresherWhenRight() throws RemoteException {
        final TestFinder serviceFinder = new TestFinder("serviceId");
        mBehavior.expectedSamplesPerSecond = 0.1f;
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, new TestStringSource(), spec, mScheduler);
        SensorRecorder recorder = makeRecorder(sis);
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
        testData.checkObserver(mObserver);
        assertEquals(9, mScheduler.getScheduleCount());

        recorder.stopObserving();
        mScheduler.incrementTime(6000);
        assertEquals(9, mScheduler.getScheduleCount());
    }

    @Test
    public void backwardCompatibleServiceId() throws RemoteException {
        final TestFinder serviceFinder = new TestFinder("serviceId/ServiceClassName");
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, new TestStringSource(), spec, mScheduler);
        SensorRecorder recorder = makeRecorder(sis);
        recorder.startObserving();
        serviceFinder.observer.onNewData(0, 0.0);

        TestData testData = new TestData();
        testData.addPoint(0, 0.0);
        testData.checkObserver(mObserver);
    }

    @Test
    public void connectedOnDataPoint() throws RemoteException {
        final TestFinder serviceFinder = neverConnectFinder();
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ExplicitExecutor uiThread = new ExplicitExecutor();
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", uiThread, serviceFinder,
                new TestStringSource(), spec, mScheduler);
        SensorRecorder recorder = makeRecorder(sis);
        recorder.startObserving();
        uiThread.drain();
        serviceFinder.observer.onNewData(0, 0.0);

        // Make sure we aren't updated until the UI thread fires
        assertTrue(mListener.mostRecentStatuses.isEmpty());
        uiThread.drain();

        assertEquals(SensorStatusListener.STATUS_CONNECTED,
                (int) mListener.mostRecentStatuses.get("sensorId"));
    }

    @Test
    public void stopObservingAndListening() throws RemoteException {
        final TestFinder serviceFinder = new TestFinder("serviceId");
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, new TestStringSource(), spec, mScheduler);
        SensorRecorder recorder = makeRecorder(sis);
        recorder.startObserving();
        serviceFinder.observer.onNewData(0, 0.0);
        recorder.stopObserving();

        // More stuff after we stop observing
        serviceFinder.observer.onNewData(1, 1.0);
        serviceFinder.listener.onSensorError("Error after disconnect!");

        TestData testData = new TestData();
        testData.addPoint(0, 0.0);
        testData.checkObserver(mObserver);

        mListener.assertNoErrors();

        recorder.startObserving();
        serviceFinder.observer.onNewData(2, 2.0);
        serviceFinder.listener.onSensorError("Error after reconnect!");

        TestData data2 = new TestData();
        data2.addPoint(0, 0.0);
        data2.addPoint(2, 2.0);
        data2.checkObserver(mObserver);

        mListener.assertErrors("Error after reconnect!");
    }

    @Test
    public void sensorTimeout() {
        final TestFinder serviceFinder = neverConnectFinder();
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        TestStringSource stringSource = new TestStringSource();
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, stringSource, spec, mScheduler);
        SensorRecorder recorder = makeRecorder(sis);
        recorder.startObserving();
        mListener.assertNoErrors();
        mScheduler.incrementTime(ScalarInputSensor.CONNECTION_TIME_OUT.asMillis() + 10);
        mListener.assertErrors(stringSource.generateConnectionTimeoutMessage());
    }

    @NonNull
    private TestFinder neverConnectFinder() {
        return new TestFinder("serviceId") {
            @Override
            protected void signalConnected(ISensorStatusListener listener) throws RemoteException {
                // Override (correct) superclass definition of signalConnected, to simulate
                // a sensor that (incorrectly) never reports a connection.
            }
        };
    }

    @Test
    public void dontTimeoutIfDisconnectedAndReconnected() {
        final TestFinder serviceFinder = neverConnectFinder();
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", mBehavior,
                null, "devId");
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, new TestStringSource(), spec, mScheduler);
        SensorRecorder recorder = makeRecorder(sis);
        recorder.startObserving();
        recorder.stopObserving();
        mScheduler.incrementTime(ScalarInputSensor.CONNECTION_TIME_OUT.asMillis() - 100);
        recorder.startObserving();
        mScheduler.incrementTime(200);
        mListener.assertNoErrors();
    }


    private SensorRecorder makeRecorder(ScalarInputSensor sis) {
        return sis.createRecorder(null, mObserver, mListener, new MemorySensorEnvironment(
                new InMemorySensorDatabase().makeSimpleRecordingController(), null, null,
                mScheduler.getClock()));
    }
}