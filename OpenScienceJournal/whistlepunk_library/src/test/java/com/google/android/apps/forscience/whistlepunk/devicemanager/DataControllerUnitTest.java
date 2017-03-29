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

import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExplodingFactory;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DataControllerUnitTest {
    @Test
    public void testAddScalarReading() {
        final InMemorySensorDatabase db = new InMemorySensorDatabase();
        RecordingDataController controller = db.makeSimpleRecordingController(
                new MemoryMetadataManager());
        controller.setDataErrorListenerForSensor("tag",
                new ExplodingFactory().makeListenerForOperation(""));

        controller.addScalarReading("tag", 0, 1234, 12.34);

        List<InMemorySensorDatabase.Reading> readings = db.getReadings(0);
        assertEquals(1, readings.size());
        InMemorySensorDatabase.Reading reading = readings.get(0);
        assertEquals("tag", reading.getDatabaseTag());
        assertEquals(1234, reading.getTimestampMillis());
        assertEquals(12.34, reading.getValue(), 0.001);
    }


    @Test
    public void testStopRun() {
        InMemorySensorDatabase db = new InMemorySensorDatabase();
        MemoryMetadataManager manager = new MemoryMetadataManager();
        final DataController dc = db.makeSimpleController(manager);

        final StoringConsumer<Project> cProject = new StoringConsumer<>();
        dc.createProject(cProject);
        Project project = cProject.getValue();

        final StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        dc.createExperiment(project, cExperiment);
        final Experiment experiment = cExperiment.getValue();

        ArrayList<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>();
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.maximumYAxisValue = 5;
        layouts.add(layout);

        final StoringConsumer<ApplicationLabel> cLabel = new StoringConsumer<>();
        dc.startRun(experiment, layouts, cLabel);
        final ApplicationLabel startLabel = cLabel.getValue();

        final ExperimentRun runWhileStarted = getOnlyExperimentRun(dc, experiment);
        assertEquals(startLabel.getRunId(), runWhileStarted.getTrialId());
        assertFalse(runWhileStarted.isValidRun());
        assertEquals(5, runWhileStarted.getSensorLayouts().get(0).maximumYAxisValue, 0.1);

        layout.maximumYAxisValue = 15;

        dc.stopRun(experiment, startLabel.getRunId(), layouts,
                TestConsumers.<ApplicationLabel>expectingSuccess());

        final ExperimentRun runWhileStopped = getOnlyExperimentRun(dc, experiment);
        assertEquals(startLabel.getRunId(), runWhileStopped.getTrialId());
        assertTrue(runWhileStopped.isValidRun());
        assertEquals(15, runWhileStarted.getSensorLayouts().get(0).maximumYAxisValue, 0.1);
    }

    private ExperimentRun getOnlyExperimentRun(DataController dc, Experiment experiment) {
        final StoringConsumer<List<ExperimentRun>> cRuns = new StoringConsumer<>();
        dc.getExperimentRuns(experiment.getExperimentId(), false, true, cRuns);
        final List<ExperimentRun> runs = cRuns.getValue();

        assertEquals("Failed.  experiment id: " + experiment.getExperimentId(), 1, runs.size());
        return runs.get(0);
    }
}