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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.MemoryAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.EmptySensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.FakeUnitAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

/**
 * Tests for the Trial class.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class TrialUnitTest {
    private static final GoosciSensorLayout.SensorLayout[]
            NO_LAYOUTS = new GoosciSensorLayout.SensorLayout[0];
    private SensorAppearanceProvider mFakeProvider = new FakeUnitAppearanceProvider();

    private Trial makeSimpleTrial(long startTime, String sensorId) {
        GoosciSensorLayout.SensorLayout[] layouts = new GoosciSensorLayout.SensorLayout[]{
                new GoosciSensorLayout.SensorLayout()};
        layouts[0].sensorId = sensorId;
        return Trial.newTrial(startTime, layouts, mFakeProvider, null);
    }

    @Test
    public void testTimestamps() {
        Trial trial = makeSimpleTrial(1000, "sensorId");
        trial.setRecordingEndTime(2000);

        assertEquals(trial.getOriginalFirstTimestamp(), 1000);
        assertEquals(trial.getOriginalLastTimestamp(), 2000);
        assertEquals(trial.getFirstTimestamp(), 1000);
        assertEquals(trial.getLastTimestamp(), 2000);
        assertEquals(trial.elapsedSeconds(), 1);
        assertTrue(trial.isValid());
    }

    @Test
    public void testTimestampsWithCrop() {
        Trial trial = makeSimpleTrial(1000, "sensorId");
        trial.setRecordingEndTime(4000);

        GoosciTrial.Range cropRange = new GoosciTrial.Range();
        cropRange.startMs = 2000;
        cropRange.endMs = 3000;
        trial.setCropRange(cropRange);

        assertEquals(trial.getOriginalFirstTimestamp(), 1000);
        assertEquals(trial.getOriginalLastTimestamp(), 4000);
        assertEquals(trial.getFirstTimestamp(), 2000);
        assertEquals(trial.getLastTimestamp(), 3000);
        assertEquals(trial.elapsedSeconds(), 1);
        assertTrue(trial.isValid());
    }

    @Test
    public void testInvalidTrial() {
        Trial trial = makeSimpleTrial(1000, "sensorId");
        assertFalse(trial.isValid());

        trial.setRecordingEndTime(999);
        assertFalse(trial.isValid());
        assertEquals(trial.elapsedSeconds(), 0);

        trial.setRecordingEndTime(2000);
        assertTrue(trial.isValid());
        assertEquals(trial.elapsedSeconds(), 1);
    }

    @Test
    public void testGetSensorInfo() {
        Trial trial = makeSimpleTrial(1000, "sensorId");
        trial.setRecordingEndTime(2000);
        assertTrue(trial.getSensorIds().contains("sensorId"));
    }

    @Test
    public void testStats() {
        Trial trial = makeSimpleTrial(1000, "sensorId");
        TrialStats stats = new TrialStats("sensorId");
        stats.setStatStatus(GoosciTrial.SensorTrialStats.VALID);
        stats.putStat(GoosciTrial.SensorStat.AVERAGE, 42);
        trial.setStats(stats);

        assertTrue(trial.getStatsForSensor("sensorId").statsAreValid());

        // Test replace works
        TrialStats newStats = new TrialStats("sensorId");
        newStats.setStatStatus(GoosciTrial.SensorTrialStats.NEEDS_UPDATE);
        newStats.putStat(GoosciTrial.SensorStat.AVERAGE, 42);
        trial.setStats(newStats);

        assertFalse(trial.getStatsForSensor("sensorId").statsAreValid());
    }

    @Test
    public void testUniqueIds() {
        Trial first = Trial.newTrial(10, NO_LAYOUTS, mFakeProvider, null);
        Trial second = Trial.newTrial(10, NO_LAYOUTS, mFakeProvider, null);
        assertNotEquals(first.getTrialId(), second.getTrialId());

        Trial firstAgain = Trial.fromTrial(first.getTrialProto());
        assertEquals(first.getTrialId(), firstAgain.getTrialId());
    }

    @Test
    public void testElapsedSeconds() {
        Trial trial = Trial.newTrial(7, NO_LAYOUTS, mFakeProvider, null);
        assertEquals(0, trial.elapsedSeconds());

        trial.setRecordingEndTime(5007);
        assertEquals(5, trial.elapsedSeconds());
    }

    @Test
    public void testNewTrialWithAppearances() {
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "foo";
        MemoryAppearanceProvider provider = new MemoryAppearanceProvider();
        provider.putAppearance(layout.sensorId, new EmptySensorAppearance() {
            @Override
            public String getName(Context context) {
                return "Fun name!";
            }
        });
        Trial trial =
                Trial.newTrial(7, new GoosciSensorLayout.SensorLayout[]{layout}, provider, null);
        Map<String, GoosciSensorAppearance.BasicSensorAppearance> appearances =
                trial.getAppearances();
        assertEquals("Fun name!", appearances.get("foo").name);
    }
}
