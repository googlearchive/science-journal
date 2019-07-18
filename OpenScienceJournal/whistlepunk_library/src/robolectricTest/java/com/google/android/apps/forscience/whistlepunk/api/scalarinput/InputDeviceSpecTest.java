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

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec.DeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class InputDeviceSpecTest {
  @Test
  public void getAddress() {
    InputDeviceSpec spec =
        new InputDeviceSpec(ScalarInputSpec.TYPE, "serviceId_deviceId", "Device Name");
    assertEquals("DEVICE&serviceId_deviceId", spec.getAddress());
  }

  @Test
  public void getName() {
    InputDeviceSpec spec =
        new InputDeviceSpec(ScalarInputSpec.TYPE, "serviceId_deviceId", "Device Name");
    assertEquals("Device Name", spec.getSensorAppearance().getName(null));
  }

  @Test
  public void providerIdFromProto() {
    DeviceSpec proto =
        GoosciDeviceSpec.DeviceSpec.newBuilder()
            .setInfo(GoosciGadgetInfo.GadgetInfo.newBuilder().setProviderId("providerId"))
            .build();
    InputDeviceSpec spec = InputDeviceSpec.fromProto(proto);
    assertEquals("providerId", spec.getProviderType());
  }
}
