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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.drawable.Drawable;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ExpandableServiceAdapterTest {
  @Test
  public void hasSensorKeyIsCorrect() {
    final BleSensorSpec spec = new BleSensorSpec("address", "name");
    ExpandableServiceAdapter adapter =
        ExpandableServiceAdapter.createEmpty(null, null, 0, new DeviceRegistry(null), null, null);
    SensorDiscoverer.DiscoveredService service =
        new SensorDiscoverer.DiscoveredService() {
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
          public SensorDiscoverer.ServiceConnectionError getConnectionErrorIfAny() {
            return null;
          }
        };
    adapter.addAvailableService("providerId", service, false);
    final InputDeviceSpec deviceSpec = DeviceRegistry.createHoldingDevice(spec);
    adapter.addAvailableDevice(
        new SensorDiscoverer.DiscoveredDevice() {
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
    adapter.addAvailableSensor(
        "sensorKey",
        new ConnectableSensor.Connector(EnumeratedDiscoverer.buildProviderMap(spec))
            .disconnected(spec.asGoosciSpec()));
    assertTrue(adapter.hasSensorKey("sensorKey"));
  }
}
