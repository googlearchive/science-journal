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

import androidx.annotation.IntDef;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorTrialStats;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of stats for a scalar value changing over time, and informs listeners when they
 * change.
 */
public class StatsAccumulator {
  public static final String KEY_MIN = "stats_min";
  public static final String KEY_MAX = "stats_max";
  public static final String KEY_AVERAGE = "stats_average";
  public static final String KEY_NUM_DATA_POINTS = "stats_count";
  public static final String KEY_TOTAL_DURATION = "stats_total_duration";
  public static final String KEY_STATUS = "status";

  @IntDef({STATUS_VALID, STATUS_NEEDS_UPDATE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface StatStatus {}

  public static final int STATUS_VALID = 0;
  public static final int STATUS_NEEDS_UPDATE = 1;

  public static class StatsDisplay {
    private List<StatsListener> statsListeners = new ArrayList<StatsListener>();
    private StreamStat minStat;
    private StreamStat maxStat;
    private StreamStat avgStat;
    private List<StreamStat> streamStats = new ArrayList<>();

    public StatsDisplay(NumberFormat numberFormat) {
      minStat = new StreamStat(StreamStat.TYPE_MIN, numberFormat);
      maxStat = new StreamStat(StreamStat.TYPE_MAX, numberFormat);
      avgStat = new StreamStat(StreamStat.TYPE_AVERAGE, numberFormat);
      streamStats.add(minStat);
      streamStats.add(maxStat);
      streamStats.add(avgStat);
    }

    public void clear() {
      minStat.clear();
      maxStat.clear();
      avgStat.clear();
    }

    public void updateFromBundle(SensorObserver.Data bundle) {
      updateStreamStats(bundle.min, bundle.max, bundle.average);
    }

    public List<StreamStat> updateStreamStats(double yMin, double yMax, double average) {
      minStat.setValue(yMin);
      maxStat.setValue(yMax);
      avgStat.setValue(average);

      updateListeners();

      return streamStats;
    }

    public void addStatsListener(StatsListener listener) {
      statsListeners.add(listener);
    }

    public List<StreamStat> updateStreamStats(TrialStats trialStats) {
      if (trialStats.hasStat(GoosciTrial.SensorStat.StatType.MINIMUM)) {
        minStat.setValue(trialStats.getStatValue(GoosciTrial.SensorStat.StatType.MINIMUM, 0));
      }
      if (trialStats.hasStat(GoosciTrial.SensorStat.StatType.MAXIMUM)) {
        maxStat.setValue(trialStats.getStatValue(GoosciTrial.SensorStat.StatType.MAXIMUM, 0));
      }
      if (trialStats.hasStat(GoosciTrial.SensorStat.StatType.AVERAGE)) {
        avgStat.setValue(trialStats.getStatValue(GoosciTrial.SensorStat.StatType.AVERAGE, 0));
      }

      updateListeners();
      return streamStats;
    }

    private void updateListeners() {
      for (int index = 0, count = statsListeners.size(); index < count; ++index) {
        statsListeners.get(index).onStatsUpdated(streamStats);
      }
    }
  }

  // Save information about the recording min, max and sum, as well as when recording
  // began and how many data points we have received, so that the stats calculation can
  // be done very efficiently.
  private double min;
  private double max;
  private double sum;

  private long startTimestamp = RecordingMetadata.NOT_RECORDING;
  private long latestTimestamp = RecordingMetadata.NOT_RECORDING;
  private int statSize;

  private String sensorId;

  public StatsAccumulator(String sensorId) {
    this.sensorId = sensorId;
    clearStats();
  }

  // Clears the stream stats.
  public void clearStats() {
    min = Double.MAX_VALUE;
    max = -Double.MAX_VALUE;
    sum = 0;
    startTimestamp = RecordingMetadata.NOT_RECORDING;
    latestTimestamp = RecordingMetadata.NOT_RECORDING;
    statSize = 0;
  }

  public boolean isInitialized() {
    return statSize > 0;
  }

  // Update the stream stats based on the new timestamp and value.
  // Assumes that all new timestamps acquired are bigger than the recording start time.
  public void updateRecordingStreamStats(long timestampMillis, double value) {
    latestTimestamp = timestampMillis;
    statSize++;
    if (startTimestamp == RecordingMetadata.NOT_RECORDING) {
      startTimestamp = timestampMillis;
      min = value;
      max = value;
      sum = value;
    } else {
      if (value > max) {
        max = value;
      } else if (value < min) {
        min = value;
      }
      sum = sum + value;
    }
  }

  private double getAverage() {
    return sum / statSize;
  }

  public long getLatestTimestamp() {
    return latestTimestamp;
  }

  public void addStatsToBundle(SensorObserver.Data data) {
    data.min = min;
    data.max = max;
    data.average = getAverage();
  }

  public void updateDisplayDirectly(StatsDisplay display) {
    final SensorObserver.Data bundle = new SensorObserver.Data();
    addStatsToBundle(bundle);
    display.updateFromBundle(bundle);
  }

  public TrialStats makeSaveableStats() {
    TrialStats stats = new TrialStats(sensorId);
    populateTrialStats(stats);
    return stats;
  }

  public void populateTrialStats(TrialStats stats) {
    stats.setStatStatus(SensorTrialStats.StatStatus.VALID);
    stats.putStat(GoosciTrial.SensorStat.StatType.MINIMUM, min);
    stats.putStat(GoosciTrial.SensorStat.StatType.MAXIMUM, max);
    stats.putStat(GoosciTrial.SensorStat.StatType.AVERAGE, getAverage());
    stats.putStat(GoosciTrial.SensorStat.StatType.NUM_DATA_POINTS, statSize);
    stats.putStat(GoosciTrial.SensorStat.StatType.TOTAL_DURATION, latestTimestamp - startTimestamp);
  }
}
