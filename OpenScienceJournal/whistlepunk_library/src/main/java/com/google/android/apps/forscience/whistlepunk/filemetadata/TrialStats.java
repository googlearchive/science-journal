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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorStat;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorStat.StatType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorTrialStats.StatStatus;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial.SensorTrialStats;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs.Destination;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Metadata object for the stats stored along with a trial */
public class TrialStats {
  private GoosciTrial.SensorTrialStats trialStats;

  public static Map<String, TrialStats> fromTrial(GoosciTrial.Trial trial) {
    Map<String, TrialStats> result = new HashMap<>();
    for (GoosciTrial.SensorTrialStats stats : trial.trialStats) {
      result.put(stats.sensorId, new TrialStats(stats));
    }
    return result;
  }

  public TrialStats(String sensorId) {
    @MigrateAs(Destination.BUILDER)
    SensorTrialStats sensorTrialStats = new GoosciTrial.SensorTrialStats();
    sensorTrialStats.sensorId = sensorId;
    trialStats = sensorTrialStats;
  }

  public TrialStats(GoosciTrial.SensorTrialStats trialStats) {
    this.trialStats = trialStats;
  }

  public GoosciTrial.SensorTrialStats getSensorTrialStatsProto() {
    return trialStats;
  }

  public void copyFrom(TrialStats other) {
    trialStats = other.getSensorTrialStatsProto();
  }

  public String getSensorId() {
    return trialStats.sensorId;
  }

  public boolean statsAreValid() {
    return trialStats.statStatus == StatStatus.VALID;
  }

  public void setStatStatus(StatStatus status) {
    trialStats.statStatus = status;
  }

  public void putStat(StatType type, double value) {
    for (int i = 0; i < trialStats.sensorStats.length; i++) {
      if (trialStats.sensorStats[i].getStatType() == type) {
        trialStats.sensorStats[i] =
            trialStats.sensorStats[i].toBuilder().setStatValue(value).build();
        return;
      }
    }
    int newSize = trialStats.sensorStats.length + 1;
    trialStats.sensorStats = Arrays.copyOf(trialStats.sensorStats, newSize);
    trialStats.sensorStats[newSize - 1] =
        SensorStat.newBuilder().setStatType(type).setStatValue(value).build();
  }

  public double getStatValue(StatType type, double defaultValue) {
    for (SensorStat sensorStat : trialStats.sensorStats) {
      if (sensorStat.getStatType() == type) {
        return sensorStat.getStatValue();
      }
    }
    return defaultValue;
  }

  public boolean hasStat(StatType type) {
    for (SensorStat sensorStat : trialStats.sensorStats) {
      if (sensorStat.getStatType() == type) {
        return true;
      }
    }
    return false;
  }
}
