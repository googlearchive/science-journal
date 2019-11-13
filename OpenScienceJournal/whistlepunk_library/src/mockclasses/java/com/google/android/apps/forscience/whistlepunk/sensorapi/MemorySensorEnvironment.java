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
import com.google.android.apps.forscience.whistlepunk.MemorySensorHistoryStorage;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.SensorHistoryStorage;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.audio.AudioSource;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import io.reactivex.Single;

public class MemorySensorEnvironment implements SensorEnvironment {
  private final RecordingDataController dataController;
  private final Clock clock;
  private final AudioSource audioSource = new AudioSource();
  private FakeBleClient bleClient;
  private SensorHistoryStorage historyStorage;

  public MemorySensorEnvironment(
      RecordingDataController dataController,
      FakeBleClient bleClient,
      SensorHistoryStorage shs,
      Clock clock) {
    this.dataController =
        dataController != null
            ? dataController
            : new InMemorySensorDatabase().makeSimpleRecordingController();
    this.bleClient = bleClient;
    historyStorage = shs != null ? shs : new MemorySensorHistoryStorage();
    this.clock = clock;
  }

  @Override
  public RecordingDataController getDataController(AppAccount appAccount) {
    return dataController;
  }

  @Override
  public Single<BleClient> getConnectedBleClient() {
    return Single.just(bleClient);
  }

  @Override
  public Clock getDefaultClock() {
    return clock;
  }

  @Override
  public AudioSource getAudioSource() {
    return audioSource;
  }

  @Override
  public SensorHistoryStorage getSensorHistoryStorage() {
    return historyStorage;
  }
}
