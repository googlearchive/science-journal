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

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.*;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.util.ArrayList;

/**
 * Tests for the Trial class.
 */
public class TrialUnitTest {

    private Trial makeSimpleTrial(long startTime, String sensorId) {
        GoosciSensorLayout.SensorLayout[] layouts = new GoosciSensorLayout.SensorLayout[]{
                new GoosciSensorLayout.SensorLayout()};
        layouts[0].sensorId =  sensorId;
        return Trial.newTrial(startTime, layouts);
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
        Trial first = Trial.newTrial(10, new GoosciSensorLayout.SensorLayout[0]);
        Trial second = Trial.newTrial(10, new GoosciSensorLayout.SensorLayout[0]);
        assertNotEquals(first.getTrialId(), second.getTrialId());

        Trial firstAgain = Trial.fromTrial(first.getTrialProto());
        assertEquals(first.getTrialId(), firstAgain.getTrialId());
    }

    @Test
    public void testElapsedSeconds() {
        Trial trial = Trial.newTrial(7, new GoosciSensorLayout.SensorLayout[0]);
        assertEquals(0, trial.elapsedSeconds());

        trial.setRecordingEndTime(5007);
        assertEquals(5, trial.elapsedSeconds());
    }
}
