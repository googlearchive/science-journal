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

import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

/**
 * Discovers BLE sensors that speak our "native" Science Journal protocol.
 */
public class NativeBleDiscoverer implements ExternalSensorDiscoverer {

    private static final ExternalSensorProvider PROVIDER = new ExternalSensorProvider() {
        @Override
        public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec) {
            return new BluetoothSensor(sensorId, (BleSensorSpec) spec,
                    BluetoothSensor.ANNING_SERVICE_SPEC);
        }

        @Override
        public String getProviderId() {
            return SensorRegistry.WP_NATIVE_BLE_PROVIDER_ID;
        }

        @Override
        public ExternalSensorSpec buildSensorSpec(String name, byte[] config) {
            return new BleSensorSpec(name, config);
        }
    };

    private DeviceDiscoverer mDeviceDiscoverer;

    @Override
    public ExternalSensorProvider getProvider() {
        return PROVIDER;
    }

    @Override
    public boolean startScanning(final Consumer<ExternalSensorSpec> onEachSensorFound,
            FailureListener onScanError, Context context) {
        stopScanning();

        mDeviceDiscoverer = createDiscoverer(context);
        if (!mDeviceDiscoverer.canScan()) {
            stopScanning();
            return false;
        }
        mDeviceDiscoverer.startScanning(new DeviceDiscoverer.Callback() {
            @Override
            public void onDeviceFound(final DeviceDiscoverer.DeviceRecord record) {
                onDeviceRecordFound(record, onEachSensorFound);
            }

            @Override
            public void onError(int error) {
                // TODO: handle errors
            }
        });
        return true;
    }

    protected DeviceDiscoverer createDiscoverer(Context context) {
        return DeviceDiscoverer.getNewInstance(context);
    }

    @Override
    public void stopScanning() {
        if (mDeviceDiscoverer != null) {
            mDeviceDiscoverer.stopScanning();
            mDeviceDiscoverer = null;
        }
    }

    private void onDeviceRecordFound(DeviceDiscoverer.DeviceRecord record,
            Consumer<ExternalSensorSpec> onEachSensorFound) {
        WhistlepunkBleDevice device = record.device;
        String address = device.getAddress();

        // sensorScanCallbacks will handle duplicates
        onEachSensorFound.take(new BleSensorSpec(address, device.getName()));
    }
}
