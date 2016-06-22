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

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;

public class StatsAccumulatorTest extends AndroidTestCase {
    public void testAllStats() {
        StatsAccumulator acc = new StatsAccumulator();
        acc.updateRecordingStreamStats(0, 0);
        acc.updateRecordingStreamStats(1, 1);
        acc.updateRecordingStreamStats(2, 2);
        RunStats stats = acc.makeSaveableStats();
        assertEquals(0.0, stats.getStat(StatsAccumulator.KEY_MIN), 0.001);
        assertEquals(1.0, stats.getStat(StatsAccumulator.KEY_AVERAGE), 0.001);
        assertEquals(2.0, stats.getStat(StatsAccumulator.KEY_MAX), 0.001);
        assertEquals(3.0, stats.getStat(StatsAccumulator.KEY_NUM_DATA_POINTS), 0.001);
        assertEquals(2.0, stats.getStat(StatsAccumulator.KEY_TOTAL_DURATION), 0.001);
    }
}