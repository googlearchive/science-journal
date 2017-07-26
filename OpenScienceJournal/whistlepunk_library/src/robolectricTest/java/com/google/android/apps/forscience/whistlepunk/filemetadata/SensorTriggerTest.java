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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation
        .TriggerInformation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for the SensorTrigger class.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class SensorTriggerTest {
    @Test
    public void testSetActionType_noChangeWhenEqual() {
        SensorTrigger trigger = SensorTrigger.newTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        SensorTrigger same = SensorTrigger.newTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        trigger.setTriggerActionType(TriggerInformation.TRIGGER_ACTION_START_RECORDING);
        assertTrue(trigger.userSettingsEquals(same));

        trigger = SensorTrigger.newAlertTypeTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_VISUAL,
                        TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        same = SensorTrigger.newAlertTypeTrigger("sensorId",
                TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_VISUAL,
                        TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        trigger.setTriggerActionType(TriggerInformation.TRIGGER_ACTION_ALERT);
        assertTrue(trigger.userSettingsEquals(same));
    }

    @Test
    public void testSetActionType_clearsMetaDataWhenChanged() {
        SensorTrigger trigger = SensorTrigger.newAlertTypeTrigger("1",
                TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_VISUAL,
                        TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        assertTrue(trigger.getNoteText().isEmpty());
        assertEquals(trigger.getAlertTypes().length, 2);

        trigger.setTriggerActionType(TriggerInformation.TRIGGER_ACTION_NOTE);
        assertTrue(trigger.getNoteText().isEmpty());
        assertEquals(trigger.getAlertTypes().length, 0);

        trigger = SensorTrigger.newNoteTypeTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                "text", 10.);
        assertTrue(trigger.getNoteText().equals("text"));
        assertEquals(trigger.getAlertTypes().length, 0);

        trigger.setTriggerActionType(TriggerInformation.TRIGGER_ACTION_START_RECORDING);
        assertTrue(trigger.getNoteText().isEmpty());
        assertEquals(trigger.getAlertTypes().length, 0);
    }

    @Test
    public void testIsTriggered_datapointAtUnequal() {
        SensorTrigger trigger = SensorTrigger.newTrigger("sensorId",
                TriggerInformation.TRIGGER_WHEN_AT,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertFalse(trigger.isTriggered(9.));
        assertTrue(trigger.isTriggered(10.));
    }

    @Test
    public void testIsTriggered_datapointAtEquals() {
        SensorTrigger trigger = SensorTrigger.newTrigger("sensorId",
                TriggerInformation.TRIGGER_WHEN_AT,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        // Never trigger on the first point.
        assertFalse(trigger.isTriggered(10.));

        // Fire if the value is equal.
        assertTrue(trigger.isTriggered(10.));

        // Fire when reaching the value from below.
        assertFalse(trigger.isTriggered(9.));
        assertTrue(trigger.isTriggered(10.));

        // Fire when reaching the value from above.
        assertFalse(trigger.isTriggered(11.));
        assertTrue(trigger.isTriggered(10.));

        // Fire when crossing from above to below.
        assertFalse(trigger.isTriggered(11.));
        assertTrue(trigger.isTriggered(9.));
    }

    @Test
    public void testIsTriggered_rising() {
        SensorTrigger trigger = SensorTrigger.newTrigger("sensorId",
                TriggerInformation.TRIGGER_WHEN_RISES_ABOVE,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        // The first point should aways be false because we have no comparison.
        assertFalse(trigger.isTriggered(10.));
        assertFalse(trigger.isTriggered(10.));
        assertFalse(trigger.isTriggered(9.));
        assertTrue(trigger.isTriggered(11.));
        assertFalse(trigger.isTriggered(10.));
        assertTrue(trigger.isTriggered(11.));
    }

    @Test
    public void testIsTriggered_falling() {
        SensorTrigger trigger = SensorTrigger.newTrigger("sensorId",
                TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        // The first point should aways be false because we have no comparison.
        assertFalse(trigger.isTriggered(10.));
        assertFalse(trigger.isTriggered(10.));
        assertTrue(trigger.isTriggered(9.));
        assertFalse(trigger.isTriggered(11.));
        assertFalse(trigger.isTriggered(10.));
        assertTrue(trigger.isTriggered(5.));
    }

    @Test
    public void testEquals() {
        SensorTrigger first = SensorTrigger.newTrigger("sensorId",
                TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        SensorTrigger second = SensorTrigger.newTrigger("sensorId",
                TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertTrue(first.userSettingsEquals(second));
        first.setLastUsed(2L);
        assertTrue(first.userSettingsEquals(second));

        second = SensorTrigger.newTrigger("otherSensorId",
                TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertFalse(first.userSettingsEquals(second));

        second = SensorTrigger.newTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_RISES_ABOVE,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertFalse(first.userSettingsEquals(second));

        second = SensorTrigger.newTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_STOP_RECORDING, 10.);
        assertFalse(first.userSettingsEquals(second));

        second = SensorTrigger.newTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_RISES_ABOVE,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 11.);
        assertFalse(first.userSettingsEquals(second));

        // Check ordering
        first = SensorTrigger.newAlertTypeTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_VISUAL,
                        TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        second = SensorTrigger.newAlertTypeTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_AUDIO,
                        TriggerInformation.TRIGGER_ALERT_VISUAL}, 10.);
        assertTrue(first.userSettingsEquals(second));

        second = SensorTrigger.newAlertTypeTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        assertFalse(first.userSettingsEquals(second));

        first = SensorTrigger.newNoteTypeTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                "text", 10.);
        assertFalse(first.userSettingsEquals(second));
        second = SensorTrigger.newNoteTypeTrigger("sensorId", TriggerInformation.TRIGGER_WHEN_AT,
                "text", 10.);
        assertTrue(first.userSettingsEquals(second));

        second.setNoteText("new");
        assertFalse(first.userSettingsEquals(second));
    }
}
