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

import android.os.IBinder;
import android.os.RemoteException;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.RecordingStatusListener;
import com.google.android.apps.forscience.whistlepunk.TestData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

public class ScalarInputSensorTest {
    @Test
    public void useRefresherWhenRight() throws RemoteException {
        final TestFinder serviceFinder = new TestFinder();
        MockScheduler scheduler = new MockScheduler();
        SensorBehavior behavior = new SensorBehavior();
        behavior.expectedSamplesPerSecond = 0.1f;
        ScalarInputSpec spec = new ScalarInputSpec("sensorName", "serviceId", "address", behavior,
                null);
        ScalarInputSensor sis = new ScalarInputSensor("sensorId", MoreExecutors.directExecutor(),
                serviceFinder, new TestStringSource(),
                spec,
                scheduler);
        RecordingSensorObserver observer = new RecordingSensorObserver();
        SensorRecorder recorder = sis.createRecorder(null, observer,
                new RecordingStatusListener(),
                new MemorySensorEnvironment(
                        new InMemorySensorDatabase().makeSimpleRecordingController(), null, null,
                        scheduler.getClock()));
        recorder.startObserving();
        serviceFinder.observer.onNewData(0, 0.0);
        scheduler.schedule(Delay.millis(2500), new Runnable() {
            @Override
            public void run() {
                try {
                    serviceFinder.observer.onNewData(2500, 2.5);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        scheduler.incrementTime(6000);

        TestData testData = new TestData();
        testData.addPoint(0, 0.0);
        testData.addPoint(1000, 0.0);
        testData.addPoint(2000, 0.0);
        testData.addPoint(2500, 2.5);
        testData.addPoint(3500, 2.5);
        testData.addPoint(4500, 2.5);
        testData.addPoint(5500, 2.5);
        testData.checkObserver(observer);
        assertEquals(8, scheduler.getScheduleCount());

        recorder.stopObserving();
        scheduler.incrementTime(6000);
        assertEquals(8, scheduler.getScheduleCount());
    }


    private static class TestFinder extends Consumer<AppDiscoveryCallbacks> {
        public ISensorObserver observer;

        @Override
        public void take(AppDiscoveryCallbacks adc) {
            adc.onServiceFound("serviceId", new ISensorDiscoverer.Stub() {
                @Override
                public String getName() throws RemoteException {
                    return null;
                }

                @Override
                public void scanDevices(IDeviceConsumer c) throws RemoteException {

                }

                @Override
                public void scanSensors(String deviceId, ISensorConsumer c)
                        throws RemoteException {

                }

                @Override
                public ISensorConnector getConnector() throws RemoteException {
                    return new ISensorConnector() {

                        @Override
                        public void startObserving(String sensorAddress,
                                ISensorObserver observer,
                                ISensorStatusListener listener, String settingsKey)
                                throws RemoteException {
                            TestFinder.this.observer = observer;
                        }

                        @Override
                        public void stopObserving(String sensorAddress)
                                throws RemoteException {

                        }

                        @Override
                        public IBinder asBinder() {
                            return null;
                        }
                    };
                }
            });
        }
    }
}