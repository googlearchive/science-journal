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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.RemoteException;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.AccumulatingConsumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class ScalarInputDiscovererTest {
    @Test
    public void testStartScanning() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ScalarInputDiscoverer sid = s.buildDiscoverer();

        AccumulatingConsumer<ExternalSensorDiscoverer.DiscoveredSensor> c =
                new AccumulatingConsumer<>();
        RecordingRunnable onScanDone = new RecordingRunnable();
        assertEquals(true,
                sid.startScanning(c, onScanDone, TestConsumers.expectingSuccess()));
        ExternalSensorSpec sensor = c.getOnlySeen().getSpec();
        ScalarInputSpec spec = (ScalarInputSpec) sensor;
        assertEquals(s.getSensorName(), spec.getName());
        assertEquals(s.getSensorAddress(), spec.getSensorAddressInService());
        assertEquals(s.getServiceId(), spec.getServiceId());
        assertEquals(ScalarInputSpec.TYPE, spec.getType());
        assertTrue(onScanDone.hasRun);
    }

    @Test
    public void testScanError() {
        final String serviceName = Arbitrary.string();
        final String errorText = Arbitrary.string();

        ScalarInputDiscoverer sid = new ScalarInputDiscoverer(
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(null, new TestSensorDiscoverer(serviceName) {
                            @Override
                            public void scanDevices(IDeviceConsumer c) throws RemoteException {
                                throw new RemoteException(errorText);
                            }

                            @Override
                            public void scanSensors(String actualDeviceId, ISensorConsumer c)
                                    throws RemoteException {
                                throw new RemoteException("Should never be thrown");
                            }

                            @Override
                            public ISensorConnector getConnector() throws RemoteException {
                                return null;
                            }
                        });
                    }
                }, new TestStringSource(), MoreExecutors.directExecutor(), new MockScheduler(), 100,
                new RecordingUsageTracker());

        AccumulatingConsumer<ExternalSensorDiscoverer.DiscoveredSensor> c =
                new AccumulatingConsumer<>();
        RecordingRunnable onScanDone = new RecordingRunnable();
        assertEquals(true, sid.startScanning(c, onScanDone, TestConsumers.expectingFailure(
                new FailureListener() {
                    @Override
                    public void fail(Exception e) {
                        String message = e.getMessage();
                        assertTrue(message.contains(errorText));
                    }
                })));
        assertEquals(0, c.seen.size());
        assertFalse(onScanDone.hasRun);
    }

    @Test
    public void testScanAllDevices() {
        ExplicitExecutor executor = new ExplicitExecutor();

        final TestSensorDiscoverer service1 = new TestSensorDiscoverer("service1", executor);
        service1.addDevice("deviceId1", "deviceName1");
        service1.addSensor("deviceId1", "sensorAddress1", "sensorName1");

        final TestSensorDiscoverer service2 = new TestSensorDiscoverer("service2", executor);
        service2.addDevice("deviceId2", "deviceName2");
        service2.addSensor("deviceId2", "sensorAddress2", "sensorName2");

        RecordingUsageTracker usageTracker = new RecordingUsageTracker();
        ScalarInputDiscoverer sid = new ScalarInputDiscoverer(
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound("serviceId1", service1);
                        adc.onServiceFound("serviceId2", service2);
                        adc.onDiscoveryDone();
                    }
                }, new TestStringSource(), executor, new MockScheduler(), 100,
                usageTracker);
        final AccumulatingConsumer<ExternalSensorDiscoverer.DiscoveredSensor> c =
                new AccumulatingConsumer<>();
        RecordingRunnable onScanDone = new RecordingRunnable() {
            @Override
            public void run() {
                super.run();

                // Make sure this isn't run until we've seen 2 sensors
                assertEquals(2, c.seen.size());
            }
        };
        sid.startScanning(c, onScanDone, TestConsumers.expectingSuccess());

        assertFalse(onScanDone.hasRun);
        executor.drain();
        assertTrue(onScanDone.hasRun);
        assertTrue(usageTracker.events.isEmpty());
    }

    @Test
    public void testTimeout() {
        final ScalarInputScenario s = new ScalarInputScenario();
        final TestSensorDiscoverer discoverer = new TestSensorDiscoverer(s.getServiceName()) {
            @Override
            protected void onDevicesDone(IDeviceConsumer c) {
                // override with empty implementation: we never call c.onScanDone, to test timeout
            }

            @Override
            protected void onSensorsDone(ISensorConsumer c) {
                // override with empty implementation: we never call c.onScanDone, to test timeout
            }
        };
        discoverer.addDevice(s.getDeviceId(), s.getDeviceName());
        discoverer.addSensor(s.getDeviceId(), s.getSensorAddress(), s.getSensorName());
        MockScheduler scheduler = new MockScheduler();

        RecordingUsageTracker usageTracker = new RecordingUsageTracker();
        ScalarInputDiscoverer sid = new ScalarInputDiscoverer(discoverer.makeFinder("serviceId"),
                new TestStringSource(), MoreExecutors.directExecutor(), scheduler, 100,
                usageTracker);

        AccumulatingConsumer<ExternalSensorDiscoverer.DiscoveredSensor> c =
                new AccumulatingConsumer<>();
        RecordingRunnable onScanDone = new RecordingRunnable();
        assertEquals(true,
                sid.startScanning(c, onScanDone, TestConsumers.expectingSuccess()));
        ExternalSensorSpec sensor = c.getOnlySeen().getSpec();
        ScalarInputSpec spec = (ScalarInputSpec) sensor;
        assertEquals(s.getSensorName(), spec.getName());

        assertFalse(onScanDone.hasRun);
        scheduler.incrementTime(200);
        assertTrue(onScanDone.hasRun);
        assertEquals(2, usageTracker.events.size());
        Set<String> eventLabels = new HashSet<>();
        for (TrackedEvent event : usageTracker.events) {
            eventLabels.add(event.getLabel());
        }
        assertTrue(eventLabels.toString(), eventLabels.contains("SERVICE:serviceId"));
        assertTrue(eventLabels.toString(), eventLabels.contains("DEVICE:" + s.getDeviceId()));
    }

    private Context getContext() {
        return null;
    }

}