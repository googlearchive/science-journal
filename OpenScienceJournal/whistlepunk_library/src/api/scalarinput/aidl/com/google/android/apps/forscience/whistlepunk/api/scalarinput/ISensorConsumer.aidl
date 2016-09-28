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
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorAppearanceResources;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorBehavior;

interface ISensorConsumer {
  // A new sensor has been discovered (implementor should correctly handle being notified of the
  // same sensor twice)
  //
  // sensorAddress: this is a service-dependent string that uniquely identifies this sensor so
  //                that it can be found again later.
  // name: human-readable name of the sensor.  Service should internationalize if possible.
  // behavior: Controls logging, display of sensor options, etc.
  // appearance: Controls how the sensor will appear once connected
  void onSensorFound(String sensorAddress, String name, in SensorBehavior behavior,
                     in SensorAppearanceResources appearance) = 0;
  void onScanDone() = 1;
}
