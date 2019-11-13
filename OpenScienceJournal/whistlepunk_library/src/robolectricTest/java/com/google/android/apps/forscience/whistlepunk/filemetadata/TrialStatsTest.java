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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorStat.StatType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorTrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorTrialStats.StatStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the TrialStats class. */
@RunWith(RobolectricTestRunner.class)
public class TrialStatsTest {
  @Test
  public void testGetAndSet() {
    TrialStats stats = new TrialStats("sensorId");
    stats.setStatStatus(StatStatus.VALID);
    stats.putStat(StatType.AVERAGE, 42);
    stats.putStat(StatType.MINIMUM, 10);

    assertTrue(stats.hasStat(StatType.AVERAGE));
    assertEquals(stats.getStatValue(StatType.AVERAGE, 0), 42.0);
    assertEquals(stats.getStatValue(StatType.MINIMUM, 0), 10.0);

    // New values overwrite old values
    stats.putStat(StatType.AVERAGE, 41);
    assertEquals(stats.getStatValue(StatType.AVERAGE, 0), 41.0);

    // Check that missing information works properly.
    assertFalse(stats.hasStat(StatType.MAXIMUM));
    // Test default too
    assertEquals(stats.getStatValue(StatType.MAXIMUM, 52.0), 52.0);

    // Put another one, just for fun.
    stats.putStat(StatType.MAXIMUM, 52);
    assertEquals(stats.getStatValue(StatType.MAXIMUM, 0), 52.0);
  }

  @Test
  public void testCopy() {
    SensorTrialStats sensorTrialStats =
        GoosciTrial.SensorTrialStats.newBuilder()
            .setSensorId("sensorId")
            .setStatStatus(StatStatus.NEEDS_UPDATE)
            .build();
    TrialStats initial = new TrialStats(sensorTrialStats);

    TrialStats other = new TrialStats("sensorId");
    other.setStatStatus(StatStatus.VALID);

    assertFalse(initial.statsAreValid());
    initial.copyFrom(other);
    assertTrue(initial.statsAreValid());
  }
}
