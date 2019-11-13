/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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
package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import androidx.collection.ArrayMap;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance.BasicSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import java.util.Map;

class ManualSensorRegistry extends SensorRegistry {
  private Map<String, String> sensorNames = new ArrayMap<>();

  public ManualSensor addSensor(String id, String name) {
    ManualSensor sensor = new ManualSensor(id, 100, 100);
    addBuiltInSensor(sensor);
    sensorNames.put(id, name);
    return sensor;
  }

  @Override
  public GoosciSensorSpec.SensorSpec getSpecForId(
      String sensorId, SensorAppearanceProvider appearanceProvider, Context context) {
    GoosciSensorSpec.SensorSpec result = super.getSpecForId(sensorId, appearanceProvider, context);
    BasicSensorAppearance newRememberedAppearance =
        result.getRememberedAppearance().toBuilder().setName(sensorNames.get(sensorId)).build();
    return result.toBuilder().setRememberedAppearance(newRememberedAppearance).build();
  }
}
