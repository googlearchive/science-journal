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
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.ArraySet;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service dealing with the BLE gory details.
 */
public class MyBleService extends Service {
    private static String TAG = "MyBleService";
    private static final int SERVICES_RETRY_COUNT = 3;
    private static final boolean DEBUG = false;
    private static final long PRUNE_DEVICE_TIMEOUT_MS = 10 * 1000;
    private static final int MSG_PRUNE = 1011;
    static int MAX_NO_DEVICES = 100;

    static LocalBroadcastManager getBroadcastManager(Context context) {
        // For security, only use local broadcasts (See b/32803250)
        return LocalBroadcastManager.getInstance(context);
    }

    /**
     * The local binder for this service.
     */
    class LocalBinder extends Binder {
        public MyBleService getService() {
            return MyBleService.this;
        }
    }

    public static String DATA = "data";
    public static String UUID = "uuid";
    public static String FLAGS = "flags";
    public static String INT_PARAM = "int_param";

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter btAdapter;
    private Boolean isScanning = new Boolean(false);
    private int maxNoDevices = MAX_NO_DEVICES;

    // the list of discovered devices
    private BleDevices bleDevices;

    private Map<String, BluetoothGatt> addressToGattClient =
            Collections.synchronizedMap(new LinkedHashMap<String, BluetoothGatt>());

    private Handler handler;

    private Set<String> mOutstandingServiceDiscoveryAddresses = new ArraySet<>();

