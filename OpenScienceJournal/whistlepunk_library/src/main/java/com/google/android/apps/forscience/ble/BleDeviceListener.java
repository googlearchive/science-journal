package com.google.android.apps.forscience.ble;

import android.bluetooth.BluetoothDevice;

/**
 * An object listening for devices being added and removed.
 */
public interface BleDeviceListener {
    void onDeviceAdded(BluetoothDevice device);
    void onDeviceRemoved(BluetoothDevice device);
}
