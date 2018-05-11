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

import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
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
    trialStats = new GoosciTrial.SensorTrialStats();
    trialStats.sensorId = sensorId;
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
    return trialStats.statStatus == GoosciTrial.SensorTrialStats.StatStatus.VALID;
  }

  public void setStatStatus(int status) {
    trialStats.statStatus = status;
  }

  public void putStat(int type, double value) {
    for (GoosciTrial.SensorStat sensorStat : trialStats.sensorStats) {
      if (sensorStat.statType == type) {
        sensorStat.statValue = value;
        return;
      }
    }
    int newSize = trialStats.sensorStats.length + 1;
    trialStats.sensorStats = Arrays.copyOf(trialStats.sensorStats, newSize);
    GoosciTrial.SensorStat newStat = new GoosciTrial.SensorStat();
    newStat.statType = type;
    newStat.statValue = value;
    trialStats.sensorStats[newSize - 1] = newStat;
  }

  public double getStatValue(int type, double defaultValue) {
    for (GoosciTrial.SensorStat sensorStat : trialStats.sensorStats) {
      if (sensorStat.statType == type) {
        return sensorStat.statValue;
      }
    }
    return defaultValue;
  }

  public boolean hasStat(int type) {
    for (GoosciTrial.SensorStat sensorStat : trialStats.sensorStats) {
      if (sensorStat.statType == type) {
        return true;
      }
    }
    return false;
  }
}
