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
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

public class ScalarInputDiscovererTest {
    @Test
    public void testStartScanning() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ScalarInputDiscoverer sid = s.buildDiscoverer();

        AccumulatingConsumer<ExternalSensorDiscoverer.DiscoveredSensor> c =
                new AccumulatingConsumer<>();
        RecordingRunnable onScanDone = new RecordingRunnable();
        assertEquals(true,
                sid.startScanning(c, onScanDone, TestConsumers.expectingSuccess(), getContext()));
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
                }, new TestStringSource(), MoreExecutors.directExecutor());

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
                }), getContext()));
        assertEquals(0, c.seen.size());
        assertFalse(onScanDone.hasRun);
    }

    private Context getContext() {
        return null;
    }

}