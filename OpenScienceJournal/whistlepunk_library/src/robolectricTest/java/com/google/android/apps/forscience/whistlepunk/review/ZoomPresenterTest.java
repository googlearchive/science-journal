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

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.FakeAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ZoomPresenterTest {

  private final InMemorySensorDatabase database = new InMemorySensorDatabase();
  private final MemoryMetadataManager metadataManager = new MemoryMetadataManager();

  @Test
  public void testTierZeroWhenNotManyDataPoints() {
    int perZoomLevel = 20;

    TrialStats stats = new TrialStats("sensorId");
    stats.putStat(GoosciTrial.SensorStat.StatType.TOTAL_DURATION, 99);
    stats.putStat(GoosciTrial.SensorStat.StatType.NUM_DATA_POINTS, 100);
    stats.putStat(GoosciTrial.SensorStat.StatType.ZOOM_PRESENTER_TIER_COUNT, 2);
    stats.putStat(
        GoosciTrial.SensorStat.StatType.ZOOM_PRESENTER_ZOOM_LEVEL_BETWEEN_TIERS, perZoomLevel);

    ZoomPresenter zp = new ZoomPresenter(200);
    zp.setRunStats(stats);
    assertEquals(0, zp.updateTier(5));
  }

  @Test
  public void testTierTwoWhenLotsOfDataPoints() {
    int perZoomLevel = 5;
    ManualSensor sensor = new ManualSensor("test", 1000, perZoomLevel);
    SensorRecorder recorder = createRecorder(sensor);
    Trial trial =
        Trial.newTrial(
            0,
            new GoosciSensorLayout.SensorLayout[0],
            new FakeAppearanceProvider(),
            RuntimeEnvironment.application.getApplicationContext());
    sensor.pushDataPoints(recorder, 100, trial);

    int howManyDesiredDataPoints = 4;

    ZoomPresenter zp = new ZoomPresenter(howManyDesiredDataPoints);
    zp.setRunStats(trial.getStatsForSensor(sensor.getId()));
    assertEquals(2, zp.updateTier(100));
  }

  @Test
  public void testTierZeroWhenNoTierStats() {
    // We have none of the tier information we need
    TrialStats stats = new TrialStats("sensorId");

    int howManyDesiredDataPoints = 4;

    ZoomPresenter zp = new ZoomPresenter(howManyDesiredDataPoints);
    zp.setRunStats(stats);
    assertEquals(0, zp.updateTier(100));
  }

  @Test
  public void testWantMoreTiersThanWeHave() {
    TrialStats stats = new TrialStats("sensorId");
    stats.putStat(GoosciTrial.SensorStat.StatType.TOTAL_DURATION, 99);
    stats.putStat(GoosciTrial.SensorStat.StatType.NUM_DATA_POINTS, 100);
    stats.putStat(GoosciTrial.SensorStat.StatType.ZOOM_PRESENTER_TIER_COUNT, 5);
    stats.putStat(GoosciTrial.SensorStat.StatType.ZOOM_PRESENTER_ZOOM_LEVEL_BETWEEN_TIERS, 5);

    // This is the ideal tier level
    assertEquals(2, ZoomPresenter.computeTier(-1, 4, stats, 100));

    // Now, we have fewer tiers than we wish
    stats.putStat(GoosciTrial.SensorStat.StatType.ZOOM_PRESENTER_TIER_COUNT, 2);
    assertEquals(1, ZoomPresenter.computeTier(-1, 4, stats, 100));
  }

  @Test
  public void testBiasToCurrentTier() {
    TrialStats stats = new TrialStats("sensorId");
    stats.putStat(GoosciTrial.SensorStat.StatType.TOTAL_DURATION, 99);
    stats.putStat(GoosciTrial.SensorStat.StatType.NUM_DATA_POINTS, 100);
    stats.putStat(GoosciTrial.SensorStat.StatType.ZOOM_PRESENTER_TIER_COUNT, 5);
    stats.putStat(GoosciTrial.SensorStat.StatType.ZOOM_PRESENTER_ZOOM_LEVEL_BETWEEN_TIERS, 5);

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
    return sensor.createRecorder(
        RuntimeEnvironment.application.getApplicationContext(),
        database.makeSimpleRecordingController(metadataManager),
        new RecordingSensorObserver());
  }
}
