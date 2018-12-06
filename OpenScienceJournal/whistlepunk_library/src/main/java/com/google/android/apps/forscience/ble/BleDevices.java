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

import android.bluetooth.BluetoothDevice;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The list of discovered BLE devices, indexed by name and address.
 */
/* package */ class BleDevices {

    /**
     * Amount of time after the last scan time when a device may have been seen.
     */
    private static final long PRUNE_SCAN_TIME_SLOP_MS = 5000;

    static class BluetoothDeviceRecord {

        BluetoothDevice device;
        int lastRssi;
        byte[] scanRecord;

        // Last time the device was seen, in uptimeMillis.
        long lastSeenMs;

        void updateParams(int lastRssi) {
            this.lastRssi = lastRssi;
            this.lastSeenMs = SystemClock.uptimeMillis();
        }
    }

    HashMap<String, BluetoothDeviceRecord> devices = new LinkedHashMap<>();
    // last scan time, in uptimeMillis.
    private long lastScanTimeMs = -1;

    public boolean addDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
        synchronized (devices) {
            String key = device.getAddress();

            if (!devices.containsKey(key)) {
                BluetoothDeviceRecord record = new BluetoothDeviceRecord();
                record.device = device;
                record.scanRecord = scanRecord;
                record.updateParams(rssi);
                devices.put(key, record);
                return true;
            } else {
                // update the last known rssi and seen time.
                devices.get(key).updateParams(rssi);
            }
            return false;
        }
    }

    public void removeDevice(BluetoothDevice device) {
        synchronized (devices) {
            String key = device.getAddress();
            devices.remove(key);
        }
    }

    public int getSize() {
        return devices.size();
    }

    public String[] getDeviceNames() {
        return devices.keySet().toArray(new String[0]);
    }

    public BluetoothDevice getDevice(String name) {
        return devices.get(name).device;
    }

    public List<BluetoothDevice> getDevices() {
        List<BluetoothDevice> returnList = new ArrayList<BluetoothDevice>(devices.size());
        for (BluetoothDeviceRecord record : devices.values()) {
            returnList.add(record.device);
        }
        return returnList;
    }

    public boolean hasDevice(String currentAddress) {
        for (BluetoothDeviceRecord record : devices.values()) {
            if (currentAddress.equalsIgnoreCase(record.device.getAddress())) {
                return true;
            }
        }
        return false;
    }

    public int getCount() {
        return devices.size();
    }


    /**
     * Removes devices which haven't been seen since the last scan time and are not currently
     * connected.
     *
     * @param connectedDevices List of currently connected devices.
     */
    void pruneOldDevices(List<BluetoothDevice> connectedDevices) {
        ArrayList<BluetoothDevice> oldDevices = new ArrayList<BluetoothDevice>();
        for (BluetoothDeviceRecord record : devices.values()) {
            if (record.lastSeenMs < (lastScanTimeMs + PRUNE_SCAN_TIME_SLOP_MS)
                    && !isContained(record.device.getAddress(), connectedDevices)) {
                oldDevices.add(record.device);
            }
        }
        for (BluetoothDevice device : oldDevices) {
            removeDevice(device);
        }
    }

    private boolean isContained(String address, List<BluetoothDevice> connectedDevices) {
        for (BluetoothDevice device : connectedDevices) {
            if (device.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the last scan time.
     */
    void setLastScanTime() {
        lastScanTimeMs = SystemClock.uptimeMillis();
    }
}
