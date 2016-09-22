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

interface ISensorConsumer {
  // A new sensor has been discovered (implementor should correctly handle being notified of the
  // same sensor twice)
  //
  // sensorAddress: this is a service-dependent string that uniquely identifies this sensor so
  //                that it can be found again later.
  // name: human-readable name of the sensor.  Service should internationalize if possible.
  // loggingId: an id that will be used to log usage and problem reports.  All reports will already
  //            contain your service's package name.  The loggingId might contain additional
  //            information about the _type_ of sensor being used ("thermometer"), but should not
  //            contain anything that would allow individual users to be identified (no user names
  //            or physical or virtual addresses of any kind)
  // appearance: Controls how the sensor will appear once connected
  // settingsIntent: a PendingIntent that the Science Journal app can send to open an activity
  //                 that will allow the user to change settings for this particular sensor.
  //                 Be sure that different sensors issue different settingsIntents (simply changing
  //                 the extras is not enough, see the javadoc for PendingIntent.)
  void onSensorFound(String sensorAddress, String name, String loggingId,
                     in SensorAppearanceResources appearance, in PendingIntent settingsIntent) = 0;
}
