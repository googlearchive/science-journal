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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.content.Context;
import android.graphics.drawable.Drawable;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeviceWithSensors {
  private final InputDeviceSpec device;
  private final List<String> sensorKeys = new ArrayList<>();

  public DeviceWithSensors(InputDeviceSpec device) {
    this.device = device;
  }

  public void addSensorKey(String sensorKey) {
    if (!sensorKeys.contains(sensorKey)) {
      sensorKeys.add(sensorKey);
    }
  }

  public String getName() {
    return device.getName();
  }

  public boolean isSameSensor(InputDeviceSpec spec) {
    return device.isSameSensor(spec);
  }

  void addToRegistry(ConnectableSensorRegistry registry, SensorRegistry sensorRegistry) {
    registry.addMyDevice(device, sensorRegistry, Lists.<String>newArrayList(sensorKeys));
  }

  public Drawable getIconDrawable(
      Context context,
      SensorAppearanceProvider appearanceProvider,
      Map<String, ConnectableSensor> sensorMap) {
    Drawable selfDrawable = device.getSensorAppearance().getIconDrawable(context);
    if (selfDrawable != null) {
      return selfDrawable;
    }
    return getArbitraryDeviceIcon(context, appearanceProvider, sensorMap);
  }

  /**
   * If the device does not have an icon, choose an arbitrary icon from the sensors on the device to
   * represent the device (It will be fairly rare that a device both has no individual icon, and
   * multiple sensors with different icons, so this seems a reasonable heuristic.)
   */
  private Drawable getArbitraryDeviceIcon(
      Context context,
      SensorAppearanceProvider appearanceProvider,
      Map<String, ConnectableSensor> sensorMap) {
    for (String sensorKey : sensorKeys) {
      ConnectableSensor sensor = sensorMap.get(sensorKey);
      Drawable sensorDrawable = sensor.getAppearance(appearanceProvider).getIconDrawable(context);
      if (sensorDrawable != null) {
        return sensorDrawable;
      }
    }
    return null;
  }

  public List<String> getSensorKeys() {
    return sensorKeys;
  }

  public InputDeviceSpec getSpec() {
    return device;
  }
}
