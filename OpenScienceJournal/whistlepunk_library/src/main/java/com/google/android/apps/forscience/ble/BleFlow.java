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

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A BLE protocol builder for simplifying the interactions with a BLE remote device.
 * <p/>
 * Example of usage:
 * <p/>
 * Create a flow:
 * <code>
 *     flow = bleClient.createFlowFor(ADDRESS).addListener(listener)
 *                     .connect().lookupService(SERVICE_UUID);
 *     BleFlow.run(flow);
 * </code>
 * <p/>
 * In the above BleFlowListener, make sure you do something useful:
 * <code>
 *     listener = new BleFlowListener() {
 *
 *          @Override public void onSuccess(BleFlow flow) {
 *             printService(flow.getCurrentService());
 *          }
 * };
 * </code>
 */
public class BleFlow {
    private enum Action {SCAN, CONNECT, LOOKUP_SRV, LOOKUP_CHARACT, READ_CHARACT, WRITE_CHARACT,
        LOOKUP_DESC, WRITE_DESC, ENABLE_NOTIF, DISABLE_NOTIF, DISCONNECT, COMMIT,
        CHANGE_MTU, START_TX, WRITE_STREAM, PICK_FIRST_DEVICE }
    private static String TAG = "BleFlow";
    private static final boolean DEBUG = false;
    private static UUID BLE_CLIENT_CONFIG_CHARACTERISTIC = UUID.fromString(
            "00002902-0000-1000-8000-00805f9b34fb");
    private static final BleFlowListener defaultListener = new BleFlowListener() {
        @Override
        public void onSuccess() {}

        @Override
        public void onNotification(UUID characteristic, int flags, byte[] value) {}

        @Override
        public void onCharacteristicRead(UUID characteristic, int flags, byte[] value) {}

        @Override
        public void onFailure(Exception error) {}

        @Override
        public void onDisconnect() {}

        @Override
        public void onConnect() {}

        @Override
        public void onNotificationSubscribed() {}

        @Override
        public void onNotificationUnsubscribed() {}

        @Override
        public void onServicesDiscovered() {}
    };

    private final Context context;
    private final BleClient client;
    private final List<Action> actions;
    private final List<UUID> services;
    private final List<UUID> characteristics;
    private final List<UUID> descriptors;
    private final List<byte[]> values;
    private UUID[] scanServiceFilter;

    private BluetoothGattCharacteristic currentCharacteristic;
    private BluetoothGattService currentService;
    private BluetoothGattDescriptor currentDescriptor;

    private BleFlowListener listener;
    private int serviceIndex;
    private int characteristicIndex;
    private int valueIndex;
    private int actionIndex;
    private int descriptorIndex;
    private int timeout = -1;
    private String address;
    private AtomicBoolean flowEnded;
    private int mtu;
    private InputStream inputStream;
    private int maxNoDevices;

