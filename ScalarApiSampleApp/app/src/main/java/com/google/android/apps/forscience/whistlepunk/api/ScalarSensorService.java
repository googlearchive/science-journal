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
package com.google.android.apps.forscience.whistlepunk.api;

import android.app.Service;
import android.content.Intent;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArrayMap;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConnector;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorStatusListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Support for creating your own sensor-exposing service.  To use, extend ScalarSensorService,
 * and override the abstract methods.
 *
 * For an example implementation,
 * @see com.google.android.apps.forscience.scalarapisample.AllNativeSensorProvider
 */
public abstract class ScalarSensorService extends Service {
    private ISensorDiscoverer.Stub mDiscoverer = null;

    /**
     * @return a human-readable name of this service
     */
    @NonNull
    protected abstract String getDiscovererName();

    /**
     * @return the devices that can currently be connected to.
     */
    protected abstract List<? extends AdvertisedDevice> getDevices();

    @Nullable
    @Override
    public final ISensorDiscoverer.Stub onBind(Intent intent) {
        return getDiscoverer();
    }

    private ISensorDiscoverer.Stub getDiscoverer() {
        if (mDiscoverer == null) {
            mDiscoverer = createDiscoverer();
        }
        return mDiscoverer;
    }

    private ISensorDiscoverer.Stub createDiscoverer() {
        final LinkedHashMap<String, AdvertisedDevice> devices = new LinkedHashMap<>();
        for (AdvertisedDevice device : getDevices()) {
            devices.put(device.getDeviceId(), device);
        }

        return new ISensorDiscoverer.Stub() {
            Map<String, AdvertisedSensor> mSensors = new ArrayMap<>();

            @Override
            public String getName() throws RemoteException {
                return getDiscovererName();
            }

            @Override
            public void scanDevices(IDeviceConsumer c) throws RemoteException {
                for (AdvertisedDevice device : devices.values()) {
                    c.onDeviceFound(device.getDeviceId(), device.getDeviceName(),
                            device.getSettingsIntent());
                }
            }

            @Override
            public void scanSensors(String deviceId, ISensorConsumer c) throws RemoteException {
                AdvertisedDevice device = devices.get(deviceId);
                if (device == null) {
                    return;
                }
                for (AdvertisedSensor sensor : device.getSensors()) {
                    mSensors.put(sensor.getAddress(), sensor);
                    c.onSensorFound(sensor.getAddress(), sensor.getName(), sensor.getBehavior(),
                            sensor.getAppearance());
                }
            }

            @Override
            public ISensorConnector getConnector() throws RemoteException {
                return new ISensorConnector.Stub() {
                    @Override
                    public void startObserving(final String sensorId,
                            final ISensorObserver observer, final ISensorStatusListener listener,
                            String settingsKey) throws RemoteException {
                        mSensors.get(sensorId).startObserving(observer, listener);
                    }

                    @Override
                    public void stopObserving(String sensorId) throws RemoteException {
                        mSensors.get(sensorId).stopObserving();
                    }
                };
            }
        };
    }
}
