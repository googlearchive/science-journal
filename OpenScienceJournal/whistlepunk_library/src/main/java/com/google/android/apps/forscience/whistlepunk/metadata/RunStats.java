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

package com.google.android.apps.forscience.whistlepunk.metadata;

import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorStat.StatType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorTrialStats.StatStatus;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ZoomRecorder;
import com.google.common.collect.ImmutableMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Metadata object for the stats stored along with a run. This object is to convert TrialStats to
 * key/value pairs which can be read and written to the database.
 */
public class RunStats {
  private static final int DEFAULT_VALUE = 0;
  private static final Map<String, StatType> KEY_MAP =
      ImmutableMap.<String, StatType>builder()
          .put(StatsAccumulator.KEY_AVERAGE, StatType.AVERAGE)
          .put(StatsAccumulator.KEY_MIN, StatType.MINIMUM)
          .put(StatsAccumulator.KEY_MAX, StatType.MAXIMUM)
          .put(StatsAccumulator.KEY_NUM_DATA_POINTS, StatType.NUM_DATA_POINTS)
          .put(StatsAccumulator.KEY_TOTAL_DURATION, StatType.TOTAL_DURATION)
          .put(ZoomRecorder.STATS_KEY_TIER_COUNT, StatType.ZOOM_PRESENTER_TIER_COUNT)
          .put(
              ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS,
              StatType.ZOOM_PRESENTER_ZOOM_LEVEL_BETWEEN_TIERS)
          .build();

  private TrialStats trialStats;

  public static RunStats fromTrialStats(TrialStats trialStats) {
    RunStats runStats = new RunStats(trialStats);
    return runStats;
  }

  private RunStats(TrialStats trialStats) {
    this.trialStats = trialStats;
  }

  public RunStats(String sensorId) {
    trialStats = new TrialStats(sensorId);
  }

  public TrialStats getTrialStats() {
    return trialStats;
  }

  public void putStat(String key, double value) {
    StatType type = keyToType(key);
    trialStats.putStat(type, value);
  }

  public double getStat(String key) {
    return getStat(key, DEFAULT_VALUE);
  }

  public double getStat(String key, double defaultValue) {
    return trialStats.getStatValue(keyToType(key), defaultValue);
  }

  public boolean hasStat(String key) {
    return trialStats.hasStat(keyToType(key));
  }

  public int getStatus() {
    if (trialStats.statsAreValid()) {
      return StatsAccumulator.STATUS_VALID;
    }
    return StatsAccumulator.STATUS_NEEDS_UPDATE;
  }

  public void setStatus(int newStatus) {
    if (newStatus == StatsAccumulator.STATUS_NEEDS_UPDATE) {
      trialStats.setStatStatus(StatStatus.NEEDS_UPDATE);
    } else if (newStatus == StatsAccumulator.STATUS_VALID) {
      trialStats.setStatStatus(StatStatus.VALID);
    }
  }

  public Set<String> getKeys() {
    Set<String> result = new HashSet<>();
    for (String key : KEY_MAP.keySet()) {
      if (trialStats.hasStat(keyToType(key))) {
        result.add(key);
      }
    }
    return result;
  }

  private static StatType keyToType(String key) {
    if (KEY_MAP.containsKey(key)) {
      return KEY_MAP.get(key);
    }
    throw new IllegalArgumentException("StatType not found for key " + key);
  }
}
