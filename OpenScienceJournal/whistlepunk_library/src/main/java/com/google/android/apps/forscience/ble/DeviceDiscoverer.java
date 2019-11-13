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
import android.os.ParcelUuid;
import android.os.SystemClock;
import androidx.collection.ArrayMap;
import com.google.android.apps.forscience.whistlepunk.devicemanager.WhistlepunkBleDevice;

/** Discovers BLE devices and tracks when they come and go. */
public abstract class DeviceDiscoverer {
  public static boolean isBluetoothEnabled() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    return adapter != null && adapter.isEnabled();
  }

  /** Receives notification of devices being discovered or errors. */
  public static class Callback {
    public void onDeviceFound(DeviceRecord record) {}

    public void onError(int error) {
      // TODO: define error codes
    }
  }

  /** Describes a Bluetooth device which was discovered. */
  public static class DeviceRecord {

    /** Device that was found. */
    public WhistlepunkBleDevice device;

    /** Last time this device was seen, in uptimeMillis. */
    public long lastSeenTimestampMs;

    /** Last RSSI value seen. */
    public int lastRssi;
  }

  private final BluetoothAdapter bluetoothAdapter;
  private final ArrayMap<String, DeviceRecord> devices;
  private Callback callback;

  public static DeviceDiscoverer getNewInstance(Context context) {
    return new DeviceDiscovererV21(context);
  }

  protected DeviceDiscoverer(Context context) {
    BluetoothManager manager =
        (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    bluetoothAdapter = manager == null ? null : manager.getAdapter();
    devices = new ArrayMap<>();
  }

  BluetoothAdapter getBluetoothAdapter() {
    return bluetoothAdapter;
  }

  public void startScanning(ParcelUuid[] serviceUuids, Callback callback) {
    if (callback == null) {
      throw new IllegalArgumentException("Callback must not be null");
    }

    this.callback = callback;
    // Clear out the older devices so we don't think they're still there.
    devices.clear();
    onStartScanning(serviceUuids);
  }

  public abstract void onStartScanning(ParcelUuid[] serviceUuids);

  public void stopScanning() {
    onStopScanning();
    callback = null;
  }

  public abstract void onStopScanning();

  public boolean canScan() {
    // It may only be possible for bluetoothAdapter to be null in emulators
    return bluetoothAdapter != null && bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON;
  }

  protected void addOrUpdateDevice(WhistlepunkBleDevice device, int rssi) {
    DeviceRecord deviceRecord = devices.get(device.getAddress());
    boolean previouslyFound = deviceRecord != null;
    if (!previouslyFound) {
      deviceRecord = new DeviceRecord();
      deviceRecord.device = device;
      devices.put(device.getAddress(), deviceRecord);
    }
    // Update the last RSSI and last seen
    deviceRecord.lastRssi = rssi;
    deviceRecord.lastSeenTimestampMs = SystemClock.uptimeMillis();

    if (!previouslyFound && callback != null) {
      callback.onDeviceFound(deviceRecord);
    }
  }
}
