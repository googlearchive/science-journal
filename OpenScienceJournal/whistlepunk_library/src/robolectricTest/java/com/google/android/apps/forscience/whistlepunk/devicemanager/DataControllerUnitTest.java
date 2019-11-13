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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExplodingFactory;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DataControllerUnitTest {
  @Test
  public void testAddScalarReading() {
    final InMemorySensorDatabase db = new InMemorySensorDatabase();
    RecordingDataController controller =
        db.makeSimpleRecordingController(new MemoryMetadataManager());
    controller.setDataErrorListenerForSensor("tag", ExplodingFactory.makeListener());

    controller.addScalarReading("runId", "tag", 0, 1234, 12.34);

    List<InMemorySensorDatabase.Reading> readings = db.getReadings(0);
    assertEquals(1, readings.size());
    InMemorySensorDatabase.Reading reading = readings.get(0);
    assertEquals("runId", "tag", reading.getDatabaseTag());
    assertEquals(1234, reading.getTimestampMillis());
    assertEquals(12.34, reading.getValue(), 0.001);
  }

  @Test
  public void testStopRun() {
    InMemorySensorDatabase db = new InMemorySensorDatabase();
    MemoryMetadataManager manager = new MemoryMetadataManager();
    final DataController dc = db.makeSimpleController(manager);

    final StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
    dc.createExperiment(cExperiment);
    final Experiment experiment = cExperiment.getValue();

    ArrayList<SensorLayoutPojo> layouts = new ArrayList<>();
    SensorLayoutPojo layout = new SensorLayoutPojo();
    layout.setMaximumYAxisValue(5);
    layouts.add(layout);

    SensorLayout[] protoList = new SensorLayout[1];
    protoList[0] = layout.toProto();

    Trial trial = Trial.newTrial(10, protoList, new FakeUnitAppearanceProvider(), null);
    experiment.addTrial(trial);
    dc.updateExperiment(experiment.getExperimentId(), TestConsumers.<Success>expectingSuccess());

    final Trial runWhileStarted = getOnlyExperimentRun(dc, experiment.getExperimentId());
    assertEquals(trial.getTrialId(), runWhileStarted.getTrialId());
    assertFalse(runWhileStarted.isValid());
    assertEquals(5, runWhileStarted.getSensorLayouts().get(0).getMaximumYAxisValue(), 0.1);

    layout.setMaximumYAxisValue(15);
    trial.setSensorLayouts(layouts);
    trial.setRecordingEndTime(40);
    experiment.updateTrial(trial);

    dc.updateExperiment(experiment.getExperimentId(), TestConsumers.<Success>expectingSuccess());

    final Trial runWhileStopped = getOnlyExperimentRun(dc, experiment.getExperimentId());
    assertEquals(trial.getTrialId(), runWhileStopped.getTrialId());
    assertTrue(runWhileStopped.isValid());
    assertEquals(15, runWhileStarted.getSensorLayouts().get(0).getMaximumYAxisValue(), 0.1);
  }

  @Test
  public void tryToUpdateUncachedExperiment() {
    InMemorySensorDatabase db = new InMemorySensorDatabase();
    MemoryMetadataManager manager = new MemoryMetadataManager();
    final DataController dc = db.makeSimpleController(manager);
    try {
      Experiment uncached = manager.newExperiment();
      dc.updateExperiment(uncached, TestConsumers.expectingSuccess());
    } catch (IllegalArgumentException expected) {
      return;
    }
    fail("Should have thrown");
  }

  @Test
  public void tryToUpdateMiscachedExperiment() {
    InMemorySensorDatabase db = new InMemorySensorDatabase();
    MemoryMetadataManager manager = new MemoryMetadataManager();
    final DataController dc = db.makeSimpleController(manager);
    Experiment e = RxDataController.createExperiment(dc).test().values().get(0);
    Experiment miscached = manager.newExperiment(0, e.getExperimentId());

    try {
      dc.updateExperiment(miscached, TestConsumers.expectingSuccess());
    } catch (IllegalArgumentException expected) {
      return;
    }
    fail("Should have thrown");
  }

  private Trial getOnlyExperimentRun(DataController dc, String experimentId) {
    final StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
    dc.getExperimentById(experimentId, cExperiment);
    Trial result = cExperiment.getValue().getTrials().get(0);
    return result;
  }
}
