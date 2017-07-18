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

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;

import java.util.concurrent.Executor;

/**
 * Generates ScalarInputSensors from ScalarInputSpecs
 */
class ScalarInputProvider implements SensorProvider {
    private final Scheduler mScheduler;
    private Consumer<AppDiscoveryCallbacks> mServiceFinder;
    private ScalarInputStringSource mStringSource;
    private Executor mUiThreadExecutor;

    public ScalarInputProvider(Consumer<AppDiscoveryCallbacks> serviceFinder,
            ScalarInputStringSource stringSource, Executor uiThreadExecutor, Scheduler scheduler) {
        this.mServiceFinder = serviceFinder;
        this.mStringSource = stringSource;
        this.mUiThreadExecutor = uiThreadExecutor;
        mScheduler = scheduler;
    }

    @Override
    public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec) {
        ScalarInputSpec sis = (ScalarInputSpec) spec;
        return new ScalarInputSensor(sensorId, mUiThreadExecutor, mServiceFinder, mStringSource,
                sis, mScheduler);
    }

    @Override
    public ExternalSensorSpec buildSensorSpec(String name, byte[] config) {
        return new ScalarInputSpec(name, config);
    }
}
