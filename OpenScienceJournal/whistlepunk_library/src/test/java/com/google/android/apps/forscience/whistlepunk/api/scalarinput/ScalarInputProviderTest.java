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
import static org.junit.Assert.assertNotNull;

import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.RecordingStatusListener;
import com.google.android.apps.forscience.whistlepunk.TestData;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.Executor;

public class ScalarInputProviderTest {
    private final RecordingSensorObserver mObserver = new RecordingSensorObserver();
    private final RecordingStatusListener mListener = new RecordingStatusListener();
    private Executor mExecutor = MoreExecutors.directExecutor();

    @Test
    public void grabDataFromService() {
        final List<ChartData.DataPoint> dataToSend = makeData();
        final String sensorId = Arbitrary.string();
        final String sensorAddress = Arbitrary.string();
        final String serviceId = Arbitrary.string();

        Consumer<AppDiscoveryCallbacks> finder =
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(serviceId,
                                new TestDiscoverer(new TestConnector(dataToSend, sensorAddress)));
                    }
                };
        ExternalSensorProvider provider = new ScalarInputProvider(finder, null, mExecutor);

        SensorChoice sensor = provider.buildSensor(sensorId,
                new ScalarInputSpec("sensorName", serviceId, sensorAddress, null, null, true));
        SensorRecorder recorder = createRecorder(sensor);
        recorder.startObserving();
        Integer integer = mListener.mostRecentStatuses.get(sensorId);
        assertNotNull(sensorId + sensorAddress + mListener.mostRecentStatuses.keySet(), integer);
        assertEquals(SensorStatusListener.STATUS_CONNECTED, (int) integer);
        recorder.stopObserving();
        assertEquals(SensorStatusListener.STATUS_DISCONNECTED,
                (int) mListener.mostRecentStatuses.get(sensorId));
        mListener.assertNoErrors();
        TestData.fromPoints(dataToSend).checkObserver(mObserver);
    }

    @Test
    public void reportErrors() {
        final String sensorId = Arbitrary.string();
        final String sensorAddress = Arbitrary.string();
        final String serviceId = Arbitrary.string();
        final String errorMessage = Arbitrary.string();

        Consumer<AppDiscoveryCallbacks> finder =
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(serviceId,
                                new TestDiscoverer(new ISensorConnector.Stub() {
                                    @Override
                                    public void startObserving(String sensorId,
                                            ISensorObserver observer,
                                            ISensorStatusListener listener, String settingsKey)
                                            throws RemoteException {
                                        listener.onSensorError(errorMessage);
                                    }

                                    @Override
                                    public void stopObserving(String sensorId)
                                            throws RemoteException {

                                    }
                                }));
                    }
                };

        ExternalSensorProvider provider = new ScalarInputProvider(finder, null, mExecutor);
        SensorChoice sensor = provider.buildSensor(sensorId,
                new ScalarInputSpec("sensorName", serviceId, sensorAddress, null, null, true));
        SensorRecorder recorder = createRecorder(sensor);
        recorder.startObserving();
        mListener.assertErrors(errorMessage);
    }

    @Test
    public void properServiceNotInstalled() {
        final List<ChartData.DataPoint> dataToSend = makeData();
        final String sensorId = Arbitrary.string();
        final String sensorAddress = Arbitrary.string();
        final String serviceId = Arbitrary.string();

        Consumer<AppDiscoveryCallbacks> finder =
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(serviceId + "wrong!",
                                new TestDiscoverer(new TestConnector(dataToSend, sensorId)));
                        adc.onDiscoveryDone();
                    }
                };

        ExternalSensorProvider provider = new ScalarInputProvider(finder,
                new ScalarInputStringSource() {
                    @Override
                    public String generateCouldNotFindServiceErrorMessage(String serviceId) {
                        return "Could not find service: " + serviceId;
                    }
                },
                mExecutor);
        SensorChoice sensor = provider.buildSensor(sensorId,
                new ScalarInputSpec("sensorName", serviceId, sensorAddress, null, null, true));
        SensorRecorder recorder = createRecorder(sensor);
        recorder.startObserving();
        mListener.assertErrors("Could not find service: " + serviceId);
    }

    @Test
    public void findCorrectServiceOfSeveral() {
        final List<ChartData.DataPoint> dataToSend = makeData();
        final String sensorId = Arbitrary.string();
        final String sensorAddress = Arbitrary.string();
        final String serviceId = Arbitrary.string();

        final List<ChartData.DataPoint> wrongPoints = makeData();

        Consumer<AppDiscoveryCallbacks> finder =
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(serviceId,
                                new TestDiscoverer(new TestConnector(dataToSend, sensorAddress)));
                        adc.onServiceFound(serviceId + "wrong!",
                                new TestDiscoverer(new TestConnector(wrongPoints, sensorAddress)));
                    }
                };

        ExternalSensorProvider provider = new ScalarInputProvider(finder, null, mExecutor);
        SensorChoice sensor = provider.buildSensor(sensorId,
                new ScalarInputSpec("sensorName", serviceId, sensorAddress, null, null, true));
        SensorRecorder recorder = createRecorder(sensor);
        recorder.startObserving();
        recorder.stopObserving();
        TestData.fromPoints(dataToSend).checkObserver(mObserver);
    }

    @NonNull
    private List<ChartData.DataPoint> makeData() {
        long value = Math.abs(Arbitrary.longInteger());
        return Lists.newArrayList(new ChartData.DataPoint(value, value));
    }

    private SensorRecorder createRecorder(SensorChoice sensor) {
        return sensor.createRecorder(null, mObserver, mListener, createEnvironment());
    }

    @NonNull
    private MemorySensorEnvironment createEnvironment() {
        return new MemorySensorEnvironment(
                new InMemorySensorDatabase().makeSimpleRecordingController(), null, null);
    }
}