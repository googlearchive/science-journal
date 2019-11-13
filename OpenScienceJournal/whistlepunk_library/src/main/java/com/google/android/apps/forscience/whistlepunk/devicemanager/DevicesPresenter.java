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

/** interface for presenting options for a given sensor within a given experiment */
public interface DevicesPresenter {
  void refreshScanningUI();

  void showSensorOptions(
      String experimentId, String sensorId, SensorDiscoverer.SettingsInterface settings);

  /** @return a display that shows "My Devices" */
  SensorGroup getPairedSensorGroup();

  /** @return a display that shows discovered devices that aren't in "My Devices" */
  SensorGroup getAvailableSensorGroup();

  void unpair(String experimentId, String sensorId);

  /** @return true if this presenter is destroyed and no longer useful for updates */
  boolean isDestroyed();
}
