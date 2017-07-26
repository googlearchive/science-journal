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
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for the TrialStats class.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class TrialStatsTest {
    @Test
    public void testGetAndSet() {
        TrialStats stats = new TrialStats("sensorId");
        stats.setStatStatus(GoosciTrial.SensorTrialStats.VALID);
        stats.putStat(GoosciTrial.SensorStat.AVERAGE, 42);
        stats.putStat(GoosciTrial.SensorStat.MINIMUM, 10);

        assertTrue(stats.hasStat(GoosciTrial.SensorStat.AVERAGE));
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.AVERAGE, 0), 42.0);
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.MINIMUM, 0), 10.0);

        // New values overwrite old values
        stats.putStat(GoosciTrial.SensorStat.AVERAGE, 41);
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.AVERAGE, 0), 41.0);

        // Check that missing information works properly.
        assertFalse(stats.hasStat(GoosciTrial.SensorStat.MAXIMUM));
        // Test default too
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, 52.0), 52.0);

        // Put another one, just for fun.
        stats.putStat(GoosciTrial.SensorStat.MAXIMUM, 52);
        assertEquals(stats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, 0), 52.0);
    }

    @Test
    public void testCopy() {
        GoosciTrial.SensorTrialStats sensorTrialStats = new GoosciTrial.SensorTrialStats();
        sensorTrialStats.sensorId = "sensorId";
        sensorTrialStats.statStatus = GoosciTrial.SensorTrialStats.NEEDS_UPDATE;
        TrialStats initial = new TrialStats(sensorTrialStats);

        TrialStats other = new TrialStats("sensorId");
        other.setStatStatus(GoosciTrial.SensorTrialStats.VALID);

        assertFalse(initial.statsAreValid());
        initial.copyFrom(other);
        assertTrue(initial.statsAreValid());
    }
}
