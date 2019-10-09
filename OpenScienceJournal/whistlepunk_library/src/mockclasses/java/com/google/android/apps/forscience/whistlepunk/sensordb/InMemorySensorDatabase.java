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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.forscience.whistlepunk.BatchInsertScalarReading;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.DataControllerImpl;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.StubAppAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData.ScalarSensorDataDump;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData.ScalarSensorDataRow;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.MoreExecutors;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemorySensorDatabase implements SensorDatabase {
  private List<List<Reading>> readings = new ArrayList<>();

  public static DataControllerImpl makeSimpleController() {
    return new InMemorySensorDatabase().makeSimpleController(new MemoryMetadataManager());
  }

  public DataControllerImpl makeSimpleController(MemoryMetadataManager mmm) {
    return makeSimpleController(mmm, new HashMap<>());
  }

  public DataControllerImpl makeSimpleController(MemoryMetadataManager mmm, Clock clock) {
    return makeDataControllerImpl(mmm, new HashMap<>(), clock);
  }

  @NonNull
  public DataControllerImpl makeSimpleController(
      MemoryMetadataManager manager, Map<String, SensorProvider> providerMap) {
    return makeDataControllerImpl(manager, providerMap, new MonotonicClock());
  }

  public RecordingDataController makeSimpleRecordingController(
      MemoryMetadataManager memoryMetadataManager) {
    return makeDataControllerImpl(memoryMetadataManager, new HashMap<>(), new MonotonicClock());
  }

  @NonNull
  private DataControllerImpl makeDataControllerImpl(
      MemoryMetadataManager manager, Map<String, SensorProvider> providerMap, Clock clock) {
    return new DataControllerImpl(
        null, // context
        StubAppAccount.getInstance(),
        this,
        MoreExecutors.directExecutor(),
        MoreExecutors.directExecutor(),
        MoreExecutors.directExecutor(),
        manager,
        clock,
        providerMap,
        new ConnectableSensor.Connector(providerMap));
  }

  @Override
  public void addScalarReadings(List<BatchInsertScalarReading> readings) {
    for (BatchInsertScalarReading r : readings) {
      addScalarReading(r.trialId, r.sensorId, r.resolutionTier, r.timestampMillis, r.value);
    }
  }

  @Override
  public void addScalarReading(
      String trialId, String databaseTag, int resolutionTier, long timestampMillis, double value) {
    getTierReadings(resolutionTier).add(new Reading(trialId, databaseTag, timestampMillis, value));
  }

  private List<Reading> getTierReadings(int resolutionTier) {
    while (resolutionTier >= readings.size()) {
      readings.add(new ArrayList<Reading>());
    }
    return readings.get(resolutionTier);
  }

  @Override
  public ScalarReadingList getScalarReadings(
      String trialId, String sensorTag, TimeRange range, int resolutionTier, int maxRecords) {
    final List<ScalarReading> readingsToReturn = new ArrayList<>();
    for (Reading reading : getReadings(resolutionTier)) {
      if (range.getTimes().contains(reading.getTimestampMillis())) {
        readingsToReturn.add(
            new ScalarReading(reading.getTimestampMillis(), reading.getValue(), sensorTag));
      }
    }
    return new ScalarReadingList() {
      @Override
      public void deliver(StreamConsumer c) {
        for (ScalarReading scalarReading : readingsToReturn) {
          c.addData(scalarReading.getCollectedTimeMillis(), scalarReading.getValue());
        }
      }

      @Override
      public int size() {
        return readingsToReturn.size();
      }

      @Override
      public List<ChartData.DataPoint> asDataPoints() {
        List<ChartData.DataPoint> result = new ArrayList<>();
        for (ScalarReading scalarReading : readingsToReturn) {
          result.add(
              new ChartData.DataPoint(
                  scalarReading.getCollectedTimeMillis(), scalarReading.getValue()));
        }
        return result;
      }
    };
  }

  @Override
  public GoosciScalarSensorData.ScalarSensorData getScalarReadingProtos(Experiment experiment) {
    GoosciScalarSensorData.ScalarSensorData.Builder data =
        GoosciScalarSensorData.ScalarSensorData.newBuilder();
    List<ScalarSensorDataDump> sensorDataList = getScalarReadingProtosAsList(experiment);
    return data.addAllSensors(sensorDataList).build();
  }

  @Override
  public List<ScalarSensorDataDump> getScalarReadingProtosAsList(Experiment experiment) {
    ArrayList<ScalarSensorDataDump> sensorDataList = new ArrayList<>();
    for (GoosciTrial.Trial trial : experiment.getTrialsList()) {
      com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range range =
          trial.getRecordingRange();
      TimeRange timeRange = TimeRange.oldest(Range.closed(range.getStartMs(), range.getEndMs()));
      for (GoosciSensorLayout.SensorLayout sensor : trial.getSensorLayoutsList()) {
        String tag = sensor.getSensorId();
        sensorDataList.add(getScalarReadingSensorProtos(tag, timeRange));
      }
    }

    return sensorDataList;
  }

  @Override
  public GoosciScalarSensorData.ScalarSensorData getScalarReadingProtosForTrial(
      Experiment experiment, String trialId) {
    ArrayList<ScalarSensorDataDump> sensorDataList = new ArrayList<>();
    for (GoosciTrial.Trial trial : experiment.getTrialsList()) {
      if (!trial.getTrialId().equals(trialId)) {
        continue;
      }
      com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range range =
          trial.getRecordingRange();
      TimeRange timeRange = TimeRange.oldest(Range.closed(range.getStartMs(), range.getEndMs()));
      for (GoosciSensorLayout.SensorLayout sensor : trial.getSensorLayoutsList()) {
        String tag = sensor.getSensorId();
        sensorDataList.add(getScalarReadingSensorProtos(tag, timeRange));
      }
    }
    return GoosciScalarSensorData.ScalarSensorData.newBuilder()
        .addAllSensors(sensorDataList)
        .build();
  }

  public ScalarSensorDataDump getScalarReadingSensorProtos(String sensorTag, TimeRange range) {
    com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData
            .ScalarSensorDataDump.Builder
        sensor = ScalarSensorDataDump.newBuilder().setTag(sensorTag);
    ArrayList<ScalarSensorDataRow> rows = new ArrayList<>();
    for (Reading reading : getReadings(0)) {
      if (range.getTimes().contains(reading.getTimestampMillis())) {
        ScalarSensorDataRow row =
            ScalarSensorDataRow.newBuilder()
                .setTimestampMillis(reading.getTimestampMillis())
                .setValue(reading.getValue())
                .build();
        sensor.setTrialId(reading.getTrialId());
        rows.add(row);
      }
    }

    return sensor.addAllRows(rows).build();
  }

  @Override
  public String getFirstDatabaseTagAfter(long timestamp) {
    return null;
  }

  @Override
  public void deleteScalarReadings(String trialId, String sensorTag, TimeRange range) {
    for (List<Reading> readingList : readings) {
      for (int index = readingList.size() - 1; index >= 0; --index) {
        Reading reading = readingList.get(index);
        if (reading.getDatabaseTag().equals(sensorTag)
            && reading.getTrialId().equals(trialId)
            && range.getTimes().contains(reading.getTimestampMillis())) {
          readingList.remove(index);
        }
      }
    }
  }

  @Override
  public Observable<ScalarReading> createScalarObservable(
      String trialId, String[] sensorTags, TimeRange range, int resolutionTier) {
    return null;
  }

  public List<Reading> getReadings(int resolutionTier) {
    if (resolutionTier >= readings.size()) {
      return Collections.emptyList();
    } else {
      return readings.get(resolutionTier);
    }
  }

  public RecordingDataController makeSimpleRecordingController() {
    return makeSimpleRecordingController(new MemoryMetadataManager());
  }

  @VisibleForTesting
  public static class Reading {
    private final String databaseTag;
    private final long timestampMillis;
    private final double value;
    private final String trialId;

    public Reading(String trialId, String databaseTag, long timestampMillis, double value) {
      this.databaseTag = databaseTag;
      this.timestampMillis = timestampMillis;
      this.value = value;
      this.trialId = trialId;
    }

    public String getDatabaseTag() {
      return databaseTag;
    }

    public long getTimestampMillis() {
      return timestampMillis;
    }

    public double getValue() {
      return value;
    }

    public String getTrialId() {
      return trialId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final Reading reading = (Reading) o;

      if (timestampMillis != reading.timestampMillis) {
        return false;
      }
      if (Double.compare(reading.value, value) != 0) {
        return false;
      }
      if (!trialId.equals(reading.trialId)) {
        return false;
      }
      return !(databaseTag != null
          ? !databaseTag.equals(reading.databaseTag)
          : reading.databaseTag != null);
    }

    @Override
    public int hashCode() {
      int result;
      long temp;
      result = databaseTag != null ? databaseTag.hashCode() : 0;
      result = 31 * result + (int) (timestampMillis ^ (timestampMillis >>> 32));
      temp = Double.doubleToLongBits(value);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      return result;
    }

    /** Results are only meant to be useful in test failure reports */
    @Override
    public String toString() {
      return "Reading{"
          + "mDatabaseTag='"
          + databaseTag
          + '\''
          + ", mTimestampMillis="
          + timestampMillis
          + ", mValue="
          + value
          + ", mTrialId="
          + trialId
          + '}';
    }
  }
}
