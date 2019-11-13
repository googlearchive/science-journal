/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BatchDataController implements RecordingDataController, Closeable {
  private RecordingDataController dataController;
  private List<BatchInsertScalarReading> readings = new ArrayList<>();

  public BatchDataController(RecordingDataController dataController) {
    this.dataController = dataController;
  }

  public void addScalarReading(
      String trialId,
      String sensorId,
      final int resolutionTier,
      long timestampMillis,
      double value) {
    readings.add(
        new BatchInsertScalarReading(trialId, sensorId, resolutionTier, timestampMillis, value));

    if (readings.size() > 10000) {
      flushScalarReadings();
    }
  }

  @Override
  public void addScalarReadings(List<BatchInsertScalarReading> readings) {
    dataController.addScalarReadings(readings);
  }

  public void flushScalarReadings() {
    dataController.addScalarReadings(readings);
    readings = new ArrayList<>();
  }

  /**
   * If an error is encountered storing data or stats for {@code sensorId}, notify {@code listener}
   */
  public void setDataErrorListenerForSensor(String sensorId, FailureListener listener) {
    dataController.setDataErrorListenerForSensor(sensorId, listener);
  }

  /** Clear listener set by earlier call to {@code setDataErrorListener} */
  public void clearDataErrorListenerForSensor(String sensorId) {
    dataController.clearDataErrorListenerForSensor(sensorId);
  }

  @Override
  public void close() throws IOException {
    flushScalarReadings();
  }
}
