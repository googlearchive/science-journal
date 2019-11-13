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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.UserMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the GadgetInfoPojo class. */
@RunWith(RobolectricTestRunner.class)
public class UserMetadataPojoTest {

  @Test
  public void testGettersAndSetters() {
    UserMetadataPojo pojo = new UserMetadataPojo();
    pojo.setVersion(25);
    pojo.setMinorVersion(10);
    FileVersionPojo fileVersion = new FileVersionPojo();
    pojo.setFileVersion(fileVersion);
    DeviceSpecPojo device = new DeviceSpecPojo();
    pojo.addDevice(device);

    ExperimentOverviewPojo overview = new ExperimentOverviewPojo();
    overview.setExperimentId("id");

    pojo.insertOverview(overview);

    assertThat(pojo.getVersion()).isEqualTo(25);
    assertThat(pojo.getMinorVersion()).isEqualTo(10);
    assertThat(pojo.getFileVersion()).isEqualTo(fileVersion);
    assertThat(pojo.getMyDevices()).hasSize(1);
    assertThat(pojo.getOverview("id")).isEqualTo(overview);
  }

  @Test
  public void testProtoRoundTrip() {
    UserMetadataPojo pojo = new UserMetadataPojo();
    pojo.setVersion(25);
    pojo.setMinorVersion(10);
    FileVersionPojo fileVersion = new FileVersionPojo();
    pojo.setFileVersion(fileVersion);
    DeviceSpecPojo device = new DeviceSpecPojo();
    device.setName("name");
    device.setGadgetInfo(new GadgetInfoPojo());
    pojo.addDevice(device);

    ExperimentOverviewPojo overview = new ExperimentOverviewPojo();
    overview.setExperimentId("id");

    pojo.insertOverview(overview);

    UserMetadata proto = pojo.toProto();
    UserMetadataPojo pojo2 = UserMetadataPojo.fromProto(proto);
    UserMetadata proto2 = pojo2.toProto();

    assertThat(proto.toString()).isEqualTo(proto2.toString());
  }

  @Test
  public void testPartialProto() {
    UserMetadata proto = UserMetadata.newBuilder().setVersion(5).build();

    UserMetadataPojo pojo = UserMetadataPojo.fromProto(proto);
    assertThat(pojo.getVersion()).isEqualTo(5);

    UserMetadataPojo pojo2 = new UserMetadataPojo();
    pojo2.setMinorVersion(10);
    UserMetadata proto2 = pojo2.toProto();
    assertThat(proto2.getMinorVersion()).isEqualTo(10);
  }
}
