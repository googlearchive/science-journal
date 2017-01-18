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

import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.common.collect.Lists;

import java.util.List;

public class RecordingScanListener implements ExternalSensorDiscoverer.ScanListener {
    public List<ExternalSensorDiscoverer.DiscoveredService> services = Lists.newArrayList();
    public List<ExternalSensorDiscoverer.DiscoveredDevice> devices = Lists.newArrayList();
    public List<ExternalSensorDiscoverer.DiscoveredSensor> sensors = Lists.newArrayList();
    public boolean isDone = false;

    @Override
    public void onServiceFound(ExternalSensorDiscoverer.DiscoveredService service) {
        services.add(service);
    }

    @Override
    public void onDeviceFound(ExternalSensorDiscoverer.DiscoveredDevice device) {
        devices.add(device);
    }

    @Override
    public void onSensorFound(ExternalSensorDiscoverer.DiscoveredSensor sensor) {
        sensors.add(sensor);
    }

    @Override
    public void onServiceScanComplete(String serviceId) {

    }

    @Override
    public void onScanDone() {
        isDone = true;
    }
}
