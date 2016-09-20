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

import android.os.RemoteException;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.AccumulatingConsumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

// TODO: make this a non-device test

public class ScalarInputDiscovererTest extends AndroidTestCase {
    public void testStartScanning() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ScalarInputDiscoverer sid = s.buildDiscoverer();

        AccumulatingConsumer<ExternalSensorDiscoverer.DiscoveredSensor> c =
                new AccumulatingConsumer<>();
        assertEquals(true, sid.startScanning(c, TestConsumers.expectingSuccess(), getContext()));
        ExternalSensorSpec sensor = c.getOnlySeen().getSpec();
        ScalarInputSpec spec = (ScalarInputSpec) sensor;
        assertEquals(s.getSensorName(), spec.getName());
        assertEquals(s.getSensorAddress(), spec.getSensorAddressInService());
        assertEquals(s.getServiceId(), spec.getServiceId());
        assertEquals(ScalarInputSpec.TYPE, spec.getType());
    }

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
                }, null);

        AccumulatingConsumer<ExternalSensorDiscoverer.DiscoveredSensor> c =
                new AccumulatingConsumer<>();
        assertEquals(true, sid.startScanning(c, TestConsumers.expectingFailure(
                new FailureListener() {
                    @Override
                    public void fail(Exception e) {
                        assertTrue(e.getMessage().contains(errorText));
                    }
                }), getContext()));
        assertEquals(0, c.seen.size());
    }

}