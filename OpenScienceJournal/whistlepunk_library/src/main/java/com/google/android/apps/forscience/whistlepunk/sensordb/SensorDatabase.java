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

import com.google.android.apps.forscience.whistlepunk.BatchInsertScalarReading;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData.ScalarSensorDataDump;
import io.reactivex.Observable;
import java.util.List;

/**
 * Stores and retrieves sensor data from on-device storage. All access should be be from a single
 * background thread; all calls are blocking, and do not perform internal synchronization.
 */
public interface SensorDatabase {

  /** Add all of the readings to the database. */
  void addScalarReadings(List<BatchInsertScalarReading> readings);

  /**
   * See {@link #getScalarReadings(String, String, TimeRange, int, int)} for semantics of these
   * params
   */
  void addScalarReading(
      String trialId, String sensorTag, int resolutionTier, long timestampMillis, double value);

  /**
   * Get stored scalar records
   *
   * @param trialId get records from this trial.
   * @param sensorTag get records with this tag
   * @param range specifies both the timestamps of interest, and the order in which records should
   *     be returned
   * @param resolutionTier the "resolution tier" requested. Use 0 for all points recorded, 1 for
   *     roughly 10% sample, 2 for 1% sample, etc.
   * @param maxRecords 0 if all records can be returned (may be very big). If >0, only this many
   *     records will be returned, starting from the direction given by range#getOrder
   * @return a list of readings read from the database
   */
  ScalarReadingList getScalarReadings(
      String trialId, String sensorTag, TimeRange range, int resolutionTier, int maxRecords);

  /**
   * Find the first sensor reading after {@code timestamp}. Return the database tag that represents
   * the sensor corresponding to the reading. This is likely to only be of value as long as we're
   * only recording one sensor at a time.
   */
  String getFirstDatabaseTagAfter(long timestamp);

  /** Deletes the scalar records for the given sensor for the given time range. */
  void deleteScalarReadings(String trialId, String sensorTag, TimeRange range);

  Observable<ScalarReading> createScalarObservable(
      String trialId, String[] sensorTags, TimeRange range, int resolutionTier);

  /**
   * Get a proto that contains all of the sensor data for the given experiment. Primarily used for
   * exporting experiments from the app.
   */
  GoosciScalarSensorData.ScalarSensorData getScalarReadingProtos(
      GoosciExperiment.Experiment experiment);

  /**
   * Get an ArrayList of ScalarSensorDataDump protos that contains all of the sensor data for the
   * given experiment. Primarily used for drive sync.
   */
  List<ScalarSensorDataDump> getScalarReadingProtosAsList(GoosciExperiment.Experiment experiment);

  /**
   * Get a ScalarSensorData proto that contains all of the sensor data for the given trial.
   * Primarily used for drive sync.
   */
  GoosciScalarSensorData.ScalarSensorData getScalarReadingProtosForTrial(
      GoosciExperiment.Experiment experiment, String trialId);
}
