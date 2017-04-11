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

package com.google.android.apps.forscience.ble;

import android.annotation.TargetApi;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import com.google.android.apps.forscience.whistlepunk.devicemanager.WhistlepunkBleDevice;
import com.google.android.apps.forscience.whistlepunk.sensors.BleServiceSpec;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers LE devices using API level 21+ methods.
 */
@TargetApi(21)
/* package */ class DeviceDiscovererV21 extends DeviceDiscoverer {

    private BluetoothLeScanner mScanner;

    private ScanCallback mCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addOrUpdateDevice(getDevice(result), result.getRssi());
        }

        private WhistlepunkBleDevice getDevice(ScanResult result) {
            return new NativeDevice(result.getDevice());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addOrUpdateDevice(getDevice(result), result.getRssi());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            // TODO: surface errors.
        }
    };

    DeviceDiscovererV21(Context context) {
        super(context);
    }

    @Override
    public void onStartScanning() {
        mScanner = getBluetoothAdapter().getBluetoothLeScanner();
        List<ScanFilter> filters = new ArrayList<>();
        for (BleServiceSpec spec : BluetoothSensor.SUPPORTED_SERVICES) {
            filters.add(new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(spec.getServiceId().toString()))
                    .build());
        }
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        mScanner.startScan(filters, settings, mCallback);
    }

    @Override
    public void onStopScanning() {
        if (mScanner != null) {
            if (isBluetoothEnabled()) {
                mScanner.stopScan(mCallback);
            }
        }
    }
}
