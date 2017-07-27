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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for the TriggerHelper class.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class TriggerHelperTest {
    @Test
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

    @Test
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
