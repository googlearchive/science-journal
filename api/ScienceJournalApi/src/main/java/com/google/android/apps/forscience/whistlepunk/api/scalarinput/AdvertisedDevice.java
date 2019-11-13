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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.app.PendingIntent;
import java.util.List;

/** A device advertised through the API */
public abstract class AdvertisedDevice {
  private final String deviceId;
  private final String deviceName;

  protected AdvertisedDevice(String deviceId, String deviceName) {
    this.deviceId = deviceId;
    this.deviceName = deviceName;
  }

  /** @return a pending intent to change settings on the device. */
  protected PendingIntent getSettingsIntent() {
    return null;
  }

  public abstract List<? extends AdvertisedSensor> getSensors();

  String getDeviceId() {
    return deviceId;
  }

  String getDeviceName() {
    return deviceName;
  }
}
