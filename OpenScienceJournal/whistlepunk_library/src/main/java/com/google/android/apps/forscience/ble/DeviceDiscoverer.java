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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.util.ArrayMap;

import com.google.android.apps.forscience.whistlepunk.devicemanager.WhistlepunkBleDevice;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Discovers BLE devices and tracks when they come and go.
 */
public abstract class DeviceDiscoverer {
    public static boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Receives notification of devices being discovered or errors.
     */
    public static class Callback {
        public void onDeviceFound(DeviceRecord record) {}

        public void onError(int error) {
            // TODO: define error codes
        }
    }

    /**
     * Describes a Bluetooth device which was discovered.
     */
    public static class DeviceRecord {

        /**
         * Device that was found.
         */
        public WhistlepunkBleDevice device;

        /**
         * Last time this device was seen, in uptimeMillis.
         */
        public long lastSeenTimestampMs;

        /**
         * Last RSSI value seen.
         */
        public int lastRssi;
    }

    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final ArrayMap<String, DeviceRecord> mDevices;
    private Callback mCallback;

    public static DeviceDiscoverer getNewInstance(Context context) {
        DeviceDiscoverer discoverer;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            discoverer = new DeviceDiscovererV21(context);
        } else {
            discoverer = new DeviceDiscovererLegacy(context);
        }
        return discoverer;
    }

    protected DeviceDiscoverer(Context context) {
        mContext = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) mContext.getSystemService(
                Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
        mDevices = new ArrayMap<>();
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    public void startScanning(Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }

        mCallback = callback;
        // Clear out the older devices so we don't think they're still there.
        mDevices.clear();
        onStartScanning();
    }

    public abstract void onStartScanning();

    public void stopScanning() {
        onStopScanning();
        mCallback = null;
    }

    public abstract void onStopScanning();

    public boolean canScan() {
        return mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON;
    }

    protected void addOrUpdateDevice(WhistlepunkBleDevice device, int rssi) {
        DeviceRecord deviceRecord = mDevices.get(device.getAddress());
        boolean previouslyFound = deviceRecord != null;
        if (!previouslyFound) {
            deviceRecord = new DeviceRecord();
            deviceRecord.device = device;
            mDevices.put(device.getAddress(), deviceRecord);
        }
        // Update the last RSSI and last seen
        deviceRecord.lastRssi = rssi;
        deviceRecord.lastSeenTimestampMs = SystemClock.uptimeMillis();

        if (!previouslyFound && mCallback != null) {
            mCallback.onDeviceFound(deviceRecord);
        }
    }
}
