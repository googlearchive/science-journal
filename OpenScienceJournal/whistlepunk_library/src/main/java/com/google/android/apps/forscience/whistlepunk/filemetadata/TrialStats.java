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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorStat;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorStat.StatType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorTrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorTrialStats.StatStatus;
import java.util.HashMap;
import java.util.Map;

/** Metadata object for the stats stored along with a trial */
public class TrialStats {
  private SensorTrialStats trialStats;

  public static Map<String, TrialStats> fromTrial(GoosciTrial.Trial trial) {
    Map<String, TrialStats> result = new HashMap<>();
    for (SensorTrialStats stats : trial.getTrialStatsList()) {
      result.put(stats.getSensorId(), new TrialStats(stats));
    }
    return result;
  }

  public TrialStats(String sensorId) {
    trialStats = SensorTrialStats.newBuilder().setSensorId(sensorId).build();
  }

  public TrialStats(SensorTrialStats trialStats) {
    this.trialStats = trialStats;
  }

  public SensorTrialStats getSensorTrialStatsProto() {
    return trialStats;
  }

  public void copyFrom(TrialStats other) {
    trialStats = other.getSensorTrialStatsProto();
  }

  public String getSensorId() {
    return trialStats.getSensorId();
  }

  public boolean statsAreValid() {
    return trialStats.getStatStatus() == StatStatus.VALID;
  }

  public void setStatStatus(StatStatus status) {
    trialStats = trialStats.toBuilder().setStatStatus(status).build();
  }

  public void putStat(StatType type, double value) {
    for (int i = 0; i < trialStats.getSensorStatsCount(); i++) {
      if (trialStats.getSensorStats(i).getStatType() == type) {
        SensorStat newSensorStat =
            trialStats.getSensorStats(i).toBuilder().setStatValue(value).build();
        trialStats = trialStats.toBuilder().setSensorStats(i, newSensorStat).build();
        return;
      }
    }

    trialStats =
        trialStats.toBuilder()
            .addSensorStats(SensorStat.newBuilder().setStatType(type).setStatValue(value))
            .build();
  }

  public double getStatValue(StatType type, double defaultValue) {
    for (SensorStat sensorStat : trialStats.getSensorStatsList()) {
      if (sensorStat.getStatType() == type) {
        return sensorStat.getStatValue();
      }
    }
    return defaultValue;
  }

  public boolean hasStat(StatType type) {
    for (SensorStat sensorStat : trialStats.getSensorStatsList()) {
      if (sensorStat.getStatType() == type) {
        return true;
      }
    }
    return false;
  }
}
