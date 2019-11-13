/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerLabelValue.SensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue.TextLabelValue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for labels. Labels implement Parcelable so they need to be tested in AndroidTest. */
@RunWith(RobolectricTestRunner.class)
public class LabelTest {
  private GoosciLabel.Label makeGoosciLabel(ValueType type, long timestamp) {
    return GoosciLabel.Label.newBuilder()
        .setTimestampMs(timestamp)
        .setCreationTimeMs(timestamp)
        .setType(type)
        .setLabelId("labelId")
        .build();
  }

  @Test
  public void testCanEditLabelTimestamp() {
    GoosciLabel.Label goosciLabel = makeGoosciLabel(ValueType.TEXT, 10);
    Label label = Label.fromLabel(goosciLabel);

    assertEquals(label.getTimeStamp(), 10);
    label.setTimestamp(20);
    assertEquals(label.getTimeStamp(), 20);
  }

  @Test
  public void testCanCreateTextLabel() {
    GoosciLabel.Label goosciLabel = makeGoosciLabel(ValueType.TEXT, 10);
    Label label = Label.fromLabel(goosciLabel);

    assertEquals(label.getType(), ValueType.TEXT);

    TextLabelValue labelValue =
        label.getTextLabelValue().toBuilder().setText("The meaning of life").build();
    label.setLabelProtoData(labelValue);

    assertEquals("The meaning of life", label.getTextLabelValue().getText());
    assertEquals(label.canEditTimestamp(), true);
  }

  @Test
  public void testCanCreatePictureLabel() {
    Caption caption =
        GoosciCaption.Caption.newBuilder().setText("kitten").setLastEditedTimestamp(5).build();
    PictureLabelValue labelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("path/to/photo").build();
    Label label = Label.newLabelWithValue(10, ValueType.PICTURE, labelValue, caption);

    assertEquals(label.getType(), ValueType.PICTURE);
    assertEquals("path/to/photo", label.getPictureLabelValue().getFilePath());
    assertEquals(10, label.getTimeStamp());
    assertEquals("kitten", label.getCaptionText());
    assertEquals(label.canEditTimestamp(), true);
  }

  @Test
  public void testCanCreateTriggerLabel() {
    SensorTrigger trigger =
        SensorTrigger.newNoteTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            "note",
            7.5);
    SensorTriggerLabelValue labelValue =
        GoosciSensorTriggerLabelValue.SensorTriggerLabelValue.newBuilder()
            .setTriggerInformation(trigger.getTriggerProto().getTriggerInformation())
            .build();

    Label label = Label.newLabelWithValue(10, ValueType.SENSOR_TRIGGER, labelValue, null);

    assertEquals(ValueType.SENSOR_TRIGGER, label.getType());

    assertEquals(label.getSensorTriggerLabelValue().getTriggerInformation().getNoteText(), "note");
    assertEquals(label.canEditTimestamp(), false);
    assertEquals(
        label.getSensorTriggerLabelValue().getTriggerInformation().getTriggerWhen(),
        GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW);
  }

  @Test
  public void testParcelableBehavior() {
    GoosciLabel.Label.Builder goosciLabel =
        GoosciLabel.Label.newBuilder().setTimestampMs(10).setType(ValueType.SENSOR_TRIGGER);
    SensorTrigger trigger =
        SensorTrigger.newNoteTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            "note",
            7.5);
    SensorTriggerLabelValue labelValue =
        GoosciSensorTriggerLabelValue.SensorTriggerLabelValue.newBuilder()
            .setTriggerInformation(trigger.getTriggerProto().getTriggerInformation())
            .build();
    goosciLabel.setProtoData(labelValue.toByteString());
    Label label = Label.fromLabel(goosciLabel.build());

    label.setTimestamp(20);

    Parcel parcel = Parcel.obtain();
    label.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);
    Label result = Label.CREATOR.createFromParcel(parcel);

    assertEquals(ValueType.SENSOR_TRIGGER, result.getType());
    assertEquals(20, result.getTimeStamp());
    assertEquals("note", result.getSensorTriggerLabelValue().getTriggerInformation().getNoteText());
  }

  @Test
  public void testUniqueIds() {
    Label first = Label.newLabel(10, ValueType.TEXT);
    Label second = Label.newLabel(10, ValueType.TEXT);
    assertNotEquals(first.getLabelId(), second.getLabelId());

    Label firstAgain = Label.fromLabel(first.getLabelProto());
    assertEquals(first.getLabelId(), firstAgain.getLabelId());
  }

  @Test
  public void testDeepCopy() {
    TextLabelValue textLabelValue1 =
        GoosciTextLabelValue.TextLabelValue.newBuilder().setText("peanutbutter").build();
    Label first = Label.newLabelWithValue(10, ValueType.TEXT, textLabelValue1, null);
    Label second = Label.copyOf(first);
    assertNotEquals(first.getLabelId(), second.getLabelId());
    assertEquals(first.getTimeStamp(), second.getTimeStamp());
    assertEquals(ValueType.TEXT, second.getType());
    assertEquals("peanutbutter", second.getTextLabelValue().getText());

    // Changing the first doesn't change the second.
    TextLabelValue textLabelValue2 =
        GoosciTextLabelValue.TextLabelValue.newBuilder().setText("jelly").build();
    first.setLabelProtoData(textLabelValue2);
    assertEquals("peanutbutter", second.getTextLabelValue().getText());

    first.setTimestamp(20);
    assertEquals(second.getTimeStamp(), 10);
  }
}
