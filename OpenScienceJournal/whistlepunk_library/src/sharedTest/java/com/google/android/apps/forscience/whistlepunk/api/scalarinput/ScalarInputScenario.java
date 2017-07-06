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

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Creates a scenario in which scanning is guaranteed to find a single device and a single sensor.
 */
public class ScalarInputScenario {
    public final SensorAppearanceResources appearance = new SensorAppearanceResources();
    private String mServiceName;
    private String mDeviceId;
    private String mDeviceName;
    private String mSensorAddress;
    private String mSensorName;
    private String mServiceId;

    public ScalarInputScenario() {
        mServiceName = Arbitrary.string("serviceName");
        mDeviceId = Arbitrary.string("deviceId");
        mDeviceName = Arbitrary.string("deviceName");
        mSensorAddress = Arbitrary.string("sensorAddress");
        mSensorName = Arbitrary.string("sensorName");
        mServiceId = Arbitrary.string("serviceId");
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
    public ScalarInputDiscoverer buildDiscoverer(Executor uiThread) {
        final TestSensorDiscoverer discoverer = makeTestSensorDiscoverer();
        return discoverer.makeScalarInputDiscoverer(getServiceId(), uiThread);
    }

    @NonNull
    private TestSensorDiscoverer makeTestSensorDiscoverer() {
        final TestSensorDiscoverer discoverer = new TestSensorDiscoverer(getServiceName());
        discoverer.addDevice(getDeviceId(), getDeviceName());
        discoverer.addSensor(getDeviceId(),
                new TestSensor(getSensorAddress(), getSensorName(), appearance));
        return discoverer;
    }

    @NonNull
    public Map<String, SensorDiscoverer> makeScalarInputDiscoverers() {
        return makeTestSensorDiscoverer().makeDiscovererMap(getServiceId());
    }

    @NonNull
    public ScalarInputSpec makeSpec() {
        return new ScalarInputSpec(getSensorName(), getServiceId(), getSensorAddress(),
                new SensorBehavior(), appearance, mDeviceId);
    }

    public Map<String, SensorProvider> makeScalarInputProviders() {
        return makeTestSensorDiscoverer().makeProviderMap(getServiceId());
    }

    @NonNull
    TestSensorDiscoverer neverDoneDiscoverer() {
        return new TestSensorDiscoverer(getServiceName()) {
            @Override
            protected void onDevicesDone(IDeviceConsumer c) {
                // override with empty implementation: we never call c.onScanDone, to test timeout
            }

            @Override
            protected void onSensorsDone(ISensorConsumer c) {
                // override with empty implementation: we never call c.onScanDone, to test timeout
            }
        };
    }

    public ConnectableSensor.Connector makeConnector() {
        return new ConnectableSensor.Connector(makeScalarInputProviders());
    }
}
