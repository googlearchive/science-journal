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

import com.google.android.apps.forscience.javalib.FailureListener;
import java.util.List;

/** Data interface for sensor recorders */
public interface RecordingDataController {
  /**
   * @see
   *     com.google.android.apps.forscience.whistlepunk.sensordb.SensorDatabase#addScalarReading(String,
   *     String, int, long, double)
   */
  void addScalarReading(
      String trialId,
      String sensorId,
      final int resolutionTier,
      long timestampMillis,
      double value);

  /** Add all of the scalar readings in the list. */
  void addScalarReadings(List<BatchInsertScalarReading> readings);

  /**
   * If an error is encountered storing data or stats for {@code sensorId}, notify {@code listener}
   */
  void setDataErrorListenerForSensor(String sensorId, FailureListener listener);

  /** Clear listener set by earlier call to {@code setDataErrorListener} */
  void clearDataErrorListenerForSensor(String sensorId);
}
