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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;

class TestScanListener extends StubScanListener {
  private final Consumer<SensorDiscoverer.DiscoveredSensor> onNewSensor;
  private final Runnable onScanDone;

  public TestScanListener(Consumer<SensorDiscoverer.DiscoveredSensor> c, Runnable onScanDone) {
    onNewSensor = c;
    this.onScanDone = onScanDone;
  }

  @Override
  public void onSensorFound(SensorDiscoverer.DiscoveredSensor sensor) {
    onNewSensor.take(sensor);
  }

  @Override
  public void onScanDone() {
    onScanDone.run();
  }
}
