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

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData.ScalarSensorDataRow;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScalarSensorDumpReaderTest {
  private final MemoryMetadataManager metadata = new MemoryMetadataManager();
  private InMemorySensorDatabase db = new InMemorySensorDatabase();
  private final RecordingDataController recordingController =
      db.makeSimpleRecordingController(metadata);

  @Test
  public void testDataSuccessfullyWritten() {
    GoosciScalarSensorData.ScalarSensorData scalarSensorData =
        new GoosciScalarSensorData.ScalarSensorData();
    GoosciScalarSensorData.ScalarSensorDataDump sensor =
        new GoosciScalarSensorData.ScalarSensorDataDump();
    sensor.tag = "foo";
    sensor.trialId = "id";
    ArrayList<ScalarSensorDataRow> rowList = new ArrayList<>();
    ArrayList<GoosciScalarSensorData.ScalarSensorDataDump> sensorList = new ArrayList<>();
    HashMap<String, String> idMap = new HashMap<>();
    idMap.put("id", "id");
    for (int x = 1; x <= 10000; x++) {
      com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData
              .ScalarSensorDataRow.Builder
          row = ScalarSensorDataRow.newBuilder();
      row.setTimestampMillis(x);
      row.setValue(x * 100);
      rowList.add(row.build());
    }
    sensor.rows = rowList.toArray(new ScalarSensorDataRow[0]);
    sensorList.add(sensor);
    scalarSensorData.sensors =
        sensorList.toArray(GoosciScalarSensorData.ScalarSensorDataDump.emptyArray());

    ScalarSensorDumpReader reader = new ScalarSensorDumpReader(recordingController);
    reader.readData(scalarSensorData, idMap);

    ScalarReadingList readings =
        db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 0, 0);
    assertEquals(10000, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 1, 0);
    assertEquals(500, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 2, 0);
    assertEquals(26, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 3, 0);
    assertEquals(2, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 4, 0);
    assertEquals(0, readings.size());
  }

  @Test
  public void testDataSuccessfullyWrittenArrayList() {
    GoosciScalarSensorData.ScalarSensorDataDump sensor =
        new GoosciScalarSensorData.ScalarSensorDataDump();
    sensor.tag = "foo";
    sensor.trialId = "id";
    ArrayList<ScalarSensorDataRow> rowList = populateRowList();
    ArrayList<GoosciScalarSensorData.ScalarSensorDataDump> sensorList = new ArrayList<>();
    HashMap<String, String> idMap = new HashMap<>();
    idMap.put("id", "id");
    sensor.rows = rowList.toArray(new ScalarSensorDataRow[0]);
    sensorList.add(sensor);

    ScalarSensorDumpReader reader = new ScalarSensorDumpReader(recordingController);
    reader.readData(sensorList);

    ScalarReadingList readings =
        db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 0, 0);
    assertEquals(10000, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 1, 0);
    assertEquals(500, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 2, 0);
    assertEquals(26, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 3, 0);
    assertEquals(2, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 4, 0);
    assertEquals(0, readings.size());
  }

  @Test
  public void testDataSuccessfullyWrittenSingleSensor() {
    GoosciScalarSensorData.ScalarSensorDataDump sensor =
        new GoosciScalarSensorData.ScalarSensorDataDump();
    sensor.tag = "foo";
    sensor.trialId = "id";
    ArrayList<ScalarSensorDataRow> rowList = populateRowList();
    sensor.rows = rowList.toArray(new ScalarSensorDataRow[0]);

    ScalarSensorDumpReader reader = new ScalarSensorDumpReader(recordingController);
    reader.readData(sensor);

    ScalarReadingList readings =
        db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 0, 0);
    assertEquals(10000, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 1, 0);
    assertEquals(500, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 2, 0);
    assertEquals(26, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 3, 0);
    assertEquals(2, readings.size());

    readings = db.getScalarReadings("id", "foo", TimeRange.oldest(Range.all()), 4, 0);
    assertEquals(0, readings.size());
  }

  private ArrayList<ScalarSensorDataRow> populateRowList() {
    ArrayList<ScalarSensorDataRow> rowList = new ArrayList<>();
    for (int x = 1; x <= 10000; x++) {
      com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData
              .ScalarSensorDataRow.Builder
          row = ScalarSensorDataRow.newBuilder();
      row.setTimestampMillis(x);
      row.setValue(x * 100);
      rowList.add(row.build());
    }
    return rowList;
  }
}
