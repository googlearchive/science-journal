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
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;


/**
 * Tests for labels.
 * Labels implement Parcelable so they need to be tested in AndroidTest.
 */
public class LabelTest extends AndroidTestCase {

    private GoosciLabel.Label makeGoosciLabel(int type, long timestamp) {
        GoosciLabel.Label goosciLabel = new GoosciLabel.Label();
        goosciLabel.timestampMs = timestamp;
        GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
        value.type = type;
        goosciLabel.values = new GoosciLabelValue.LabelValue[]{value};
        return goosciLabel;
    }

    public void testCanEditLabelTimestamp() {
        GoosciLabel.Label goosciLabel = makeGoosciLabel(GoosciLabelValue.LabelValue.TEXT, 10);
        Label label = new Label(goosciLabel);

        assertEquals(label.getTimeStamp(), 10);
        label.setTimestamp(20);
        assertEquals(label.getTimeStamp(), 20);
    }

    public void testCanCreateTextLabel() {
        GoosciLabel.Label goosciLabel = makeGoosciLabel(GoosciLabelValue.LabelValue.TEXT, 10);
        Label label = new Label(goosciLabel);

        assertEquals(label.getValueTypes().size(), 1);
        assertTrue(label.getValueTypes().contains(GoosciLabelValue.LabelValue.TEXT));

        assertNull(label.getLabelValue(GoosciLabelValue.LabelValue.PICTURE));
        assertNull(label.getLabelValue(GoosciLabelValue.LabelValue.SENSOR_TRIGGER));

        TextLabelValue labelValue = (TextLabelValue) label.getLabelValue(
                GoosciLabelValue.LabelValue.TEXT);
        labelValue.setText("The meaning of life");
        assertEquals(labelValue.getText(), "The meaning of life");
    }

    public void testCanCreatePictureLabel() {
        GoosciLabel.Label goosciLabel = makeGoosciLabel(GoosciLabelValue.LabelValue.PICTURE, 10);
        PictureLabelValue.populateLabelValue(goosciLabel.values[0], "path/to/photo", "cheese!");
        Label label = new Label(goosciLabel);

        assertEquals(label.getValueTypes().size(), 1);
        assertTrue(label.getValueTypes().contains(GoosciLabelValue.LabelValue.PICTURE));

        PictureLabelValue labelValue = (PictureLabelValue) label.getLabelValue(
                GoosciLabelValue.LabelValue.PICTURE);
        assertEquals(labelValue.getFilePath(), "path/to/photo");
        assertEquals(labelValue.getCaption(), "cheese!");

    }

    public void testCanCreateTriggerLabel() {
        GoosciLabel.Label goosciLabel = makeGoosciLabel(GoosciLabelValue.LabelValue.SENSOR_TRIGGER,
                10);
        SensorTrigger trigger = SensorTrigger.newNoteTypeTrigger("id", "sensorId",
                GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_WHEN_DROPS_BELOW, "note",
                7.5);
        SensorTriggerLabelValue.populateLabelValue(goosciLabel.values[0], trigger, "note");
        Label label = new Label(goosciLabel);

        assertEquals(label.getValueTypes().size(), 1);
        assertTrue(label.getValueTypes().contains(GoosciLabelValue.LabelValue.SENSOR_TRIGGER));

        SensorTriggerLabelValue labelValue = (SensorTriggerLabelValue) label.getLabelValue(
                GoosciLabelValue.LabelValue.SENSOR_TRIGGER);
        assertEquals(labelValue.getCustomText(), "note");
        assertEquals(labelValue.canEditTimestamp(), false);
        assertEquals(labelValue.getSensorId(), "sensorId");
    }

    public void testCanCreateLabelWithMultipleTypes() {
        GoosciLabel.Label goosciLabel = new GoosciLabel.Label();
        goosciLabel.timestampMs = 10;

        GoosciLabelValue.LabelValue value1 = new GoosciLabelValue.LabelValue();
        SensorTrigger trigger = SensorTrigger.newNoteTypeTrigger("id", "sensorId",
                GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_WHEN_DROPS_BELOW, "note",
                7.5);
        SensorTriggerLabelValue.populateLabelValue(value1, trigger, "note");

        GoosciLabelValue.LabelValue value2 = new GoosciLabelValue.LabelValue();
        PictureLabelValue.populateLabelValue(value2, "path/to/photo", "cheese!");

        goosciLabel.values = new GoosciLabelValue.LabelValue[] {value1, value2};

        Label label = new Label(goosciLabel);

        assertTrue(label.hasValueType(GoosciLabelValue.LabelValue.PICTURE));
        assertTrue(label.hasValueType(GoosciLabelValue.LabelValue.SENSOR_TRIGGER));
        assertFalse(label.hasValueType(GoosciLabelValue.LabelValue.TEXT));
    }

    public void testOnlyOneOfEachType() {
        GoosciLabel.Label goosciLabel = makeGoosciLabel(GoosciLabelValue.LabelValue.PICTURE, 10);
        Label label = new Label(goosciLabel);

        assertTrue(label.hasValueType(GoosciLabelValue.LabelValue.PICTURE));
        assertEquals(label.getValueTypes().size(), 1);
        assertNotEquals(((PictureLabelValue) label.getLabelValue(
                GoosciLabelValue.LabelValue.PICTURE))
                .getCaption(), "potato");

        PictureLabelValue newLabelValue = new PictureLabelValue();
        newLabelValue.setCaption("potato");

        label.setLabelValue(newLabelValue);

        assertTrue(label.hasValueType(GoosciLabelValue.LabelValue.PICTURE));
        assertEquals(label.getValueTypes().size(), 1);
        assertEquals(((PictureLabelValue) label.getLabelValue(GoosciLabelValue.LabelValue.PICTURE))
                .getCaption(), "potato");

    }

    public void testParcelableBehavior() {
        GoosciLabel.Label goosciLabel = new GoosciLabel.Label();
        goosciLabel.timestampMs = 10;
        GoosciLabelValue.LabelValue labelValue = new GoosciLabelValue.LabelValue();
        SensorTrigger trigger = SensorTrigger.newNoteTypeTrigger("id", "sensorId",
                GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_WHEN_DROPS_BELOW, "note",
                7.5);
        SensorTriggerLabelValue.populateLabelValue(labelValue, trigger, "note");
        Label label = new Label(goosciLabel);

        // Add a text note after the label has been created.
        TextLabelValue textLabelValue = new TextLabelValue();
        textLabelValue.setText("Text notes rock!");
        label.setLabelValue(textLabelValue);

        Parcel parcel = Parcel.obtain();
        label.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        Label result = Label.CREATOR.createFromParcel(parcel);
        assertEquals(result.getLabelValue(GoosciLabelValue.LabelValue.SENSOR_TRIGGER),
                label.getLabelValue(GoosciLabelValue.LabelValue.SENSOR_TRIGGER));
        assertTrue(result.hasValueType(GoosciLabelValue.LabelValue.TEXT));
        assertEquals(((TextLabelValue) result.getLabelValue(GoosciLabelValue.LabelValue.TEXT))
                .getText(), "Text notes rock!");
    }
}
