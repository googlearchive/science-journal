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
import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.sensors.BleServiceSpec;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Discovers devices using pre API level 21 methods.
 */
/* package */ class DeviceDiscovererLegacy extends DeviceDiscoverer {
    private final Executor mUiThreadExecutor;

    private BluetoothAdapter.LeScanCallback mCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            if (isScienceSensor(parseUuids(scanRecord))) {
                mUiThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        addOrUpdateDevice(new NativeDevice(device), rssi);
                    }
                });
            }
        }
    };

    DeviceDiscovererLegacy(Context context) {
        super(context);
        mUiThreadExecutor = AppSingleton.getUiThreadExecutor();
    }

    @Override
    public void onStartScanning() {
        // KitKat can't handle 128bit UUIDs, so ask for all devices.
        getBluetoothAdapter().startLeScan(mCallback);
    }

    @Override
    public void onStopScanning() {
        getBluetoothAdapter().stopLeScan(mCallback);
    }

    private List<UUID> parseUuids(byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        ByteBuffer buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 2) {
            byte length = buffer.get();
            if (length == 0) break;

            byte type = buffer.get();
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (length >= 2) {
                        uuids.add(UUID.fromString(String.format(
                                "%08x-0000-1000-8000-00805f9b34fb", buffer.getShort())));
                        length -= 2;
                    }
                    break;

                case 0x06: // Partial list of 128-bit UUIDs
                case 0x07: // Complete list of 128-bit UUIDs
                    while (length >= 16) {
                        long lsb = buffer.getLong();
                        long msb = buffer.getLong();
                        uuids.add(new UUID(msb, lsb));
                        length -= 16;
                    }
                    break;

                default:
                    buffer.position(buffer.position() + length - 1);
                    break;
            }
        }

        return uuids;
    }

    private boolean isScienceSensor(List<UUID> ids) {
        for (BleServiceSpec spec : BluetoothSensor.SUPPORTED_SERVICES) {
            for (UUID loopId : ids) {
                if (loopId.compareTo(spec.getServiceId()) == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
