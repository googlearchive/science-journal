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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.ble.BleFlow;
import io.reactivex.Single;
import java.util.UUID;

public class FakeBleClient implements BleClient {
  public String expectedAddress = null;
  public String mostRecentAddress = null;

  private Context context;

  public FakeBleClient(Context context) {
    this.context = context;
  }

  @Override
  public BleFlow getFlowFor(String address) {
    return createFlowFor(address);
  }

  @Override
  public BleFlow createFlowFor(String address) {
    return BleFlow.getInstance(this, context, address);
  }

  @Override
  public boolean connectToAddress(String address) {
    mostRecentAddress = address;
    return address.equals(expectedAddress);
  }

  @Override
  public void findServices(String address) {}

  @Override
  public BluetoothGattService getService(String address, UUID serviceId) {
    return null;
  }

  @Override
  public void readValue(String address, BluetoothGattCharacteristic theCharacteristic) {}

  @Override
  public void writeValue(
      String address, BluetoothGattCharacteristic theCharacteristic, byte[] value) {}

  @Override
  public void disconnectDevice(String address) {}

  @Override
  public void writeValue(String address, BluetoothGattDescriptor currentDescriptor, byte[] value) {}

  @Override
  public boolean enableNotifications(String address, BluetoothGattCharacteristic characteristic) {
    return false;
  }

  @Override
  public boolean disableNotifications(String address, BluetoothGattCharacteristic characteristic) {
    return false;
  }

  @Override
  public Single<BleClient> whenConnected() {
    return Single.just(this);
  }
}
