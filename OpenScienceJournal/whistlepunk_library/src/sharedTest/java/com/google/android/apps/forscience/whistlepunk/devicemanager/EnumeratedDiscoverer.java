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
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

public class EnumeratedDiscoverer extends StubSensorDiscoverer {
    private final ExternalSensorSpec[] mSpecs;

    public EnumeratedDiscoverer(ExternalSensorSpec... specs) {
        mSpecs = specs;
    }

    @Override
    public boolean startScanning(
            Consumer<ExternalSensorDiscoverer.DiscoveredSensor> onEachSensorFound,
            Runnable onScanDone, FailureListener onScanError) {
        for (ExternalSensorSpec spec : mSpecs) {
            onEachSensorFound.take(getDiscovered(spec));
        }
        return true;
    }

    @NonNull
    private ExternalSensorDiscoverer.DiscoveredSensor getDiscovered(final ExternalSensorSpec spec) {
        return new ExternalSensorDiscoverer.DiscoveredSensor() {
            @Override
            public ExternalSensorSpec getSpec() {
                return spec;
            }

            @Override
            public PendingIntent getSettingsIntent() {
                return null;
            }
        };
    }
}
