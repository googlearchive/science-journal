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

package com.google.android.apps.forscience.whistlepunk.review;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.ExplodingFactory;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.TestData;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ZoomRecorder;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;

public class ZoomPresenterTest extends AndroidTestCase {

    private final InMemorySensorDatabase mDatabase = new InMemorySensorDatabase();
    private final MemoryMetadataManager mMetadataManager = new MemoryMetadataManager();

    public void testTierZeroWhenNotManyDataPoints() {
        int perZoomLevel = 20;

        RunStats stats = new RunStats();
        stats.putStat(StatsAccumulator.KEY_TOTAL_DURATION, 99);
        stats.putStat(StatsAccumulator.KEY_NUM_DATA_POINTS, 100);
        stats.putStat(ZoomRecorder.STATS_KEY_TIER_COUNT, 2);
        stats.putStat(ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS, perZoomLevel);

        ZoomPresenter zp = new ZoomPresenter(200);
        zp.setRunStats(stats);
        assertEquals(0, zp.updateTier(5));
    }

    public void testTierTwoWhenLotsOfDataPoints() {
        int perZoomLevel = 5;
        ManualSensor sensor = new ManualSensor("test", 1000, perZoomLevel);
        SensorRecorder recorder = createRecorder(sensor);
        sensor.pushDataPoints(recorder, 100);

        RunStats stats = mMetadataManager.getStats("runId", "test");

        int howManyDesiredDataPoints = 4;

        ZoomPresenter zp = new ZoomPresenter(howManyDesiredDataPoints);
        zp.setRunStats(stats);
        assertEquals(2, zp.updateTier(100));
    }

    public void testTierZeroWhenNoTierStats() {
        // We have none of the tier information we need
        RunStats stats = new RunStats();

        int howManyDesiredDataPoints = 4;

        ZoomPresenter zp = new ZoomPresenter(howManyDesiredDataPoints);
        zp.setRunStats(stats);
        assertEquals(0, zp.updateTier(100));
    }

    public void testWantMoreTiersThanWeHave() {
        RunStats stats = new RunStats();
        stats.putStat(StatsAccumulator.KEY_TOTAL_DURATION, 99);
        stats.putStat(StatsAccumulator.KEY_NUM_DATA_POINTS, 100);
        stats.putStat(ZoomRecorder.STATS_KEY_TIER_COUNT, 5);
        stats.putStat(ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS, 5);

        // This is the ideal tier level
        assertEquals(2, ZoomPresenter.computeTier(-1, 4, stats, 100));

        // Now, we have fewer tiers than we wish
        stats.putStat(ZoomRecorder.STATS_KEY_TIER_COUNT, 2);
        assertEquals(1, ZoomPresenter.computeTier(-1, 4, stats, 100));
    }

    public void testBiasToCurrentTier() {
        RunStats stats = new RunStats();
        stats.putStat(StatsAccumulator.KEY_TOTAL_DURATION, 99);
        stats.putStat(StatsAccumulator.KEY_NUM_DATA_POINTS, 100);
        stats.putStat(ZoomRecorder.STATS_KEY_TIER_COUNT, 5);
        stats.putStat(ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS, 5);

        // Establish the ideal fractional tiers
        assertEquals(1.006, ZoomPresenter.computeIdealTier(20, stats, 100), 0.01);
        assertEquals(0.867, ZoomPresenter.computeIdealTier(20, stats, 80), 0.01);
        assertEquals(0.784, ZoomPresenter.computeIdealTier(20, stats, 70), 0.01);
        assertEquals(0.688, ZoomPresenter.computeIdealTier(20, stats, 60), 0.01);
        assertEquals(0.575, ZoomPresenter.computeIdealTier(20, stats, 50), 0.01);
        assertEquals(0.436, ZoomPresenter.computeIdealTier(20, stats, 40), 0.01);
        assertEquals(0.258, ZoomPresenter.computeIdealTier(20, stats, 30), 0.01);
        assertEquals(0.006, ZoomPresenter.computeIdealTier(20, stats, 20), 0.01);
    }

    private SensorRecorder createRecorder(ManualSensor sensor) {
        return sensor.createRecorder(getContext(),
                mDatabase.makeSimpleRecordingController(mMetadataManager),
                new RecordingSensorObserver());
    }

    private FailureListener makeFailureListener() {
        return new ExplodingFactory().makeListenerForOperation("load");
    }
}