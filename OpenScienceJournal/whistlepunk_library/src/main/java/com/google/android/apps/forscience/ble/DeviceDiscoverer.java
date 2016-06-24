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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.util.ArrayMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Discovers BLE devices and tracks when they come and go.
 */
public abstract class DeviceDiscoverer {

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
        public BluetoothDevice device;

        /**
         * Last time this device was seen, in uptimeMillis.
         */
        public long lastSeenTimestampMs;

        /**
         * Last RSSI value seen.
         */
        public int lastRssi;

        /**
         * Long version of the device name.
         */
        private String mLongName;

        @Override
        public String toString() {
            // If long name is available show the long name instead.
            if (!mLongName.isEmpty()) {
                return mLongName;
            } else {
                return device.getName();
            }
        }
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

    public List<DeviceRecord> getDevices() {
        return new ArrayList<>(mDevices.values());
    }

    public boolean canScan() {
        return mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON;
    }

    protected Context getContext() {
        return mContext;
    }

    protected void addOrUpdateDevice(BluetoothDevice device, int rssi, String longName) {
        DeviceRecord deviceRecord = mDevices.get(device.getAddress());
        boolean previouslyFound = deviceRecord != null;
        if (!previouslyFound) {
            deviceRecord = new DeviceRecord();
            deviceRecord.device = device;
            mDevices.put(device.getAddress(), deviceRecord);
            deviceRecord.mLongName = longName;
        }
        // Update the last RSSI and last seen
        deviceRecord.lastRssi = rssi;
        deviceRecord.lastSeenTimestampMs = SystemClock.uptimeMillis();

        if (!previouslyFound && mCallback != null) {
            mCallback.onDeviceFound(deviceRecord);
        }
    }

    // Extract from raw scan record and combine with the shortname to make the long name.
    // Offset 0 to 31 = Advertisement
    // Offset 32 to 61 = Scan Response
    // In the Scan Response:
    //   - Offset 0 to 6 = Short name
    //   - Offset 7 = length of the long name
    //   - Offset 8 = AD type
    //   - Offset 9 = LSB of UUID
    //   - Offset 10 = MSB of UUID
    //   - Offset 11 to 30 = Long name
    // Scan Response = Short name + other data + long name
    // String to return = "Longname (shortname)"
    // The hardware side use standard ASCII code for name.
    // See Bluetooth Core Specification (www.bluetooth.org)
    protected String extractLongName(byte[] scanRecord) {
        String longname = "";
        int size = scanRecord.length;
        if (size == 62) {
            byte[] scanResponse = Arrays.copyOfRange(scanRecord, 32, 61);
            if (scanResponse[0] != 0) {
                byte[] shortname = Arrays.copyOfRange(scanResponse, 0, 7);
                int length = scanResponse[7];
//                int adType = scanResponse[8];
//                int uuidLsb = scanResponse[9];
//                int uuidMsb = scanResponse[10];
                byte[] scandata = Arrays.copyOfRange(scanResponse, 11, length+11);
                longname = new String(scandata, StandardCharsets.US_ASCII)
                    + " ("
                    + new String(shortname, StandardCharsets.US_ASCII)
                    + ")";
            }
        }

        return longname;
    }
}