    private int currentBufferSize = 20;

    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleEvents.CHAR_CHANGED.equals(action)) {
                int flags = intent.getIntExtra(MyBleService.FLAGS, 0);
                byte[] data = intent.getByteArrayExtra(MyBleService.DATA);
                UUID characteristic = UUID.fromString(intent.getStringExtra(MyBleService.UUID));
                listener.onNotification(characteristic, flags, data);
                return;
            }
            if (flowEnded.get() && (BleEvents.GATT_CONNECT_FAIL.equals(action)
                    || BleEvents.GATT_DISCONNECT.equals(action))) {
                listener.onDisconnect();
                return;
            }
            if (flowEnded.get()) {
                return;
            }

            if (BleEvents.BLE_SCAN_END.equals(action)) {
                nextAction();
            } else if (BleEvents.BLE_DISABLED.equals(action)) {
                listener.onFailure(new Exception("BLE disabled"));
                flowEnded.set(true);
            } else if (BleEvents.GATT_CONNECT.equals(action)) {
                listener.onConnect();
                nextAction();
            } else if (BleEvents.GATT_CONNECT_FAIL.equals(action)) {
                listener.onFailure(new Exception("Cannot connect to " + address));
                flowEnded.set(true);
            } else if (BleEvents.GATT_DISCONNECT.equals(action)) {
                nextAction();
            } else if (BleEvents.GATT_DISCONNECT_FAIL.equals(action)) {
                listener.onFailure(new Exception("Could not disconnect from: "
                        + address));
                flowEnded.set(true);
            } else if (BleEvents.SERVICES_OK.equals(action)) {
                currentService = services.size() <= serviceIndex ? null
                        : client.getService(address, services.get(serviceIndex++));
                if (currentService == null) {
                    listener.onFailure(new Exception("Service lookup failure at: "
                            + address));
                    flowEnded.set(true);
                } else {
                    listener.onServicesDiscovered();
                    nextAction();
                }
            } else if (BleEvents.SERVICES_FAIL.equals(action)) {
                listener.onFailure(new Exception("Service lookup failure at: "
                        + address));
                flowEnded.set(true);
            } else if (BleEvents.START_TX_OK.equals(action)) {
                nextAction();
            } else if (BleEvents.START_TX_FAIL.equals(action)) {
                listener.onFailure(new Exception("Start TX characteristic fail for: "
                        + currentCharacteristic));
                flowEnded.set(true);
            } else if (BleEvents.READ_CHAR_OK.equals(action)) {
                UUID characteristic = UUID.fromString(intent.getStringExtra(MyBleService.UUID));
                int flags = intent.getIntExtra(MyBleService.FLAGS, 0);
                byte[] data = intent.getByteArrayExtra(MyBleService.DATA);
                listener.onCharacteristicRead(characteristic, flags, data);
                nextAction();
            } else if (BleEvents.WRITE_CHAR_OK.equals(action)) {
                nextAction();
            } else if (BleEvents.WRITE_CHAR_FAIL.equals(action)) {
                listener.onFailure(new Exception("Writing characteristic fail for: "
                        + currentCharacteristic));
                flowEnded.set(true);
            } else if (BleEvents.WRITE_DESC_OK.equals(action)) {
                nextAction();
            } else if (BleEvents.WRITE_DESC_FAIL.equals(action)) {
                listener.onFailure(new Exception("Write desriptor fail for: "
                        + currentDescriptor));
                flowEnded.set(true);
            } else if (BleEvents.COMMIT_OK.equals(action)) {
                nextAction();
            } else if (BleEvents.COMMIT_FAIL.equals(action)) {
                listener.onFailure(new Exception("Commit TX fail for: "
                        + currentDescriptor));
                flowEnded.set(true);
            } else if (BleEvents.MTU_CHANGE_OK.equals(action)) {
                currentBufferSize = mtu - 3;
                nextAction();
            } else if (BleEvents.MTU_CHANGE_FAIL.equals(action)) {
                listener.onFailure(new Exception("Mtu change failed."));
                flowEnded.set(true);
            } else {
                Log.e(TAG, "Event not mapped: " + action);
            }
        }
    };
    
    private BleFlow(BleClient client, Context context, String address) {
        this.client = client;
        this.context = context;
        actions = new ArrayList<>();
        services = new ArrayList<>();
        characteristics = new ArrayList<>();
        values = new ArrayList<>();
        descriptors = new ArrayList<>();
        this.address = address;
        listener = defaultListener;
        flowEnded = new AtomicBoolean();
        flowEnded.set(true);
        maxNoDevices = MyBleService.MAX_NO_DEVICES;

        LocalBroadcastManager.getInstance(context).registerReceiver(receiver,
                BleEvents.createIntentFilter(address));
    }

    private void nextAction() {
        if (flowEnded.get()) {
            return;
        }
        if (actionIndex == actions.size()) {
            if (DEBUG) Log.d(TAG, "no other action, success");
            flowEnded.set(true);
            listener.onSuccess();
            return;
        }
        Action action = actions.get(actionIndex++);
        if (DEBUG) Log.d(TAG, "current action: " + action);
        switch (action) {
            case SCAN:
                if (timeout > 0) {
                    client.setMaxNoDevices(maxNoDevices);
                    client.scanForDevices(scanServiceFilter, timeout);
                } else {
                    listener.onFailure(new Exception("no scanning time set"));
                    flowEnded.set(true);
                }
                break;
            case PICK_FIRST_DEVICE:
                address = client.getFirstDeviceAddress();
                if (address == null) {
                    listener.onFailure(new Exception("no device found"));
                    flowEnded.set(true);
                } else {
                    nextAction();
                }
                break;
            case CONNECT:
                if (!client.connectToAddress(address)) {
                    listener.onFailure(new Exception("cannot connect to: " + address));
                    flowEnded.set(true);
                }
                break;
            case DISCONNECT:
                client.disconnectDevice(address);
                break;
            case START_TX:
                client.startTransaction(address);
                break;
            case COMMIT:
                client.commit(address);
                break;
            case CHANGE_MTU:
                client.changeMtu(address, mtu);
                break;
            case LOOKUP_SRV:
                client.findServices(address);
                break;
            case LOOKUP_CHARACT:
                UUID charactId = characteristics.get(characteristicIndex++);
                currentCharacteristic = currentService != null ?
                        currentService.getCharacteristic(charactId) : null;
                if (currentCharacteristic == null) {
                    listener.onFailure(new Exception("No such charact.: " + charactId));
                    flowEnded.set(true);
                } else {
                    nextAction();
                }
                break;
            case READ_CHARACT:
                if (currentCharacteristic == null) {
                    listener.onFailure(new Exception("Missing charact."));
                    flowEnded.set(true);
                } else {
                    if (DEBUG) Log.d(TAG, "Reading on characteristic " + currentCharacteristic.getUuid());
                    client.readValue(address, currentCharacteristic);
                }
                break;
            case WRITE_CHARACT:
                if (currentCharacteristic == null) {
                    listener.onFailure(new Exception("Missing charact."));
                    flowEnded.set(true);
                } else {
                    if (DEBUG) Log.d(TAG, "Writing on characteristic " + currentCharacteristic.getUuid()
                            + " the value " + Arrays.toString(values.get(valueIndex)));
                    client.writeValue(address, currentCharacteristic, values.get(valueIndex++));
                }
                break;
            case WRITE_STREAM:
                writeStream();
                break;
            case LOOKUP_DESC:
                UUID descId = descriptors.get(descriptorIndex++);
                currentDescriptor = currentCharacteristic.getDescriptor(descId);
                if (currentDescriptor == null) {
                    listener.onFailure(new Exception("No such descriptor: " + descId));
                    flowEnded.set(true);
                } else {
                    nextAction();
                }
                break;
            case WRITE_DESC:
                if (currentDescriptor == null) {
                    listener.onFailure(new Exception("Missing descriptor."));
                    flowEnded.set(true);
                } else {
                    if (DEBUG) Log.d(TAG, "Writing on descriptor " + currentDescriptor.getUuid()
                            + " the value " + Arrays.toString(values.get(valueIndex)));
                    client.writeValue(address, currentDescriptor, values.get(valueIndex++));
                }
                break;
            case ENABLE_NOTIF:
                if (currentCharacteristic == null) {
                    listener.onFailure(new Exception("Failed to enable notifications "
                            + " due to missing characteristic."));
                    flowEnded.set(true);
                }
                if (!client.enableNotifications(address, currentCharacteristic)) {
                    listener.onFailure(new Exception(
                            "Failed to enable notifications on " + currentCharacteristic.getUuid()));
                    flowEnded.set(true);
                }
                nextAction();
                listener.onNotificationSubscribed();
                break;
            case DISABLE_NOTIF:
                if (currentCharacteristic == null) {
                    listener.onFailure(new Exception("Failed to disable notifications "
                            + " due to missing characteristic."));
                    flowEnded.set(true);
                }
                if (!client.disableNotifications(address, currentCharacteristic)) {
                    listener.onFailure(new Exception(
                            "Failed to disable notifications on " + currentCharacteristic.getUuid()));
                    flowEnded.set(true);
                }
                nextAction();
                listener.onNotificationUnsubscribed();
                break;
            default:
                break;
        }
    }

    public BleFlow reset() {

        actions.clear();
        services.clear();
        characteristics.clear();
        descriptors.clear();
        values.clear();
        serviceIndex = 0;
        characteristicIndex = 0;
        valueIndex = 0;
        actionIndex = 0;
        descriptorIndex = 0;
        currentCharacteristic = null;
        currentService = null;
        currentDescriptor = null;
        inputStream = null;
        timeout = -1;
        listener = defaultListener;
        maxNoDevices = MyBleService.MAX_NO_DEVICES;

        return this;
    }

    public BleFlow resetAndAddListener(BleFlowListener flowListener){
        return this.reset().addListener(flowListener);
    }
    
    public static BleFlow getInstance(BleClient client, Context context, String address) {
        return new BleFlow(client, context, address);
    }

    public BleFlow addListener(BleFlowListener listener) {
        this.listener = listener;
        return this;
    }

    public BleFlow scan(int timeout) {
        actions.add(Action.SCAN);
        scanServiceFilter = null;
        this.timeout = timeout;
        return this;
    }

    public BleFlow scan(int timeout, int maxNoDevices) {
        actions.add(Action.SCAN);
        this.maxNoDevices = maxNoDevices;
        scanServiceFilter = null;
        this.timeout = timeout;
        return this;
    }

    public BleFlow scanFor(UUID[] services, int timeout) {
        actions.add(Action.SCAN);
        scanServiceFilter = services;
        this.timeout = timeout;
        return this;
    }

    public BleFlow scanFor(UUID[] services, int timeout, int maxNoDevices) {
        actions.add(Action.SCAN);
        this.maxNoDevices = maxNoDevices;
        scanServiceFilter = services;
        this.timeout = timeout;
        return this;
    }

    public BleFlow setAddress(String address) {
        this.address = address;
        return this;
    }

    public BleFlow setAddressToFirstFoundDevice() {
        actions.add(Action.PICK_FIRST_DEVICE);
        return this;
    }

    public BleFlow connect() {
        actions.add(Action.CONNECT);
        return this;
    }

    public BleFlow lookupService(UUID serviceUuid) {
        actions.add(Action.LOOKUP_SRV);
        services.add(serviceUuid);
        return this;
    }

    public BleFlow lookupCharacteristic(UUID characteristicUuid) {
        actions.add(Action.LOOKUP_CHARACT);
        characteristics.add(characteristicUuid);
        return this;
    }

    public BleFlow lookupDescriptor(UUID descriptorUuid) {
        actions.add(Action.LOOKUP_DESC);
        descriptors.add(descriptorUuid);
        return this;
    }

    public BleFlow read() {
        actions.add(Action.READ_CHARACT);
        return this;
    }

    public BleFlow write(byte[] value) {
        actions.add(Action.WRITE_CHARACT);
        values.add(value);
        return this;
    }

    public BleFlow enableNotification() {
        actions.add(Action.ENABLE_NOTIF);

        lookupDescriptor(BLE_CLIENT_CONFIG_CHARACTERISTIC);

        actions.add(Action.WRITE_DESC);
        values.add(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return this;
    }

    public BleFlow disableNotification() {
        actions.add(Action.DISABLE_NOTIF);

        lookupDescriptor(BLE_CLIENT_CONFIG_CHARACTERISTIC);

        actions.add(Action.WRITE_DESC);
        values.add(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        return this;
    }

    public BleFlow beginTransaction() {
        actions.add(Action.START_TX);
        return this;
    }

    // TODO: If we need this, need to figure out how to make it work at API < 21
    //
    //    public BleFlow changeMessageTransferUnitSize(int mtu) {
    //        actions.add(Action.CHANGE_MTU);
    //        this.mtu = mtu;
    //        return this;
    //    }

    public BleFlow commit() {
        actions.add(Action.COMMIT);
        return this;
    }

    public BleFlow disconnect() {
        actions.add(Action.DISCONNECT);
        return this;
    }

    public static void run(BleFlow flow) {
        flow.run();
    }

    private void run() {
        Log.v(TAG, "executing actions: " + actions);
        Log.v(TAG, "services: " + services);
        Log.v(TAG, "characteristics: " + characteristics);
        Log.v(TAG, "descriptors: " + descriptors);
        Log.v(TAG, "values: " + Arrays.toString(values.toArray()));

        if(flowEnded.get()) {
            flowEnded.set(false);
            nextAction();
        }

    }

    private BluetoothGattService getCurrentService() {
        return currentService;
    }

    public boolean isCharacteristicValid (UUID characteristic) {
        return currentService != null && currentService.getCharacteristic(characteristic) != null;
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID serviceId, UUID charractId) {
        BluetoothGattService service = client.getService(address, serviceId);
        if (service == null) {
            return null;
        }
        return service.getCharacteristic(charractId);
    }

    void close() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
    }

    public BleFlow writeInputStream(InputStream stream) {
        actions.add(Action.WRITE_STREAM);
        this.inputStream = stream;
        return this;
    }

    private void writeStream() {
        byte[] buffer = new byte[currentBufferSize];
        try {
            int read = inputStream.read(buffer);
            if (read <= 0) {
                nextAction();
                return;
            }

            // we execute the same action again
            actionIndex--;

            if (read == buffer.length) {
                client.writeValue(address, currentCharacteristic, buffer);
                return;
            }

            byte[] smallerBuffer = new byte[read];
            System.arraycopy(buffer, 0, smallerBuffer, 0, read);
            client.writeValue(address, currentCharacteristic, smallerBuffer);
        } catch (IOException e) {
            listener.onFailure(new Exception("Failed to read input stream."));
            flowEnded.set(true);
        }
    }

    public String getAddress() {
        return address;
    }
}
