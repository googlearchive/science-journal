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

import android.os.RemoteException;

public class TestSensor {
  private final String sensorAddress;
  private final String sensorName;
  private final SensorAppearanceResources appearance;

  public TestSensor(String sensorAddress, String sensorName, SensorAppearanceResources appearance) {
    this.sensorAddress = sensorAddress;
    this.sensorName = sensorName;
    this.appearance = appearance;
  }

  public void deliverTo(ISensorConsumer c) {
    try {
      c.onSensorFound(sensorAddress, sensorName, new SensorBehavior(), appearance);
    } catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public String getSensorAddress() {
    return sensorAddress;
  }
}
