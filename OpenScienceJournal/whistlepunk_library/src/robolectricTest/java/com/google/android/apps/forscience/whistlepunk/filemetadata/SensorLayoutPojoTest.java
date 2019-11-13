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

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout.CardView;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the SensorLayoutPojo class. */
@RunWith(RobolectricTestRunner.class)
public class SensorLayoutPojoTest {

  @Test
  public void testGettersAndSetters() {
    SensorLayoutPojo pojo = new SensorLayoutPojo();
    pojo.setCardView(CardView.GRAPH);
    pojo.setSensorId("id");
    pojo.setAudioEnabled(true);
    pojo.setShowStatsOverlay(true);
    pojo.setColor(12);

    Map<String, String> extras = new HashMap<>();
    extras.put("foo", "bar");
    extras.put("blah", "foobar");
    pojo.setExtras(extras);

    pojo.setMinimumYAxisValue(10);
    pojo.setMaximumYAxisValue(200);

    pojo.addActiveTriggerId("triggerId");
    pojo.addActiveTriggerId("TID");

    pojo.setColorIndex(34);

    assertThat(pojo.getCardView()).isEqualTo(CardView.GRAPH);
    assertThat(pojo.getSensorId()).isEqualTo("id");
    assertThat(pojo.isAudioEnabled()).isTrue();
    assertThat(pojo.isShowStatsOverlay()).isTrue();
    assertThat(pojo.getColor()).isEqualTo(12);
    assertThat(pojo.getExtras()).containsEntry("foo", "bar");
    assertThat(pojo.getMinimumYAxisValue()).isEqualTo(10);
    assertThat(pojo.getMaximumYAxisValue()).isEqualTo(200);
    assertThat(pojo.getActiveSensorTriggerIds()).contains("TID");
    assertThat(pojo.getColorIndex()).isEqualTo(34);
  }

  @Test
  public void testProtoRoundTrip() {
    SensorLayoutPojo pojo = new SensorLayoutPojo();
    pojo.setCardView(CardView.GRAPH);
    pojo.setSensorId("id");
    pojo.setAudioEnabled(true);
    pojo.setShowStatsOverlay(true);
    pojo.setColor(12);

    Map<String, String> extras = new HashMap<>();
    extras.put("foo", "bar");
    extras.put("blah", "foobar");
    pojo.setExtras(extras);

    pojo.setMinimumYAxisValue(10);
    pojo.setMaximumYAxisValue(200);

    pojo.addActiveTriggerId("triggerId");
    pojo.addActiveTriggerId("TID");

    pojo.setColorIndex(34);
    SensorLayout proto = pojo.toProto();
    SensorLayoutPojo pojo2 = SensorLayoutPojo.fromProto(proto);
    SensorLayout proto2 = pojo2.toProto();

    assertThat(proto.toString()).isEqualTo(proto2.toString());
  }

  @Test
  public void testPartialProto() {
    SensorLayout proto = SensorLayout.newBuilder().setMaximumYAxisValue(25).build();
    SensorLayoutPojo pojo = SensorLayoutPojo.fromProto(proto);
    assertThat(pojo.getMaximumYAxisValue()).isEqualTo(25);

    SensorLayoutPojo pojo2 = new SensorLayoutPojo();
    pojo2.setSensorId("id");
    SensorLayout.Builder proto2 = pojo2.toProto().toBuilder();
    assertThat(proto2.getSensorId()).isEqualTo("id");
  }
}
