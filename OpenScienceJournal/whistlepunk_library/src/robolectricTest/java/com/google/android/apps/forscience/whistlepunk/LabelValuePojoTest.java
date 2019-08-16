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
package com.google.android.apps.forscience.whistlepunk;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue.LabelValue.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabelValue;
import com.google.common.testing.EqualsTester;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs.Destination;
import com.google.protobuf.nano.MessageNano;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for LabelValuePojo classe.
 */
@RunWith(RobolectricTestRunner.class)
public class LabelValuePojoTest {
  private static final GoosciLabelValue.LabelValue defaultProto = new GoosciLabelValue.LabelValue();
  private static final GoosciLabelValue.LabelValue proto;
  static {
    @MigrateAs(Destination.BUILDER)
    GoosciLabelValue.LabelValue builder = new GoosciLabelValue.LabelValue();
    builder.type = ValueType.PICTURE;
    builder.putData("favoriteColor", "purple");
    builder.putData("favoriteFood", "lobster");
    proto = builder;
  }

  @Test
  public void testToProto() {
    LabelValuePojo defaultPojo = new LabelValuePojo();
    assertThat(MessageNano.toByteArray(defaultPojo.toProto())).isEqualTo(MessageNano.toByteArray(defaultProto));

    LabelValuePojo pojo = new LabelValuePojo(proto);
    assertThat(MessageNano.toByteArray(pojo.toProto())).isEqualTo(MessageNano.toByteArray(proto));
  }

  @Test
  public void testGetType() {
    LabelValuePojo defaultPojo = new LabelValuePojo();
    assertThat(defaultPojo.getType()).isEqualTo(ValueType.TEXT);

    LabelValuePojo pojo = new LabelValuePojo(proto);
    assertThat(pojo.getType()).isEqualTo(ValueType.PICTURE);
  }

  @Test
  public void testGetDataOrThrow() {
    LabelValuePojo defaultPojo = new LabelValuePojo();
    assertThrows(IllegalArgumentException.class, () -> defaultPojo.getDataOrThrow("Elvis"));

    LabelValuePojo pojo = new LabelValuePojo(proto);
    assertThat(pojo.getDataOrThrow("favoriteColor")).isEqualTo("purple");
    assertThrows(IllegalArgumentException.class, () -> pojo.getDataOrThrow("Elvis"));
  }

  @Test
  public void testGetDataOrDefault() {
    LabelValuePojo defaultPojo = new LabelValuePojo();
    assertThat(defaultPojo.getDataOrDefault("favoriteCar", "Prius")).isEqualTo("Prius");

    LabelValuePojo pojo = new LabelValuePojo(proto);
    assertThat(pojo.getDataOrDefault("favoriteColor", "orange")).isEqualTo("purple");
    assertThat(pojo.getDataOrDefault("favoriteCar", "Prius")).isEqualTo("Prius");
  }

  @Test
  public void testEquals() {
    LabelValuePojo defaultPojo1 = new LabelValuePojo();
    LabelValuePojo defaultPojo2 = new LabelValuePojo(defaultProto);

    LabelValuePojo pojo1 = new LabelValuePojo(proto);
    LabelValuePojo pojo2 = new LabelValuePojo();
    pojo2.setType(ValueType.PICTURE);
    pojo2.putData("favoriteColor", "purple");
    pojo2.putData("favoriteFood", "lobster");

    new EqualsTester()
        .addEqualityGroup(defaultPojo1, defaultPojo2)
        .addEqualityGroup(pojo1, pojo2)
        .testEquals();
  }
}
