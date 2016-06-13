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
    private static final Runnable NOOP = new Runnable() {
        @Override
        public void run() {

        }
    };

    private final InMemorySensorDatabase mDatabase = new InMemorySensorDatabase();
    private final MemoryMetadataManager mMetadataManager = new MemoryMetadataManager();

    public void testTierZeroWhenNotManyDataPoints() {
        int perZoomLevel = 20;
        ManualSensor sensor = new ManualSensor("test", 20, perZoomLevel);
        sensor.createRecordingPresenter(getContext(),
                mDatabase.makeSimpleRecordingController(mMetadataManager), "runId");

        for (int i = 0; i < 100; i++) {
            mDatabase.addScalarReading("test", 0, i, i);
            if (i % 10 == 0) {
                mDatabase.addScalarReading("test", 1, i, i);
            }
        }

        RunStats stats = new RunStats();
        stats.putStat(StatsAccumulator.KEY_TOTAL_DURATION, 99);
        stats.putStat(StatsAccumulator.KEY_NUM_DATA_POINTS, 100);
        stats.putStat(ZoomRecorder.STATS_KEY_TIER_COUNT, 2);
        stats.putStat(ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS, perZoomLevel);

        ChartController ctrl = sensor.getChartController();
        ZoomPresenter zp = new ZoomPresenter(ctrl, mDatabase.makeSimpleController(mMetadataManager),
                200, makeFailureListener());
        zp.loadInitialReadings(0, 100, stats, NOOP, NOOP, "test");
        assertEquals(100, sensor.getLineData().size());
    }

    public void testTierTwoWhenLotsOfDataPoints() {
        int perZoomLevel = 5;
        ManualSensor sensor = new ManualSensor("test", 1000, perZoomLevel);
        SensorRecorder recorder = createRecorder(sensor);
        sensor.pushDataPoints(recorder, 100);

        RunStats stats = mMetadataManager.getStats("runId", "test");

        int howManyDesiredDataPoints = 4;

        ZoomPresenter zp = new ZoomPresenter(sensor.getChartController(),
                mDatabase.makeSimpleController(mMetadataManager), howManyDesiredDataPoints,
                makeFailureListener());
        zp.loadInitialReadings(0, 100, stats, NOOP, NOOP, "test");
        assertEquals(4, sensor.getLineData().size());
    }

    public void testTierZeroWhenNoTierStats() {
        int perZoomLevel = 5;
        ManualSensor sensor = new ManualSensor("test", 1000, perZoomLevel);
        SensorRecorder recorder = createRecorder(sensor);
        sensor.pushDataPoints(recorder, 100);

        // We have none of the tier information we need
        RunStats stats = new RunStats();

        int howManyDesiredDataPoints = 4;

        ZoomPresenter zp = new ZoomPresenter(sensor.getChartController(),
                mDatabase.makeSimpleController(mMetadataManager), howManyDesiredDataPoints,
                makeFailureListener());

        // While we're here, check that null == NOOP
        zp.loadInitialReadings(0, 100, stats, null, null, "test");
        assertEquals(100, sensor.getLineData().size());
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
        ManualSensor sensor = new ManualSensor("test", 1000, 5);
        sensor.createRecordingPresenter(getContext(),
                mDatabase.makeSimpleRecordingController(mMetadataManager), "runId");
        SensorRecorder recorder = createRecorder(sensor);
        sensor.pushDataPoints(recorder, 100);

        RunStats stats = new RunStats();
        stats.putStat(StatsAccumulator.KEY_TOTAL_DURATION, 99);
        stats.putStat(StatsAccumulator.KEY_NUM_DATA_POINTS, 100);
        stats.putStat(ZoomRecorder.STATS_KEY_TIER_COUNT, 5);
        stats.putStat(ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS, 5);

        ZoomPresenter zp = new ZoomPresenter(sensor.getChartController(),
                mDatabase.makeSimpleController(mMetadataManager), 20,
                makeFailureListener());

        // Establish the ideal fractional tiers
        assertEquals(1.006, ZoomPresenter.computeIdealTier(20, stats, 100), 0.01);
        assertEquals(0.867, ZoomPresenter.computeIdealTier(20, stats, 80), 0.01);
        assertEquals(0.784, ZoomPresenter.computeIdealTier(20, stats, 70), 0.01);
        assertEquals(0.688, ZoomPresenter.computeIdealTier(20, stats, 60), 0.01);
        assertEquals(0.575, ZoomPresenter.computeIdealTier(20, stats, 50), 0.01);
        assertEquals(0.436, ZoomPresenter.computeIdealTier(20, stats, 40), 0.01);
        assertEquals(0.258, ZoomPresenter.computeIdealTier(20, stats, 30), 0.01);
        assertEquals(0.006, ZoomPresenter.computeIdealTier(20, stats, 20), 0.01);

        // Now test that we have to get more than 0.6 away from the previous tier before we will
        // swap to another tier (this is to prevent constant reloads if the user zooms back and
        // forth past the 0.5-point
        zp.loadInitialReadings(0, 100, stats, NOOP, NOOP, "test");
        assertEquals(1, zp.updateTier(100));
        assertEquals(1, zp.updateTier(80));
        assertEquals(1, zp.updateTier(70));
        assertEquals(1, zp.updateTier(60));
        assertEquals(1, zp.updateTier(50));
        assertEquals(1, zp.updateTier(40));
        assertEquals(0, zp.updateTier(30));
        assertEquals(0, zp.updateTier(40));
        assertEquals(0, zp.updateTier(50));
        assertEquals(1, zp.updateTier(60));
    }

    public void testRescaleOnResize() {
        int perZoomLevel = 5;
        ManualSensor sensor = new ManualSensor("test", 1000, perZoomLevel);
        SensorRecorder recorder = createRecorder(sensor);
        sensor.pushDataPoints(recorder, 100);

        RunStats stats = mMetadataManager.getStats("runId", "test");

        int howManyDesiredDataPoints = 20;

        ZoomPresenter zp = new ZoomPresenter(sensor.getChartController(),
                mDatabase.makeSimpleController(mMetadataManager), howManyDesiredDataPoints,
                makeFailureListener());
        zp.loadInitialReadings(0, 100, stats, NOOP, NOOP, "test");
        assertEquals(20, sensor.getLineData().size());

        // Rescale a little bit, just re-use current data
        zp.setXAxis(0, 80);
        assertEquals(20, sensor.getLineData().size());

        // Rescale a lot, load at new resolution
        zp.setXAxis(0, 20);

        TestData data = TestData.allPointsBetween(0, 20, 1);
        data.checkRawData(sensor.getLineData());

        // Pan a bit, load some more data
        zp.setXAxis(10, 30);
        TestData.allPointsBetween(0, 30, 1).checkRawData(sensor.getLineData());

        // Zoom back out, reload again
        zp.setXAxis(0, 80);
        TestData data2 = new TestData();
        for (int i = 0; i < 80; i += 10) {
            data2.addPoint(i, i);
            data2.addPoint(i + 9, i + 9);
        }
        data2.addPoint(80, 80);
        data2.checkRawData(sensor.getLineData());
    }

    public void testDontCrashIfXAxisSetBeforeLoad() {
        int perZoomLevel = 5;
        ManualSensor sensor = new ManualSensor("test", 1000, perZoomLevel);
        SensorRecorder recorder = createRecorder(sensor);
        sensor.pushDataPoints(recorder, 100);

        int howManyDesiredDataPoints = 20;
        ZoomPresenter zp = new ZoomPresenter(sensor.getChartController(),
                mDatabase.makeSimpleController(mMetadataManager), howManyDesiredDataPoints,
                makeFailureListener());
        zp.setXAxis(0, 100);
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