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

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates a scenario in which scanning is guaranteed to find a single device and a single sensor.
 */
public class ScalarInputScenario {
    private String mServiceName;
    private String mDeviceId;
    private String mDeviceName;
    private String mSensorAddress;
    private String mSensorName;
    private String mServiceId;

    public ScalarInputScenario() {
        mServiceName = Arbitrary.string();
        mDeviceId = Arbitrary.string();
        mDeviceName = Arbitrary.string();
        mSensorAddress = Arbitrary.string();
        mSensorName = Arbitrary.string();
        mServiceId = Arbitrary.string();
    }

    public String getServiceName() {
        return mServiceName;
    }

    public String getDeviceId() {
        return mDeviceId;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public String getSensorAddress() {
        return mSensorAddress;
    }

    public String getSensorName() {
        return mSensorName;
    }

    public String getServiceId() {
        return mServiceId;
    }

    @NonNull
    public ScalarInputDiscoverer buildDiscoverer() {
        final TestSensorDiscoverer discoverer = new TestSensorDiscoverer(getServiceName());
        discoverer.addDevice(getDeviceId(), getDeviceName());
        discoverer.addSensor(getDeviceId(), getSensorAddress(), getSensorName());
        return new ScalarInputDiscoverer(
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(getServiceId(), discoverer);
                        adc.onDiscoveryDone();
                    }
                },
                new TestStringSource(),
                MoreExecutors.directExecutor());
    }

    @NonNull
    public Map<String, ExternalSensorDiscoverer> makeScalarInputDiscoverers() {
        ScalarInputDiscoverer sid = buildDiscoverer();
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();
        discoverers.put(ScalarInputSpec.TYPE, sid);
        return discoverers;
    }

    @NonNull
    public ScalarInputSpec makeSpec() {
        return new ScalarInputSpec(getSensorName(), getServiceId(), getSensorAddress(), null, null);
    }
}
