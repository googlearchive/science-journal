package com.google.android.apps.forscience.whistlepunk.metadata;

import android.test.AndroidTestCase;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;

/**
 * Tests for the SensorLayoutTriggerUtils class.
 */
public class SensorLayoutTriggerUtilsTest extends AndroidTestCase {

    public void testAddTrigger() {
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        SensorLayoutTriggerUtils.addTriggerToLayoutActiveTriggers(layout, "triggerId");
        assertEquals(layout.activeSensorTriggerIds.length, 1);

        SensorLayoutTriggerUtils.addTriggerToLayoutActiveTriggers(layout, "triggerId2");
        assertEquals(layout.activeSensorTriggerIds.length, 2);

        // Doesn't double-add
        SensorLayoutTriggerUtils.addTriggerToLayoutActiveTriggers(layout, "triggerId2");
        assertEquals(layout.activeSensorTriggerIds.length, 2);
    }

    public void testRemoveTrigger() {
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();

        // No error if it doesn't exist
        SensorLayoutTriggerUtils.removeTriggerFromLayoutActiveTriggers(layout, "triggerId");
        assertEquals(layout.activeSensorTriggerIds.length, 0);

        SensorLayoutTriggerUtils.addTriggerToLayoutActiveTriggers(layout, "triggerId");
        SensorLayoutTriggerUtils.addTriggerToLayoutActiveTriggers(layout, "triggerId2");
        SensorLayoutTriggerUtils.addTriggerToLayoutActiveTriggers(layout, "triggerId3");
        assertEquals(layout.activeSensorTriggerIds.length, 3);

        // It deletes the right one
        SensorLayoutTriggerUtils.removeTriggerFromLayoutActiveTriggers(layout, "triggerId");
        assertEquals(layout.activeSensorTriggerIds.length, 2);
        for (int i = 0; i < layout.activeSensorTriggerIds.length; i++) {
            assertFalse(TextUtils.equals(layout.activeSensorTriggerIds[i], "triggerId"));
        }
    }
}
