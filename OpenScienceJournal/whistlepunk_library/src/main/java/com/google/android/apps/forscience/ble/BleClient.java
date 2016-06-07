package com.google.android.apps.forscience.ble;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

public interface BleClient {
    void scanForDevices(UUID[] serviceType, int timeoutSeconds);

    boolean connectToAddress(String address);

    void findServices(String address);

    BluetoothGattService getService(String address, UUID serviceId);

    void readValue(String address, BluetoothGattCharacteristic theCharacteristic);

    void writeValue(String address, BluetoothGattCharacteristic theCharacteristic, byte[] value);

    BleFlow getFlowFor(String address);

    BleFlow createFlowFor(String address);

    void disconnectDevice(String address);

    void commit(String address);

    void writeValue(String address, BluetoothGattDescriptor currentDescriptor, byte[] value);

    boolean enableNotifications(String address, BluetoothGattCharacteristic characteristic);

    boolean disableNotifications(String address, BluetoothGattCharacteristic characteristic);

    void changeMtu(String address, int mtu);

    void startTransaction(String address);

    String getFirstDeviceAddress();

    void setMaxNoDevices(int maxNoDevices);
}
