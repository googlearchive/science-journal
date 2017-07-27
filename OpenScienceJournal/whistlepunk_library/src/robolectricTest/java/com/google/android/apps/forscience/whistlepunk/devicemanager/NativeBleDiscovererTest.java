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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.whistlepunk.AccumulatingConsumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.RecordingRunnable;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class NativeBleDiscovererTest {
    @Test
    public void testConnectableSensor() {
        final String name = Arbitrary.string();
        final String address = Arbitrary.string();

        NativeBleDiscoverer discoverer = new NativeBleDiscoverer(
                RuntimeEnvironment.application.getApplicationContext()) {
            @Override
            protected DeviceDiscoverer createDiscoverer(Context context) {
                return new DeviceDiscoverer(context) {
                    @Override
                    public void onStartScanning() {
                        int rssi = Arbitrary.integer();
                        addOrUpdateDevice(new WhistlepunkBleDevice() {
                            @Override
                            public String getName() {
                                return name;
                            }

                            @Override
                            public String getAddress() {
                                return address;
                            }
                        }, rssi);
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

            @Override
            protected boolean hasScanPermission() {
                return true;
            }
        };

        final AccumulatingConsumer<SensorDiscoverer.DiscoveredSensor> sensorsSeen =
                new AccumulatingConsumer<>();
        final RecordingRunnable onScanDone = new RecordingRunnable();
        SensorDiscoverer.ScanListener listener =
                new SensorDiscoverer.ScanListener() {
                    @Override
                    public void onServiceFound(SensorDiscoverer.DiscoveredService service) {

                    }

                    @Override
                    public void onDeviceFound(SensorDiscoverer.DiscoveredDevice device) {

                    }

                    @Override
                    public void onSensorFound(SensorDiscoverer.DiscoveredSensor sensor) {
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
        GoosciSensorSpec.SensorSpec sensor = sensorsSeen.seen.get(0).getSensorSpec();
        assertEquals(name, sensor.rememberedAppearance.name);
        assertEquals(address, sensor.info.address);
        assertEquals(BleSensorSpec.TYPE, sensor.info.providerId);
        assertFalse(onScanDone.hasRun);
        discoverer.stopScanning();
        assertTrue(onScanDone.hasRun);
    }
}