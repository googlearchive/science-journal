/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerAlertType;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the SensorTrigger class. */
@RunWith(RobolectricTestRunner.class)
public class SensorTriggerTest {
  @Test
  public void testSetActionType_noChangeWhenEqual() {
    SensorTrigger trigger =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    SensorTrigger same =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    trigger.setTriggerActionType(
        GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
            .TRIGGER_ACTION_START_RECORDING);
    assertThat(trigger.userSettingsEquals(same)).isTrue();

    trigger =
        SensorTrigger.newAlertTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            ImmutableSet.of(
                TriggerAlertType.TRIGGER_ALERT_VISUAL, TriggerAlertType.TRIGGER_ALERT_AUDIO),
            10.);
    same =
        SensorTrigger.newAlertTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            ImmutableSet.of(
                TriggerAlertType.TRIGGER_ALERT_VISUAL, TriggerAlertType.TRIGGER_ALERT_AUDIO),
            10.);
    trigger.setTriggerActionType(
        GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType.TRIGGER_ACTION_ALERT);
    assertThat(trigger.userSettingsEquals(same)).isTrue();
  }

  @Test
  public void testSetActionType_clearsMetaDataWhenChanged() {
    SensorTrigger trigger =
        SensorTrigger.newAlertTypeTrigger(
            "1",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            ImmutableSet.of(
                TriggerAlertType.TRIGGER_ALERT_VISUAL, TriggerAlertType.TRIGGER_ALERT_AUDIO),
            10.);
    assertThat(trigger.getNoteText()).isEmpty();
    assertThat(trigger.getAlertTypes()).hasSize(2);

    trigger.setTriggerActionType(
        GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType.TRIGGER_ACTION_NOTE);
    assertThat(trigger.getNoteText()).isEmpty();
    assertThat(trigger.getAlertTypes()).isEmpty();

    trigger =
        SensorTrigger.newNoteTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            "text",
            10.);
    assertThat(trigger.getNoteText()).isEqualTo("text");
    assertThat(trigger.getAlertTypes()).isEmpty();

    trigger.setTriggerActionType(
        GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
            .TRIGGER_ACTION_START_RECORDING);
    assertThat(trigger.getNoteText()).isEmpty();
    assertThat(trigger.getAlertTypes()).isEmpty();
  }

  @Test
  public void testIsTriggered_datapointAtUnequal() {
    SensorTrigger trigger =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    assertThat(trigger.isTriggered(9.)).isFalse();
    assertThat(trigger.isTriggered(10.)).isTrue();
  }

  @Test
  public void testIsTriggered_datapointAtEquals() {
    SensorTrigger trigger =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    // Never trigger on the first point.
    assertThat(trigger.isTriggered(10.)).isFalse();

    // Fire if the value is equal.
    assertThat(trigger.isTriggered(10.)).isTrue();

    // Fire when reaching the value from below.
    assertThat(trigger.isTriggered(9.)).isFalse();
    assertThat(trigger.isTriggered(10.)).isTrue();

    // Fire when reaching the value from above.
    assertThat(trigger.isTriggered(11.)).isFalse();
    assertThat(trigger.isTriggered(10.)).isTrue();

    // Fire when crossing from above to below.
    assertThat(trigger.isTriggered(11.)).isFalse();
    assertThat(trigger.isTriggered(9.)).isTrue();
  }

  @Test
  public void testIsTriggered_rising() {
    SensorTrigger trigger =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_RISES_ABOVE,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    // The first point should aways be false because we have no comparison.
    assertThat(trigger.isTriggered(10.)).isFalse();
    assertThat(trigger.isTriggered(10.)).isFalse();
    assertThat(trigger.isTriggered(9.)).isFalse();
    assertThat(trigger.isTriggered(11.)).isTrue();
    assertThat(trigger.isTriggered(10.)).isFalse();
    assertThat(trigger.isTriggered(11.)).isTrue();
  }

  @Test
  public void testIsTriggered_falling() {
    SensorTrigger trigger =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    // The first point should aways be false because we have no comparison.
    assertThat(trigger.isTriggered(10.)).isFalse();
    assertThat(trigger.isTriggered(10.)).isFalse();
    assertThat(trigger.isTriggered(9.)).isTrue();
    assertThat(trigger.isTriggered(11.)).isFalse();
    assertThat(trigger.isTriggered(10.)).isFalse();
    assertThat(trigger.isTriggered(5.)).isTrue();
  }

  @Test
  public void testEquals() {
    SensorTrigger first =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    SensorTrigger second =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    assertThat(first.userSettingsEquals(second)).isTrue();
    first.setLastUsed(2L);
    assertThat(first.userSettingsEquals(second)).isTrue();

    second =
        SensorTrigger.newTrigger(
            "otherSensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    assertThat(first.userSettingsEquals(second)).isFalse();

    second =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_RISES_ABOVE,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            10.);
    assertThat(first.userSettingsEquals(second)).isFalse();

    second =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_DROPS_BELOW,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_STOP_RECORDING,
            10.);
    assertThat(first.userSettingsEquals(second)).isFalse();

    second =
        SensorTrigger.newTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_RISES_ABOVE,
            GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType
                .TRIGGER_ACTION_START_RECORDING,
            11.);
    assertThat(first.userSettingsEquals(second)).isFalse();

    // Check ordering
    first =
        SensorTrigger.newAlertTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            ImmutableSet.of(
                TriggerAlertType.TRIGGER_ALERT_VISUAL, TriggerAlertType.TRIGGER_ALERT_AUDIO),
            10.);
    second =
        SensorTrigger.newAlertTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            ImmutableSet.of(
                TriggerAlertType.TRIGGER_ALERT_AUDIO, TriggerAlertType.TRIGGER_ALERT_VISUAL),
            10.);
    assertThat(first.userSettingsEquals(second)).isTrue();

    second =
        SensorTrigger.newAlertTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            ImmutableSet.of(TriggerAlertType.TRIGGER_ALERT_AUDIO),
            10.);
    assertThat(first.userSettingsEquals(second)).isFalse();

    first =
        SensorTrigger.newNoteTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            "text",
            10.);
    assertThat(first.userSettingsEquals(second)).isFalse();
    second =
        SensorTrigger.newNoteTypeTrigger(
            "sensorId",
            GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen.TRIGGER_WHEN_AT,
            "text",
            10.);
    assertThat(first.userSettingsEquals(second)).isTrue();

    second.setNoteText("new");
    assertThat(first.userSettingsEquals(second)).isFalse();
  }
}
