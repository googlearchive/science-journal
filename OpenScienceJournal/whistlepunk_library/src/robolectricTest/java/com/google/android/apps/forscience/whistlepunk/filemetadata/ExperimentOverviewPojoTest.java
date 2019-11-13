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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.ExperimentOverview;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the GadgetInfoPojo class. */
@RunWith(RobolectricTestRunner.class)
public class ExperimentOverviewPojoTest {

  @Test
  public void testGettersAndSetters() {
    ExperimentOverviewPojo pojo = new ExperimentOverviewPojo();
    pojo.setLastUsedTimeMs(25);
    pojo.setColorIndex(10);
    pojo.setTrialCount(5);
    pojo.setArchived(true);
    pojo.setExperimentId("id");
    pojo.setImagePath("path");
    pojo.setTitle("title");

    assertThat(pojo.getLastUsedTimeMs()).isEqualTo(25);
    assertThat(pojo.getColorIndex()).isEqualTo(10);
    assertThat(pojo.getTrialCount()).isEqualTo(5);
    assertThat(pojo.isArchived()).isEqualTo(true);
    assertThat(pojo.getExperimentId()).isEqualTo("id");
    ;
    assertThat(pojo.getImagePath()).isEqualTo("path");
    assertThat(pojo.getTitle()).isEqualTo("title");
  }

  @Test
  public void testProtoRoundTrip() {
    ExperimentOverview proto =
        ExperimentOverview.newBuilder()
            .setLastUsedTimeMs(25L)
            .setColorIndex(10)
            .setTrialCount(5)
            .setIsArchived(true)
            .setExperimentId("id")
            .setImagePath("path")
            .setTitle("title")
            .build();
    ExperimentOverviewPojo pojo = ExperimentOverviewPojo.fromProto(proto);
    ExperimentOverview proto2 = pojo.toProto();

    assertThat(proto.toString()).isEqualTo(proto2.toString());
  }

  @Test
  public void testPartialProto() {
    ExperimentOverview proto = ExperimentOverview.newBuilder().setLastUsedTimeMs(25).build();
    ExperimentOverviewPojo pojo = ExperimentOverviewPojo.fromProto(proto);
    assertThat(pojo.getLastUsedTimeMs()).isEqualTo(25);

    ExperimentOverviewPojo pojo2 = new ExperimentOverviewPojo();
    pojo2.setTitle("title");
    ExperimentOverview proto2 = pojo2.toProto();
    assertThat(proto2.getTitle()).isEqualTo("title");
  }
}
