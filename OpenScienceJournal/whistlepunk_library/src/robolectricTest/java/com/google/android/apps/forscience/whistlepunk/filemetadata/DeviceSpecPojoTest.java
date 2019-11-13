/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec.DeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo.GadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo.GadgetInfo.Platform;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the GadgetInfoPojo class. */
@RunWith(RobolectricTestRunner.class)
public class DeviceSpecPojoTest {

  @Test
  public void testGettersAndSetters() {
    GadgetInfoPojo gadget = new GadgetInfoPojo();
    gadget.setHostId("id");

    DeviceSpecPojo pojo = new DeviceSpecPojo();
    pojo.setName("name");
    pojo.setGadgetInfo(gadget);

    assertThat(pojo.getGadgetInfo().getHostId()).isEqualTo("id");
    assertThat(pojo.getName()).isEqualTo("name");
  }

  @Test
  public void testProtoRoundTrip() {
    GadgetInfo info =
        GadgetInfo.newBuilder()
            .setAddress("add")
            .setHostId("hid")
            .setProviderId("pid")
            .setHostDescription("host")
            .setPlatform(Platform.IOS)
            .build();
    DeviceSpec proto = DeviceSpec.newBuilder().setName("name").setInfo(info).build();
    DeviceSpecPojo pojo = DeviceSpecPojo.fromProto(proto);
    DeviceSpec proto2 = pojo.toProto();

    assertThat(proto).isEqualTo(proto2);
  }

  @Test
  public void testPartialProto() {
    DeviceSpec proto = DeviceSpec.newBuilder().setName("name").build();
    DeviceSpecPojo pojo = DeviceSpecPojo.fromProto(proto);
    assertThat(pojo.getName()).isEqualTo("name");

    DeviceSpecPojo pojo2 = new DeviceSpecPojo();
    pojo2.setName("name");
    DeviceSpec proto2 = pojo2.toProto();
    assertThat(proto2.getName()).isEqualTo("name");
  }
}
