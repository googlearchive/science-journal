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

import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.SensorHistoryStorage;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.audio.AudioSource;
import io.reactivex.Single;

/** Encapsulates services that sensors need to do their jobs */
public interface SensorEnvironment {
  RecordingDataController getDataController(AppAccount appAccount);

  /** @return a Single that publishes once there is a connected BLE client */
  Single<BleClient> getConnectedBleClient();

  /**
   * @return report values using this clock, _unless_ the sensor has a better built-in timestamp to
   *     use (for example, given skew, it's better to believe the timestamps in the BLE packets than
   *     the clock value)
   */
  Clock getDefaultClock();

  /** @return the common audio source that can be used by multiple sensors simultaneously. */
  AudioSource getAudioSource();

  SensorHistoryStorage getSensorHistoryStorage();
}