    // BLE callback
    BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (bleDevices.addDevice(device, rssi, scanRecord)) {
                if (DEBUG)
                    Log.d(TAG, "New device: " + device.getAddress() + ", name: " + device.getName());

                if (bleDevices.getCount() >= maxNoDevices) {
                    Log.d(TAG, "Max no of devices reached, exiting the scan.");
                    endScan();
                }
            }
            pruneAfterDelay();
        }
    };

    // GATT callbacks
    private BluetoothGattCallback gattCallbacks = new BluetoothGattCallback() {
        Map<String, Integer> mConnectionStatuses = new ArrayMap<>();

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String address = getAddressFromGatt(gatt);
            if (DEBUG) Log.d(TAG, "CONNECTION CHANGED FOR " + address + " : " + newState);

            // On ChromeOS (and maybe other platforms?), onConnectionStateChange can be called
            // multiple times without any actual corresponding state change.  This confuses our
            // (very brittle) downstream code, so filter the duplicates out here.  See
            // b/31741822 for further discussion.
            boolean isActualChange = !(mConnectionStatuses.containsKey(address)
                                       && mConnectionStatuses.get(address) == newState);
            mConnectionStatuses.put(address, newState);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                sendGattBroadcast(address, BleEvents.GATT_CONNECT_FAIL, null);
                addressToGattClient.remove(address);
                gatt.close();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // TODO: extract testable code here
                if (isActualChange) {
                    sendGattBroadcast(address, BleEvents.GATT_CONNECT, null);
                }
                return;
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendGattBroadcast(address, BleEvents.GATT_DISCONNECT, null);
                addressToGattClient.remove(address);
                gatt.close();
                return;
            }
            Log.e(TAG, "Gatt - unexpected connection state: " + newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            String address = getAddressFromGatt(gatt);
            mOutstandingServiceDiscoveryAddresses.remove(address);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (DEBUG) Log.d(TAG, "Sending the action: " + BleEvents.SERVICES_OK);
                sendServiceDiscoveryIntent(MyBleService.this, address, SERVICES_RETRY_COUNT);
            } else {
                sendGattBroadcast(address, BleEvents.SERVICES_FAIL, null);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (DEBUG) Log.d(TAG, "Got notification from " + characteristic.getUuid());
            sendGattBroadcast(getAddressFromGatt(gatt), BleEvents.CHAR_CHANGED,
                    characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            // Characteristic value will be stored in the intent which will be extract from
            // the broadcast message (see sendGattBroadcast and BleFlow.BroadcastReceiver).
            if (DEBUG) {
                Log.d(TAG, "Characteristic read result: "
                        + characteristic.getUuid() + " - " + (status == BluetoothGatt.GATT_SUCCESS));
                Log.d(TAG, "Characteristic value: " + characteristic.getStringValue(0).toString());
            }

            sendGattBroadcast(getAddressFromGatt(gatt), status == BluetoothGatt.GATT_SUCCESS
                    ? BleEvents.READ_CHAR_OK : BleEvents.READ_CHAR_FAIL, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if (DEBUG) Log.d(TAG, "Characteristic write result: "
                    + characteristic.getUuid() + " - " + (status == BluetoothGatt.GATT_SUCCESS));
            sendGattBroadcast(getAddressFromGatt(gatt),
                    status == BluetoothGatt.GATT_SUCCESS
                            ? BleEvents.WRITE_CHAR_OK : BleEvents.WRITE_CHAR_FAIL, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                                     BluetoothGattDescriptor descriptor, int status) {
            sendGattBroadcast(getAddressFromGatt(gatt), status == BluetoothGatt.GATT_SUCCESS
                    ? BleEvents.READ_DESC_OK : BleEvents.READ_DESC_FAIL, null);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            sendGattBroadcast(getAddressFromGatt(gatt), status == BluetoothGatt.GATT_SUCCESS
                    ? BleEvents.WRITE_DESC_OK : BleEvents.WRITE_DESC_FAIL, null);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            sendGattBroadcast(getAddressFromGatt(gatt), status == BluetoothGatt.GATT_SUCCESS
                    ? BleEvents.COMMIT_OK : BleEvents.COMMIT_FAIL, null);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            sendGattBroadcast(getAddressFromGatt(gatt), status == BluetoothGatt.GATT_SUCCESS
                    ? BleEvents.MTU_CHANGE_OK : BleEvents.MTU_CHANGE_OK, null);
        }
    };

    public static void sendServiceDiscoveryIntent(Context context, String address,
            int retriesLeft) {
        Intent newIntent = BleEvents.createIntent(BleEvents.SERVICES_OK, address);
        newIntent.putExtra(INT_PARAM, retriesLeft);
        getBroadcastManager(context).sendBroadcast(newIntent);
    }

    @VisibleForTesting
    protected String getAddressFromGatt(BluetoothGatt gatt) {
        return gatt.getDevice().getAddress();
    }

    private final IBinder binder = new LocalBinder();

    @VisibleForTesting
    protected void sendGattBroadcast(String address, String gattAction,
            BluetoothGattCharacteristic characteristic) {
        if (DEBUG) Log.d(TAG, "Sending the action: " + gattAction);
        Intent newIntent = BleEvents.createIntent(gattAction, address);
        if (characteristic != null) {
            newIntent.putExtra(UUID, characteristic.getUuid().toString());
            newIntent.putExtra(FLAGS, characteristic.getProperties());
            newIntent.putExtra(DATA, characteristic.getValue());
        }
        getBroadcastManager(this).sendBroadcast(newIntent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bleDevices = new BleDevices();
        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (MSG_PRUNE == msg.what && bleDevices != null) {
                    Log.v(TAG, "Pruning devices");
                    BluetoothManager manager = (BluetoothManager) getSystemService(
                            Context.BLUETOOTH_SERVICE);
                    bleDevices.pruneOldDevices(manager.getConnectedDevices(BluetoothProfile.GATT));
                }
                return false;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Intent newIntent = new Intent(BleEvents.BLE_UNSUPPORTED);
            getBroadcastManager(this).sendBroadcast(newIntent);
            return START_NOT_STICKY;
        }

        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        }
        if (btAdapter == null) {
            btAdapter = bluetoothManager.getAdapter();
        }
        if (!checkBleEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // Intent newIntent = new Intent(BleEvents.BLE_ENABLED);
        // LocalBroadcastManager.getInstance(this).sendBroadcast(newIntent);

        return START_STICKY;
    }

    public boolean checkBleEnabled() {
        if (!isBleEnabled()) {
            Intent newIntent = new Intent(BleEvents.BLE_DISABLED);
            getBroadcastManager(this).sendBroadcast(newIntent);
            if (DEBUG) Log.d(TAG, "sent intent BLE_DISABLED");
            return false;
        }

        return true;
    }

    private boolean isBleEnabled() {
        return btAdapter != null && btAdapter.isEnabled();
    }

    public boolean connect(String address) {
        if (btAdapter == null) {
            // Not sure how we could get here, but it happens (b/36738130), so flag an error
            // instead of crashing.
            return false;
        }
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        //  Explicitly check if Ble is enabled, otherwise it attempts a connection
        //  that never timesout even though it should.
        if (device == null || !isBleEnabled()) {
            return false;
        }
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        int connectionState = bluetoothManager.getConnectionState(device,
                BluetoothProfile.GATT);
        if (bluetoothGatt != null && connectionState != BluetoothProfile.STATE_CONNECTED) {
            return bluetoothGatt.connect();
        }
        if (bluetoothGatt != null && connectionState == BluetoothProfile.STATE_CONNECTED) {
            sendGattBroadcast(address, BleEvents.GATT_CONNECT, null);
            return true;
        }

        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
        bluetoothGatt = device.connectGatt(this, false,  // autoConnect = false
                gattCallbacks);
        addressToGattClient.put(address, bluetoothGatt);
        return true;
    }

    public void disconnectDevice(String address) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (btAdapter == null || address == null || bluetoothGatt == null) {
            // Broadcast the disconnect so BleFlow doesn't hang waiting for it; something else
            // already disconnected us in this case.
            sendGattBroadcast(address, BleEvents.GATT_DISCONNECT, null);
            return;
        }
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        int bleState = bluetoothManager.getConnectionState(device,
                BluetoothProfile.GATT);
        if (bleState != BluetoothProfile.STATE_DISCONNECTED
                && bleState != BluetoothProfile.STATE_DISCONNECTING) {
            bluetoothGatt.disconnect();
        } else {
            bluetoothGatt.close();
            addressToGattClient.remove(address);
            sendGattBroadcast(address, BleEvents.GATT_DISCONNECT, null);
        }
    }

    void resetGatt() {
        if (btAdapter != null && isScanning) {
            btAdapter.stopLeScan(scanCallback);
        }
        for (BluetoothGatt bluetoothGatt : addressToGattClient.values()) {
            bluetoothGatt.close();
        }
    }

    @Override
    public void onDestroy() {
        resetGatt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public BleDevices getBleDevices() {
        return bleDevices;
    }

    public boolean discoverServices(String address) {
        if (mOutstandingServiceDiscoveryAddresses.contains(address)) {
            return addressToGattClient.containsKey(address);
        }
        mOutstandingServiceDiscoveryAddresses.add(address);
        return internalDiscoverServices(address);
    }

    @VisibleForTesting
    protected boolean internalDiscoverServices(String address) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        return bluetoothGatt != null && bluetoothGatt.discoverServices();
    }

    public void scanFor(UUID[] serviceType) {
        synchronized (isScanning) {
            if (isScanning) {
                return;
            }
            btAdapter.startLeScan(serviceType, scanCallback);
            isScanning = true;
        }
        setLastScanTime();
    }

    public void scanAll() {
        synchronized (isScanning) {
            if (isScanning) {
                return;
            }
            isScanning = true;
            btAdapter.startLeScan(scanCallback);
        }
        setLastScanTime();
    }

    public void endScan() {
        synchronized (isScanning) {
            maxNoDevices = MAX_NO_DEVICES;
            if (!isScanning) {
                return;
            }
            isScanning = false;
            btAdapter.stopLeScan(scanCallback);
            getBroadcastManager(this).sendBroadcast(new Intent(BleEvents.BLE_SCAN_END));
        }
    }

    BluetoothGattService getService(String address, UUID serviceId) {
        if (DEBUG) Log.d(TAG, "lookup for service: " + serviceId);
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        return bluetoothGatt == null ? null : bluetoothGatt.getService(serviceId);
    }

    /**
     * FOR DEBUGGING ONLY.  This should never be called from production code; we don't want this
     * data in our logs.
     */
    public void printServices(String address) {
        if (!DEBUG) {
            return;
        }
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (bluetoothGatt == null) {
            Log.d(TAG, "No connection found for: " + address);
            return;
        }
        for (BluetoothGattService service : bluetoothGatt.getServices()) {
            Log.d(TAG, "Service ================================");
            Log.d(TAG, "Service UUID: " + service.getUuid());
            Log.d(TAG, "Service Type: " + service.getType());

            for (BluetoothGattCharacteristic charact : service.getCharacteristics()) {
                Log.d(TAG, "Charact UUID: " + charact.getUuid());
                Log.d(TAG, "Charact prop: " + charact.getProperties());

                if (charact.getValue() != null) {
                    Log.d(TAG, "Charact Value: " + new String(charact.getValue()));
                }
            }
        }
    }

    void readValue(String address, BluetoothGattCharacteristic theCharacteristic) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (bluetoothGatt == null) {
            Log.w(TAG, "No connection found for: " + address);
            sendGattBroadcast(address, BleEvents.READ_CHAR_FAIL, null);
            return;
        }
        bluetoothGatt.readCharacteristic(theCharacteristic);
    }

    void writeValue(String address, BluetoothGattCharacteristic theCharacteristic, byte[] value) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (bluetoothGatt == null) {
            Log.w(TAG, "No connection found for: " + address);
            sendGattBroadcast(address, BleEvents.WRITE_CHAR_FAIL, null);
            return;
        }
        theCharacteristic.setValue(value);
        bluetoothGatt.writeCharacteristic(theCharacteristic);
    }

    void commit(String address) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (bluetoothGatt == null) {
            Log.w(TAG, "No connection found for: " + address);
            sendGattBroadcast(address, BleEvents.COMMIT_FAIL, null);
            return;
        }
        bluetoothGatt.executeReliableWrite();
    }

    public void writeValue(String address, BluetoothGattDescriptor descriptor, byte[] value) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (bluetoothGatt == null) {
            Log.w(TAG, "No connection found for: " + address);
            sendGattBroadcast(address, BleEvents.WRITE_DESC_FAIL, null);
            return;
        }

        if (!descriptor.setValue(value) || !bluetoothGatt.writeDescriptor(descriptor)) {
            sendGattBroadcast(address, BleEvents.WRITE_DESC_FAIL, descriptor.getCharacteristic());
        }
    }

    @TargetApi(21)
    public void setMtu(String address, int mtu) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (bluetoothGatt == null) {
            Log.w(TAG, "No connection found for: " + address);
            sendGattBroadcast(address, BleEvents.MTU_CHANGE_FAIL, null);
            return;
        }
        bluetoothGatt.requestMtu(mtu);
    }

    boolean setNotificationsFor(String address, BluetoothGattCharacteristic characteristic,
                                boolean enable) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (bluetoothGatt == null) {
            Log.w(TAG, "No connection found for: " + address);
            return false;
        }

        return bluetoothGatt.setCharacteristicNotification(characteristic, enable);
    }

    public void startTransaction(String address) {
        BluetoothGatt bluetoothGatt = addressToGattClient.get(address);
        if (bluetoothGatt == null) {
            Log.w(TAG, "No connection found for: " + address);
            return;
        }
        sendGattBroadcast(
                address,
                (bluetoothGatt.beginReliableWrite() ? BleEvents.START_TX_OK
                        : BleEvents.START_TX_FAIL),
                null);
    }

    public void setMaxNoDevices(int maxNoDevices) {
        this.maxNoDevices = maxNoDevices;
    }

    private void setLastScanTime() {
        bleDevices.setLastScanTime();
    }

    private void cancelPrune() {
        handler.removeMessages(MSG_PRUNE);
    }

    private void pruneAfterDelay() {
        cancelPrune();

        handler.sendEmptyMessageDelayed(MSG_PRUNE, PRUNE_DEVICE_TIMEOUT_MS);
    }
}
