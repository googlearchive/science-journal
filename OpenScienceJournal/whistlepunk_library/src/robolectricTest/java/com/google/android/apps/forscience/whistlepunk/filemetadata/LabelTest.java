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
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciSensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTextLabelValue;
import com.google.protobuf.nano.MessageNano;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for labels. Labels implement Parcelable so they need to be tested in AndroidTest. */
@RunWith(RobolectricTestRunner.class)
public class LabelTest {
  private GoosciLabel.Label makeGoosciLabel(int type, long timestamp) {
    GoosciLabel.Label goosciLabel = new GoosciLabel.Label();
    goosciLabel.timestampMs = timestamp;
    goosciLabel.creationTimeMs = timestamp;
    goosciLabel.type = type;
    goosciLabel.labelId = "labelId";
    return goosciLabel;
  }

  @Test
  public void testCanEditLabelTimestamp() {
    GoosciLabel.Label goosciLabel = makeGoosciLabel(GoosciLabel.Label.ValueType.TEXT, 10);
    Label label = Label.fromLabel(goosciLabel);

    assertEquals(label.getTimeStamp(), 10);
    label.setTimestamp(20);
    assertEquals(label.getTimeStamp(), 20);
  }

  @Test
  public void testCanCreateTextLabel() {
    GoosciLabel.Label goosciLabel = makeGoosciLabel(GoosciLabel.Label.ValueType.TEXT, 10);
    Label label = Label.fromLabel(goosciLabel);

    assertEquals(label.getType(), GoosciLabel.Label.ValueType.TEXT);

    GoosciTextLabelValue.TextLabelValue labelValue = label.getTextLabelValue();
    labelValue.text = "The meaning of life";
    label.setLabelProtoData(labelValue);

    assertEquals("The meaning of life", label.getTextLabelValue().text);
    assertEquals(label.canEditTimestamp(), true);
  }

  @Test
  public void testCanCreatePictureLabel() {
    GoosciCaption.Caption caption = new GoosciCaption.Caption();
    caption.text = "kitten";
    caption.lastEditedTimestamp = 5;
    GoosciPictureLabelValue.PictureLabelValue labelValue =
        new GoosciPictureLabelValue.PictureLabelValue();
    labelValue.filePath = "path/to/photo";
    Label label =
        Label.newLabelWithValue(10, GoosciLabel.Label.ValueType.PICTURE, labelValue, caption);

    assertEquals(label.getType(), GoosciLabel.Label.ValueType.PICTURE);
    assertEquals("path/to/photo", label.getPictureLabelValue().filePath);
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
    GoosciSensorTriggerLabelValue.SensorTriggerLabelValue labelValue =
        new GoosciSensorTriggerLabelValue.SensorTriggerLabelValue();
    labelValue.triggerInformation = trigger.getTriggerProto().triggerInformation;

    Label label =
        Label.newLabelWithValue(10, GoosciLabel.Label.ValueType.SENSOR_TRIGGER, labelValue, null);

    assertEquals(GoosciLabel.Label.ValueType.SENSOR_TRIGGER, label.getType());

    assertEquals(label.getSensorTriggerLabelValue().triggerInformation.noteText, "note");
    assertEquals(label.canEditTimestamp(), false);
    assertEquals(
        label.getSensorTriggerLabelValue().triggerInformation.triggerWhen,
        GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW);
  }

  @Test
  public void testParcelableBehavior() {
    GoosciLabel.Label goosciLabel = new GoosciLabel.Label();
    goosciLabel.timestampMs = 10;
    goosciLabel.type = GoosciLabel.Label.ValueType.SENSOR_TRIGGER;
    SensorTrigger trigger =
        SensorTrigger.newNoteTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            "note",
            7.5);
    GoosciSensorTriggerLabelValue.SensorTriggerLabelValue labelValue =
        new GoosciSensorTriggerLabelValue.SensorTriggerLabelValue();
    labelValue.triggerInformation = trigger.getTriggerProto().triggerInformation;
    goosciLabel.protoData = MessageNano.toByteArray(labelValue);
    Label label = Label.fromLabel(goosciLabel);

    label.setTimestamp(20);

    Parcel parcel = Parcel.obtain();
    label.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);
    Label result = Label.CREATOR.createFromParcel(parcel);

    assertEquals(GoosciLabel.Label.ValueType.SENSOR_TRIGGER, result.getType());
    assertEquals(20, result.getTimeStamp());
    assertEquals("note", result.getSensorTriggerLabelValue().triggerInformation.noteText);
  }

  @Test
  public void testUniqueIds() {
    Label first = Label.newLabel(10, GoosciLabel.Label.ValueType.TEXT);
    Label second = Label.newLabel(10, GoosciLabel.Label.ValueType.TEXT);
    assertNotEquals(first.getLabelId(), second.getLabelId());

    Label firstAgain = Label.fromLabel(first.getLabelProto());
    assertEquals(first.getLabelId(), firstAgain.getLabelId());
  }

  @Test
  public void testDeepCopy() {
    GoosciTextLabelValue.TextLabelValue textLabelValue = new GoosciTextLabelValue.TextLabelValue();
    textLabelValue.text = "peanutbutter";
    Label first =
        Label.newLabelWithValue(10, GoosciLabel.Label.ValueType.TEXT, textLabelValue, null);
    Label second = Label.copyOf(first);
    assertNotEquals(first.getLabelId(), second.getLabelId());
    assertEquals(first.getTimeStamp(), second.getTimeStamp());
    assertEquals(GoosciLabel.Label.ValueType.TEXT, second.getType());
    assertEquals("peanutbutter", second.getTextLabelValue().text);

    // Changing the first doesn't change the second.
    textLabelValue.text = "jelly";
    first.setLabelProtoData(textLabelValue);
    assertEquals("peanutbutter", second.getTextLabelValue().text);

    first.setTimestamp(20);
    assertEquals(second.getTimeStamp(), 10);
  }
}
