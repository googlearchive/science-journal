package com.google.android.apps.forscience.whistlepunk.metadata;

import android.test.AndroidTestCase;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;

/**
 * Tests for the TriggerHelper class.
 */
public class TriggerHelperTest extends AndroidTestCase {

    public void testAddTrigger() {
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        TriggerHelper.addTriggerToLayoutActiveTriggers(layout, "triggerId");
        assertEquals(layout.activeSensorTriggerIds.length, 1);

        TriggerHelper.addTriggerToLayoutActiveTriggers(layout, "triggerId2");
        assertEquals(layout.activeSensorTriggerIds.length, 2);

        // Doesn't double-add
        TriggerHelper.addTriggerToLayoutActiveTriggers(layout, "triggerId2");
        assertEquals(layout.activeSensorTriggerIds.length, 2);
    }

    public void testRemoveTrigger() {
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();

        // No error if it doesn't exist
        TriggerHelper.removeTriggerFromLayoutActiveTriggers(layout, "triggerId");
        assertEquals(layout.activeSensorTriggerIds.length, 0);

        TriggerHelper.addTriggerToLayoutActiveTriggers(layout, "triggerId");
        TriggerHelper.addTriggerToLayoutActiveTriggers(layout, "triggerId2");
        TriggerHelper.addTriggerToLayoutActiveTriggers(layout, "triggerId3");
        assertEquals(layout.activeSensorTriggerIds.length, 3);

        // It deletes the right one
        TriggerHelper.removeTriggerFromLayoutActiveTriggers(layout, "triggerId");
        assertEquals(layout.activeSensorTriggerIds.length, 2);
        for (int i = 0; i < layout.activeSensorTriggerIds.length; i++) {
            assertFalse(TextUtils.equals(layout.activeSensorTriggerIds[i], "triggerId"));
        }
    }
}
