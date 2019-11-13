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
import io.reactivex.Single;
import java.util.UUID;

public interface BleClient {
  boolean connectToAddress(String address);

  void findServices(String address);

  BluetoothGattService getService(String address, UUID serviceId);

  void readValue(String address, BluetoothGattCharacteristic theCharacteristic);

  void writeValue(String address, BluetoothGattCharacteristic theCharacteristic, byte[] value);

  BleFlow getFlowFor(String address);

  BleFlow createFlowFor(String address);

  void disconnectDevice(String address);

  void writeValue(String address, BluetoothGattDescriptor currentDescriptor, byte[] value);

  boolean enableNotifications(String address, BluetoothGattCharacteristic characteristic);

  boolean disableNotifications(String address, BluetoothGattCharacteristic characteristic);

  Single<BleClient> whenConnected();
}
