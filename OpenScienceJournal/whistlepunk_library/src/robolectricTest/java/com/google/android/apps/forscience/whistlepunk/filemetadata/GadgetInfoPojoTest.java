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

import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo.GadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo.GadgetInfo.Platform;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the GadgetInfoPojo class. */
@RunWith(RobolectricTestRunner.class)
public class GadgetInfoPojoTest {

  @Test
  public void testGettersAndSetters() {
    GadgetInfoPojo pojo = new GadgetInfoPojo();
    pojo.setPlatform(Platform.ANDROID);
    pojo.setHostId("id");
    pojo.setHostDescription("description");
    pojo.setAddress("address");
    pojo.setProviderId("provider");

    assertThat(pojo.getPlatform()).isEqualTo(Platform.ANDROID);
    assertThat(pojo.getHostId()).isEqualTo("id");
    assertThat(pojo.getHostDescription()).isEqualTo("description");
    assertThat(pojo.getAddress()).isEqualTo("address");
    assertThat(pojo.getProviderId()).isEqualTo("provider");
  }

  @Test
  public void testProtoRoundTrip() {
    GadgetInfo proto =
        GadgetInfo.newBuilder()
            .setAddress("add")
            .setHostId("hid")
            .setProviderId("pid")
            .setHostDescription("host")
            .setPlatform(Platform.IOS)
            .build();
    GadgetInfoPojo pojo = GadgetInfoPojo.fromProto(proto);
    GadgetInfo proto2 = pojo.toProto();

    assertThat(proto).isEqualTo(proto2);
  }

  @Test
  public void testPartialProto() {
    GadgetInfo proto = GadgetInfo.newBuilder().setAddress("add").build();
    GadgetInfoPojo pojo = GadgetInfoPojo.fromProto(proto);
    assertThat(pojo.getAddress()).isEqualTo("add");

    GadgetInfoPojo pojo2 = new GadgetInfoPojo();
    pojo2.setProviderId("id");
    GadgetInfo proto2 = pojo2.toProto();
    assertThat(proto2.getProviderId()).isEqualTo("id");
  }
}
