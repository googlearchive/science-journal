/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.apps.forscience.samplegyroprovider;

import android.app.Service;
import android.content.Intent;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorDiscoverer;

public class GyroAddedSensor extends Service {
    public static final String DEVICE_ID = "onlyDevice";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private ISensorDiscoverer.Stub mDiscoverer = null;

    @Nullable
    @Override
    public ISensorDiscoverer.Stub onBind(Intent intent) {
        return getDiscoverer();
    }

    public ISensorDiscoverer.Stub getDiscoverer() {
        if (mDiscoverer == null) {
            mDiscoverer = createDiscoverer();
        }
        return mDiscoverer;
    }

    private ISensorDiscoverer.Stub createDiscoverer() {
        return new ISensorDiscoverer.Stub() {
            @Override
            public String getName() throws RemoteException {
                return "GYRO";
            }

            @Override
            public void scanDevices(IDeviceConsumer c) throws RemoteException {
                c.onDeviceFound(DEVICE_ID, "Phone gyros", null);
            }

            @Override
            public void scanSensors(String deviceId, ISensorConsumer c) throws RemoteException {
                if (! DEVICE_ID.equals(deviceId)) {
                    return;
                }
                c.onSensorFound("GYRO_X", "gyro.x", null);
                c.onSensorFound("GYRO_Y", "gyro.y", null);
                c.onSensorFound("GYRO_Z", "gyro.z", null);
            }
        };
    }
}
