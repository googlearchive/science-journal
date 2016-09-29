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

import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class TestSensorDiscoverer extends ISensorDiscoverer.Stub {
    private final Executor mExecutor;
    private String mServiceName;
    private List<Device> mDevices = new ArrayList<>();
    private Multimap<String, TestSensor> mSensors = HashMultimap.create();

    public TestSensorDiscoverer(String serviceName) {
        this(serviceName, MoreExecutors.directExecutor());
    }

    public TestSensorDiscoverer(String serviceName, Executor executor) {
        mServiceName = serviceName;
        mExecutor = executor;
    }

    @NonNull
    public ScalarInputDiscoverer makeScalarInputDiscoverer(
            final String serviceId) {
        return new ScalarInputDiscoverer(
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(serviceId, TestSensorDiscoverer.this);
                        adc.onDiscoveryDone();
                    }
                },
                new TestStringSource(),
                MoreExecutors.directExecutor());
    }

    @Override
    public String getName() throws RemoteException {
        return mServiceName;
    }

    public void addDevice(String deviceId, String name) {
        mDevices.add(new Device(deviceId, name));
    }

    @Override
    public void scanDevices(final IDeviceConsumer c) throws RemoteException {
        for (final Device device : mDevices) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    device.deliverTo(c);
                }
            });
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        c.onScanDone();
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    public void addSensor(String deviceId, String sensorAddress, String sensorName) {
        mSensors.put(deviceId, new TestSensor(sensorAddress, sensorName));
    }

    @Override
    public void scanSensors(String deviceId, final ISensorConsumer c) throws RemoteException {
        for (final TestSensor sensor : mSensors.get(deviceId)) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    sensor.deliverTo(c);
                }
            });
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        c.onScanDone();
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    @Override
    public ISensorConnector getConnector() throws RemoteException {
        return null;
    }

    @NonNull
    public Map<String, ExternalSensorDiscoverer> makeDiscovererMap(String serviceId) {
        ScalarInputDiscoverer sid = makeScalarInputDiscoverer(serviceId);
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();
        discoverers.put(ScalarInputSpec.TYPE, sid);
        return discoverers;
    }

    private class Device {
        private final String mDeviceId;
        private final String mName;

        public Device(String deviceId, String name) {
            mDeviceId = deviceId;
            mName = name;
        }

        public void deliverTo(IDeviceConsumer c) {
            try {
                c.onDeviceFound(mDeviceId, mName, null);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class TestSensor {
        private final String mSensorAddress;
        private final String mSensorName;

        public TestSensor(String sensorAddress, String sensorName) {
            mSensorAddress = sensorAddress;
            mSensorName = sensorName;
        }

        public void deliverTo(ISensorConsumer c) {
            try {
                c.onSensorFound(mSensorAddress, mSensorName, new SensorBehavior(),
                        new SensorAppearanceResources());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
