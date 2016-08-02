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

package com.google.android.apps.forscience.whistlepunk.metadata;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation;
import android.test.AndroidTestCase;

/**
 * Tests for the SensorTrigger class.
 */
public class SensorTriggerTest extends AndroidTestCase {

    public void testSetActionType_noChangeWhenEqual() {
        SensorTrigger trigger = new SensorTrigger("0", "fakeId", TriggerInformation.TRIGGER_WHEN_AT,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        SensorTrigger same = new SensorTrigger("0", "fakeId", TriggerInformation.TRIGGER_WHEN_AT,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        trigger.setTriggerActionType(TriggerInformation.TRIGGER_ACTION_START_RECORDING);
        assertTrue(trigger.userSettingsEquals(same));

        trigger = SensorTrigger.newAlertTypeTrigger("1", "fakeId",
                TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_VISUAL,
                        TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        same = SensorTrigger.newAlertTypeTrigger("1", "fakeId",
                TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_VISUAL,
                        TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        trigger.setTriggerActionType(TriggerInformation.TRIGGER_ACTION_ALERT);
        assertTrue(trigger.userSettingsEquals(same));
    }

    public void testSetActionType_clearsMetaDataWhenChanged() {
        SensorTrigger trigger = SensorTrigger.newAlertTypeTrigger("1", "fakeId",
                TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_VISUAL,
                        TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        assertTrue(trigger.getNoteText().isEmpty());
        assertEquals(trigger.getAlertTypes().length, 2);

        trigger.setTriggerActionType(TriggerInformation.TRIGGER_ACTION_NOTE);
        assertTrue(trigger.getNoteText().isEmpty());
        assertEquals(trigger.getAlertTypes().length, 0);

        trigger = SensorTrigger.newNoteTypeTrigger("7", "fakeId",
                TriggerInformation.TRIGGER_WHEN_AT, "text", 10.);
        assertTrue(trigger.getNoteText().equals("text"));
        assertEquals(trigger.getAlertTypes().length, 0);

        trigger.setTriggerActionType(TriggerInformation.TRIGGER_ACTION_START_RECORDING);
        assertTrue(trigger.getNoteText().isEmpty());
        assertEquals(trigger.getAlertTypes().length, 0);
    }

    public void testIsTriggered_datapointAtUnequal() {
        SensorTrigger trigger = new SensorTrigger("0", "fakeId", TriggerInformation.TRIGGER_WHEN_AT,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertFalse(trigger.isTriggered(9.));
        assertTrue(trigger.isTriggered(10.));
    }

    public void testIsTriggered_datapointAtEquals() {
        SensorTrigger trigger = new SensorTrigger("0", "fakeId", TriggerInformation.TRIGGER_WHEN_AT,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertFalse(trigger.isTriggered(10.));
        assertFalse(trigger.isTriggered(9.));
        assertTrue(trigger.isTriggered(10.));
    }

    public void testIsTriggered_rising() {
        SensorTrigger trigger = new SensorTrigger("0", "fakeId",
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

    public void testIsTriggered_falling() {
        SensorTrigger trigger = new SensorTrigger("0", "fakeId",
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

    public void testEquals() {
        SensorTrigger first = new SensorTrigger("0", "fakeId",
                TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        SensorTrigger second = new SensorTrigger("1", "fakeId",
                TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertTrue(first.userSettingsEquals(second));
        first.setLastUsed(2L);
        assertTrue(first.userSettingsEquals(second));

        second = new SensorTrigger("2", "differentId", TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertFalse(first.userSettingsEquals(second));

        second = new SensorTrigger("3", "fakeId", TriggerInformation.TRIGGER_WHEN_RISES_ABOVE,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 10.);
        assertFalse(first.userSettingsEquals(second));

        second = new SensorTrigger("3", "fakeId", TriggerInformation.TRIGGER_WHEN_DROPS_BELOW,
                TriggerInformation.TRIGGER_ACTION_STOP_RECORDING, 10.);
        assertFalse(first.userSettingsEquals(second));

        second = new SensorTrigger("3", "fakeId", TriggerInformation.TRIGGER_WHEN_RISES_ABOVE,
                TriggerInformation.TRIGGER_ACTION_START_RECORDING, 11.);
        assertFalse(first.userSettingsEquals(second));

        first = SensorTrigger.newAlertTypeTrigger("4", "fakeId", TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_VISUAL,
                        TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        second = SensorTrigger.newAlertTypeTrigger("5", "fakeId",
                TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_AUDIO,
                        TriggerInformation.TRIGGER_ALERT_VISUAL}, 10.);
        assertTrue(first.userSettingsEquals(second));

        second = SensorTrigger.newAlertTypeTrigger("6", "fakeId",
                TriggerInformation.TRIGGER_WHEN_AT,
                new int[]{TriggerInformation.TRIGGER_ALERT_AUDIO}, 10.);
        assertFalse(first.userSettingsEquals(second));

        first = SensorTrigger.newNoteTypeTrigger("7", "fakeId", TriggerInformation.TRIGGER_WHEN_AT,
                "text", 10.);
        assertFalse(first.userSettingsEquals(second));
        second = SensorTrigger.newNoteTypeTrigger("7", "fakeId", TriggerInformation.TRIGGER_WHEN_AT,
                "text", 10.);
        assertTrue(first.userSettingsEquals(second));

        second.setNoteText("new");
        assertFalse(first.userSettingsEquals(second));
    }
}
