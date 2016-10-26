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

import android.app.PendingIntent;
import android.content.Context;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

/**
 * One way of discovering additional sensors that can be added to an experiment
 */
public interface ExternalSensorDiscoverer {
    public interface DiscoveredSensor {
        ExternalSensorSpec getSpec();
        PendingIntent getSettingsIntent();
    }

    public interface ScanListener {
        /**
         * Called when a scan finds a new device
         */
        void onDeviceFound(InputDeviceSpec device);

        /**
         * Called when a scan finds a new sensor on a device.
         */
        void onSensorFound(DiscoveredSensor sensor);

        /**
         * Called when all devices and sensors are found.
         */
        void onScanDone();
    }

    /**
     * @return true if starting scanning was successful
     */
    boolean startScanning(ScanListener listener, FailureListener onScanError);

    /**
     * Stops scanning, and discards any state or references acquired during scanning
     */
    void stopScanning();

    /**
     * @return the provider that can be used to generate a SensorChoice from the stored spec.
     */
    ExternalSensorProvider getProvider();
}
