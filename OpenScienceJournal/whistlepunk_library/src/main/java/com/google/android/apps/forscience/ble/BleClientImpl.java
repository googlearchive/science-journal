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
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;

/**
 * The BLE client, a thin wrapper around the MyBleService.
 */
public class BleClientImpl implements BleClient {
    private static String TAG = "BLEClient";
    private static final boolean DEBUG = false;

    private BehaviorSubject<Optional<MyBleService>> mWhenService = BehaviorSubject.create();
    private MyBleService bleService;
    private Handler handler = new Handler();

    private final Context context;
    private final List<BleFlow> flows;

    // service state changes
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bleService = ((MyBleService.LocalBinder) service).getService();
            mWhenService.onNext(Optional.of(bleService));
            if (DEBUG)  Log.d(TAG, "bleService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bleService = null;
            mWhenService.onNext(Optional.absent());
            if (DEBUG) Log.d(TAG, "bleService disconnected");
        }
    };

    public BleClientImpl(Context context) {
        this.context = context;
        flows = new ArrayList<>();
    }

    public final boolean create() {
        if (DEBUG) Log.d(TAG, "starting client...");

        Intent bindIntent = new Intent(context, MyBleService.class);
        context.startService(bindIntent);
        return context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void onCreate(boolean isBleSupported, boolean isBleEnabled) {
        // TODO: create boolean fields.
        if (DEBUG) {
            Log.d(TAG, "BLE support: " + (isBleSupported ? "YES" : "NO"));
            Log.d(TAG, "BLE enabled: " + (isBleEnabled ? "YES" : "NO"));
        }
    }

    public final void destroy() {
        for (BleFlow flow : flows) {
            flow.close();
        }
        if (bleService != null) {
            context.unbindService(serviceConnection);
        }
        if (DEBUG) Log.d(TAG, "client stopped");
    }

    @Override
    public final void scanForDevices(UUID[] serviceType, int timeoutSeconds) {
        if (bleService == null) {
            context.sendBroadcast(new Intent(BleEvents.BLE_SCAN_END));
            return;
        }
        if (serviceType == null) {
            bleService.scanAll();
        } else {
            bleService.scanFor(serviceType);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (bleService != null) {
                    bleService.endScan();
                }
            }
        }, 1000 * timeoutSeconds);
        if (DEBUG) Log.d(TAG, "scanning for devices...");
    }

    public final List<BluetoothDevice> getDevices() {
        if (bleService == null) {
            return new ArrayList<BluetoothDevice>();
        }
        return bleService.getBleDevices().getDevices();
    }

    @Override
    public final boolean connectToAddress(String address) {
        if (DEBUG) Log.d(TAG, "connecting to address: " + address + "...");
        try {
            return bleService.connect(address);
        } catch (java.lang.IllegalArgumentException ex) {
            Log.e(TAG, "failure connecting to address " + address + " due to: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public final void findServices(String address) {
        if (DEBUG) Log.d(TAG, "scanning for services on " + address + "...");
        bleService.discoverServices(address);
    }

    @Override
    public BluetoothGattService getService(String address, UUID serviceId) {
        return bleService.getService(address, serviceId);
    }

    @Override
    public void readValue(String address, BluetoothGattCharacteristic theCharacteristic) {
        bleService.readValue(address, theCharacteristic);
    }

    @Override
    public void writeValue(String address, BluetoothGattCharacteristic theCharacteristic,
            byte[] value) {
        bleService.writeValue(address, theCharacteristic, value);
    }

    @Override
    public BleFlow getFlowFor(String address) {
        for (BleFlow flow : flows) {
            // Should only have 1 flow per device
            if (flow.getAddress().equals(address)) {
                return flow;
            }
        }
        // If there isn't a valid flow, return a new one.
        return createFlowFor(address);
    }

    @Override
    public BleFlow createFlowFor(String address) {
        BleFlow flow = BleFlow.getInstance(this, context, address);
        flows.add(flow);
        return flow;
    }

    @Override
    public void disconnectDevice(String address) {
        bleService.disconnectDevice(address);
    }

    @Override
    public void commit(String address) {
        bleService.commit(address);
    }

    @Override
    public void writeValue(String address, BluetoothGattDescriptor currentDescriptor, byte[]
            value) {
        bleService.writeValue(address, currentDescriptor, value);
    }

    @Override
    public boolean enableNotifications(String address, BluetoothGattCharacteristic characteristic) {
        return bleService.setNotificationsFor(address, characteristic, true);
    }

    @Override
    public boolean disableNotifications(String address, BluetoothGattCharacteristic
            characteristic) {
        return bleService.setNotificationsFor(address, characteristic, false);
    }

    @Override
    public void changeMtu(String address, int mtu) {
        bleService.setMtu(address, mtu);
    }

    @Override
    public void startTransaction(String address) {
        bleService.startTransaction(address);
    }

    @Override
    public String getFirstDeviceAddress() {
        if (bleService == null) {
            return null;
        }
        BleDevices bleDevices = bleService.getBleDevices();
        if (bleDevices.devices.isEmpty()) {
            return null;
        }
        return bleDevices.devices.keySet().iterator().next();
    }

    @Override
    public void setMaxNoDevices(int maxNoDevices) {
        bleService.setMaxNoDevices(maxNoDevices <= 0 ? 1 : maxNoDevices);
    }

    @Override
    public Single<BleClient> whenConnected() {
        return mWhenService.filter(Optional::isPresent)
                           .firstElement()
                           .map(o -> (BleClient) this)
                           .toSingle();
    }
}
