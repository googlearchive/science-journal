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

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExpandableServiceAdapterTest {
    @Test
    public void hasSensorKeyIsCorrect() {
        ExpandableServiceAdapter adapter = ExpandableServiceAdapter.createEmpty(null, null, 0,
                new DeviceRegistry(null), null, null);
        ExternalSensorDiscoverer.DiscoveredService service =
                new ExternalSensorDiscoverer.DiscoveredService() {
                    @Override
                    public String getServiceId() {
                        return "serviceId";
                    }

                    @Override
                    public String getName() {
                        return "serviceName";
                    }

                    @Override
                    public Drawable getIconDrawable(Context context) {
                        return null;
                    }

                    @Override
                    public ExternalSensorDiscoverer.ServiceConnectionError
                    getConnectionErrorIfAny() {
                        return null;
                    }
                };
        adapter.addAvailableService("providerId", service, false);
        final BleSensorSpec spec = new BleSensorSpec("address", "name");
        final InputDeviceSpec deviceSpec = DeviceRegistry.createHoldingDevice(spec);
        adapter.addAvailableDevice(new ExternalSensorDiscoverer.DiscoveredDevice() {
            @Override
            public String getServiceId() {
                return "serviceId";
            }

            @Override
            public InputDeviceSpec getSpec() {
                return deviceSpec;
            }
        });

        assertFalse(adapter.hasSensorKey("sensorKey"));
        adapter.addAvailableSensor("sensorKey",
                new ConnectableSensor.Connector().disconnected(spec));
        assertTrue(adapter.hasSensorKey("sensorKey"));
    }
}