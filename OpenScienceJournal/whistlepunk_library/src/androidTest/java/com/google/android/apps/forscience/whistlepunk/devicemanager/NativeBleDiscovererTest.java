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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.content.Context;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.whistlepunk.AccumulatingConsumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.RecordingRunnable;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

public class NativeBleDiscovererTest extends AndroidTestCase {
    // TODO: can we make this a JDK-only test?

    public void testConnectableSensor() {
        final String name = Arbitrary.string();
        final String address = Arbitrary.string();

        NativeBleDiscoverer discoverer = new NativeBleDiscoverer(getContext()) {
            @Override
            protected DeviceDiscoverer createDiscoverer(Context context) {
                return new DeviceDiscoverer(context) {
                    @Override
                    public void onStartScanning() {
                        int rssi = Arbitrary.integer();
                        String longName = Arbitrary.string();
                        addOrUpdateDevice(new WhistlepunkBleDevice() {
                            @Override
                            public String getName() {
                                return name;
                            }

                            @Override
                            public String getAddress() {
                                return address;
                            }
                        }, rssi, longName);
                    }

                    @Override
                    public boolean canScan() {
                        return true;
                    }

                    @Override
                    public void onStopScanning() {

                    }
                };
            }
        };

        final AccumulatingConsumer<ExternalSensorDiscoverer.DiscoveredSensor> sensorsSeen =
                new AccumulatingConsumer<>();
        final RecordingRunnable onScanDone = new RecordingRunnable();
        ExternalSensorDiscoverer.ScanListener listener =
                new ExternalSensorDiscoverer.ScanListener() {
                    @Override
                    public void onServiceFound(ExternalSensorDiscoverer.DiscoveredService service) {

                    }

                    @Override
                    public void onDeviceFound(ExternalSensorDiscoverer.DiscoveredDevice device) {

                    }

                    @Override
                    public void onSensorFound(ExternalSensorDiscoverer.DiscoveredSensor sensor) {
                        sensorsSeen.take(sensor);
                    }

                    @Override
                    public void onServiceScanComplete(String serviceId) {

                    }

                    @Override
                    public void onScanDone() {
                        onScanDone.run();
                    }
                };
        discoverer.startScanning(listener, TestConsumers.expectingSuccess());
        assertEquals(1, sensorsSeen.seen.size());
        ExternalSensorSpec sensor = sensorsSeen.seen.get(0).getSpec();
        assertEquals(name, sensor.getName());
        assertEquals(address, sensor.getAddress());
        assertEquals(BleSensorSpec.TYPE, sensor.getType());
        assertFalse(onScanDone.hasRun);
        discoverer.stopScanning();
        assertTrue(onScanDone.hasRun);
    }
}