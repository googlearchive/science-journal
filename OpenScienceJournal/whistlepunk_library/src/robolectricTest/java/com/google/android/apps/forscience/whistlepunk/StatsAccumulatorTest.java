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

package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class StatsAccumulatorTest {
    @Test
    public void testAllStats() {
        StatsAccumulator acc = new StatsAccumulator("sensorId");
        acc.updateRecordingStreamStats(0, 0);
        acc.updateRecordingStreamStats(1, 1);
        acc.updateRecordingStreamStats(2, 2);
        TrialStats stats = acc.makeSaveableStats();
        assertEquals(0.0, stats.getStatValue(GoosciTrial.SensorStat.MINIMUM, -1), 0.001);
        assertEquals(1.0, stats.getStatValue(GoosciTrial.SensorStat.AVERAGE, -1), 0.001);
        assertEquals(2.0, stats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, -1), 0.001);
        assertEquals(3.0, stats.getStatValue(GoosciTrial.SensorStat.NUM_DATA_POINTS, -1), 0.001);
        assertEquals(2.0, stats.getStatValue(GoosciTrial.SensorStat.TOTAL_DURATION, -1), 0.001);
    }
}