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

import static junit.framework.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.LabelValuePojo;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for deprecated LabelValue classes. These are used to make sure we can parse old LabelValues
 * correctly when pulling them out of the DB for upgrade.
 */
@RunWith(RobolectricTestRunner.class)
public class LabelValueTest {
  @Test
  public void testTextLabelValue() {
    TextLabelValue textLabelValue = TextLabelValue.fromText("potato");
    assertEquals("potato", textLabelValue.getText());

    textLabelValue.setText("tomato");
    assertEquals("tomato", textLabelValue.getText());
  }

  @Test
  public void testPictureLabelValue() {
    LabelValuePojo value = new LabelValuePojo();
    PictureLabelValue.populateLabelValue(value, "path/to/photo", "cheese!");

    assertEquals("path/to/photo", PictureLabelValue.getFilePath(value));
    assertEquals("cheese!", PictureLabelValue.getCaption(value));

    PictureLabelValue labelValue = PictureLabelValue.fromPicture("path/to/photo", "cheese!");
    assertEquals("path/to/photo", labelValue.getFilePath());
    assertEquals("cheese!", labelValue.getCaption());
  }

  @Test
  public void testSnapshotLabelValue() {
    SensorTrigger trigger =
        SensorTrigger.newNoteTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            "note",
            7.5);
    LabelValuePojo value = new LabelValuePojo();
    SensorTriggerLabelValue.populateLabelValue(value, trigger, "note");

    assertEquals("note", SensorTriggerLabelValue.getCustomText(value));
    assertEquals("sensorId", SensorTriggerLabelValue.getSensorId(value));
  }
}
