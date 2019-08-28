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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.BatchDataController;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData.ScalarSensorDataDump;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData.ScalarSensorDataRow;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/*
 * Reads protos that have been exported from another experiment and populates the database with a
 * new experiment containing the data being imported.
 */

public class ScalarSensorDumpReader {
  private static final int NO_DATA_RECORDED = -1;
  private static final String TAG = "ScalarSensorDumpReader";

  private final RecordingDataController dataController;
  private long lastDataTimestampMillis = NO_DATA_RECORDED;
  private final int zoomLevelBetweenTiers;

  public ScalarSensorDumpReader(RecordingDataController dataController) {
    this.dataController = dataController;
    zoomLevelBetweenTiers = ScalarSensor.DEFAULT_ZOOM_LEVEL_BETWEEN_TIERS;
  }

  public void readData(
      GoosciScalarSensorData.ScalarSensorData scalarSensorData, Map<String, String> idMap) {
    int zoomBufferSize = zoomLevelBetweenTiers * 2;
    for (ScalarSensorDataDump sensor : scalarSensorData.getSensorsList()) {
      ZoomRecorder zoomRecorder = new ZoomRecorder(sensor.getTag(), zoomBufferSize, 1);
      String trialId = idMap.get(sensor.getTrialId());
      zoomRecorder.setTrialId(trialId);
      try (BatchDataController batchController = new BatchDataController(dataController)) {
        addAllRows(sensor, zoomRecorder, trialId, batchController);
        batchController.flushScalarReadings();
      } catch (IOException ioe) {
        Log.e(TAG, "Exception while flushing BatchDataController", ioe);
      }
      lastDataTimestampMillis = NO_DATA_RECORDED;
    }
  }

  public void readData(List<ScalarSensorDataDump> scalarSensorData) {
    int zoomBufferSize = zoomLevelBetweenTiers * 2;
    for (ScalarSensorDataDump sensor : scalarSensorData) {
      ZoomRecorder zoomRecorder = new ZoomRecorder(sensor.getTag(), zoomBufferSize, 1);
      String trialId = sensor.getTrialId();
      zoomRecorder.setTrialId(trialId);
      try (BatchDataController batchController = new BatchDataController(dataController)) {
        addAllRows(sensor, zoomRecorder, trialId, batchController);
        batchController.flushScalarReadings();
      } catch (IOException ioe) {
        Log.e(TAG, "Exception while flushing BatchDataController", ioe);
      }
      lastDataTimestampMillis = NO_DATA_RECORDED;
    }
  }

  public void readData(ScalarSensorDataDump sensor) {
    int zoomBufferSize = zoomLevelBetweenTiers * 2;

    ZoomRecorder zoomRecorder = new ZoomRecorder(sensor.getTag(), zoomBufferSize, 1);
    String trialId = sensor.getTrialId();
    zoomRecorder.setTrialId(trialId);
    try (BatchDataController batchController = new BatchDataController(dataController)) {
      addAllRows(sensor, zoomRecorder, trialId, batchController);
      batchController.flushScalarReadings();
    } catch (IOException ioe) {
      Log.e(TAG, "Exception while flushing BatchDataController", ioe);
    }
    lastDataTimestampMillis = NO_DATA_RECORDED;
  }

  private void addAllRows(
      ScalarSensorDataDump sensor,
      ZoomRecorder zoomRecorder,
      String trialId,
      RecordingDataController batchController) {
    for (ScalarSensorDataRow row : sensor.getRowsList()) {
      addData(
          batchController,
          zoomRecorder,
          trialId,
          sensor.getTag(),
          row.getTimestampMillis(),
          row.getValue());
    }
    zoomRecorder.flushAllTiers(batchController);
  }

  private boolean addData(
      RecordingDataController dataController,
      ZoomRecorder zoomRecorder,
      String trialId,
      String tag,
      final long timestampMillis,
      double value) {
    if (!maintainsTimeSeries(timestampMillis)) {
      return false;
    }
    recordData(dataController, zoomRecorder, trialId, tag, timestampMillis, value);
    lastDataTimestampMillis = timestampMillis;
    return true;
  }

  private boolean maintainsTimeSeries(final long timestampMillis) {
    if (timestampMillis > lastDataTimestampMillis) {
      return true;
    }
    return false;
  }

  private void recordData(
      RecordingDataController batchController,
      ZoomRecorder zoomRecorder,
      String trialId,
      String tag,
      long timestampMillis,
      double value) {
    zoomRecorder.addData(timestampMillis, value, batchController);
    batchController.addScalarReading(trialId, tag, 0, timestampMillis, value);
  }
}
