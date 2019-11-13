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

import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo.GadgetInfo.Platform;
import com.google.android.apps.forscience.whistlepunk.metadata.Version.FileVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the GadgetInfoPojo class. */
@RunWith(RobolectricTestRunner.class)
public class FileVersionPojoTest {

  @Test
  public void testGettersAndSetters() {
    FileVersionPojo pojo = new FileVersionPojo();
    pojo.setVersion(25);
    pojo.setMinorVersion(10);
    pojo.setPlatformVersion(5);
    pojo.setPlatform(Platform.IOS);

    assertThat(pojo.getVersion()).isEqualTo(25);
    assertThat(pojo.getMinorVersion()).isEqualTo(10);
    assertThat(pojo.getPlatformVersion()).isEqualTo(5);
    assertThat(pojo.getPlatform()).isEqualTo(Platform.IOS);
  }

  @Test
  public void testProtoRoundTrip() {
    FileVersion proto =
        FileVersion.newBuilder()
            .setVersion(25)
            .setMinorVersion(10)
            .setPlatformVersion(5)
            .setPlatform(Platform.IOS)
            .build();

    FileVersionPojo pojo = FileVersionPojo.fromProto(proto);
    FileVersion proto2 = pojo.toProto();
    assertThat(proto).isEqualTo(proto2);
  }

  @Test
  public void testPartialProto() {
    FileVersion proto = FileVersion.newBuilder().setVersion(25).build();

    FileVersionPojo pojo = FileVersionPojo.fromProto(proto);
    assertThat(pojo.getVersion()).isEqualTo(25);

    FileVersionPojo pojo2 = new FileVersionPojo();
    pojo2.setMinorVersion(10);
    FileVersion proto2 = pojo2.toProto();
    assertThat(proto2.getMinorVersion()).isEqualTo(10);
  }
}
