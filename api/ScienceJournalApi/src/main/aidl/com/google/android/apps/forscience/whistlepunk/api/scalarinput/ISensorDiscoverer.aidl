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

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConnector;

// Top-level interface for a service that exposes sensors for Science Journal.
interface ISensorDiscoverer {
    // Human-readable name of the service
    String getName() = 0;

    // Call c once for each device the service can detect
    void scanDevices(IDeviceConsumer c) = 1;

    // Call c once for each sensor on the device indicated by deviceId
    void scanSensors(String deviceId, ISensorConsumer c) = 2;

    // Return a new connector.  This connector will be used to connect to, and disconnect from,
    // exactly one sensor.
    ISensorConnector getConnector() = 3;
}
