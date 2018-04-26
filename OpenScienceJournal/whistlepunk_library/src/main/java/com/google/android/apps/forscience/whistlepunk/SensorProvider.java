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
package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;

/**
 * Builds observable SensorChoice implementations from configurations loaded from the database.
 *
 * <p>Has different implementations depending on whether the sensor is a built-in sensor, a
 * bluetooth- connected arduino, or discovered via the scalar API.
 */
public interface SensorProvider {
  ExternalSensorSpec buildSensorSpec(String name, byte[] config);

  public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec);
}
