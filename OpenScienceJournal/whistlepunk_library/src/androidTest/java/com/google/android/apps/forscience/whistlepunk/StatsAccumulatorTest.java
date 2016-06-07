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