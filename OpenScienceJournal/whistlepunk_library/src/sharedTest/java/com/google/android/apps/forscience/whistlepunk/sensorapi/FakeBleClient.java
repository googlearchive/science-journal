package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.ble.BleFlow;

import java.util.UUID;

public class FakeBleClient implements BleClient {
    public String expectedAddress = null;
    public String mostRecentAddress = null;

    private Context mContext;

    public FakeBleClient(Context context) {
        this.mContext = context;
    }

    @Override
    public BleFlow getFlowFor(String address) {
        return createFlowFor(address);
    }

    @Override
    public BleFlow createFlowFor(String address) {
        return BleFlow.getInstance(this, mContext, address);
    }

    @Override
    public void scanForDevices(UUID[] serviceType, int timeoutSeconds) {

    }

    @Override
    public boolean connectToAddress(String address) {
        mostRecentAddress = address;
        return address.equals(expectedAddress);
    }

    @Override
    public void findServices(String address) {

    }

    @Override
    public BluetoothGattService getService(String address, UUID serviceId) {
        return null;
    }

    @Override
    public void readValue(String address, BluetoothGattCharacteristic theCharacteristic) {

    }

    @Override
    public void writeValue(String address, BluetoothGattCharacteristic theCharacteristic,
            byte[] value) {

    }

    @Override
    public void disconnectDevice(String address) {

    }

    @Override
    public void commit(String address) {

    }

    @Override
    public void writeValue(String address, BluetoothGattDescriptor currentDescriptor,
            byte[] value) {

    }

    @Override
    public boolean enableNotifications(String address,
            BluetoothGattCharacteristic characteristic) {
        return false;
    }

    @Override
    public boolean disableNotifications(String address,
            BluetoothGattCharacteristic characteristic) {
        return false;
    }

    @Override
    public void changeMtu(String address, int mtu) {

    }

    @Override
    public void startTransaction(String address) {

    }

    @Override
    public String getFirstDeviceAddress() {
        return null;
    }

    @Override
    public void setMaxNoDevices(int maxNoDevices) {

    }
}
