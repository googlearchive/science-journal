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

class TestDevicesPresenter implements DevicesPresenter {
  public String experimentId;
  public String sensorId;
  private MemorySensorGroup availableDevices;
  private SensorGroup pairedDevices;

  public TestDevicesPresenter(MemorySensorGroup availableDevices, MemorySensorGroup pairedDevices) {
    this.availableDevices = availableDevices;
    this.pairedDevices = pairedDevices;
  }

  @Override
  public void refreshScanningUI() {}

  @Override
  public void showSensorOptions(
      String experimentId, String sensorId, SensorDiscoverer.SettingsInterface settings) {
    this.experimentId = experimentId;
    this.sensorId = sensorId;
  }

  @Override
  public SensorGroup getPairedSensorGroup() {
    return pairedDevices;
  }

  @Override
  public SensorGroup getAvailableSensorGroup() {
    return availableDevices;
  }

  @Override
  public void unpair(String experimentId, String sensorId) {}

  @Override
  public boolean isDestroyed() {
    return false;
  }

  public void setPairedDevices(SensorGroup pairedDevices) {
    this.pairedDevices = pairedDevices;
  }
}
