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

import static junit.framework.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for the Experiment class.
 */
public class ExperimentTest {

    @Test
    public void testNewExperiment() {
        Experiment experiment = Experiment.newExperiment(10);
        assertEquals(experiment.getTimestamp(), 10);
        assertEquals(experiment.getLastUsedTime(), 10);
        assertEquals(experiment.isArchived(), false);
    }

    @Test
    public void testTriggers() {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();

        // No triggers on creation
        Experiment experiment = new Experiment(proto, false);
        assertEquals(experiment.getSensorTriggers(), Collections.emptyList());

        GoosciSensorTrigger.SensorTrigger triggerProto = new GoosciSensorTrigger.SensorTrigger();
        triggerProto.sensorId = "sensorid";
        SensorTrigger trigger = new SensorTrigger(triggerProto);
        experiment.setSensorTriggers(Arrays.asList(trigger));

        assertEquals(experiment.getSensorTriggers().size(), 1);
        assertEquals(experiment.getExperimentProto().sensorTriggers.length, 1);
    }

    @Test
    public void testLayouts() {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();

        // No layouts on creation
        Experiment experiment = new Experiment(proto, false);
        assertEquals(experiment.getSensorLayouts(), Collections.emptyList());

        GoosciSensorLayout.SensorLayout sensorLayout = new GoosciSensorLayout.SensorLayout();
        sensorLayout.sensorId = "sensorId";
        experiment.setSensorLayouts(Arrays.asList(sensorLayout));

        assertEquals(experiment.getSensorLayouts().size(), 1);
        assertEquals(experiment.getExperimentProto().sensorLayouts.length, 1);
    }

    @Test
    public void testExperimentSensors() {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();

        // No sensors on creation
        Experiment experiment = new Experiment(proto, false);
        assertEquals(experiment.getExperimentSensors(), Collections.emptyList());

        GoosciExperiment.ExperimentSensor sensor = new GoosciExperiment.ExperimentSensor();
        sensor.sensorId = "sensorId";
        experiment.setExperimentSensors(Arrays.asList(sensor));

        assertEquals(experiment.getExperimentSensors().size(), 1);
        assertEquals(experiment.getExperimentProto().experimentSensors.length, 1);
    }

    @Test
    public void testUpdatesProtoOnlyWhenNeeded() {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();
        GoosciSensorLayout.SensorLayout layoutProto = new GoosciSensorLayout.SensorLayout();
        layoutProto.sensorId = "sensorId";
        proto.sensorLayouts = new GoosciSensorLayout.SensorLayout[]{layoutProto};
        GoosciSensorTrigger.SensorTrigger triggerProto = new GoosciSensorTrigger.SensorTrigger();
        triggerProto.sensorId = "sensorId";
        proto.sensorTriggers = new GoosciSensorTrigger.SensorTrigger[]{triggerProto};
        GoosciExperiment.ExperimentSensor expSensorProto = new GoosciExperiment.ExperimentSensor();
        expSensorProto.sensorId = "sensorId";
        proto.experimentSensors = new GoosciExperiment.ExperimentSensor[]{expSensorProto};

        Experiment experiment = new Experiment(proto, false);

        // Try to get the proto *before* converting the objects into lists.
        GoosciExperiment.Experiment result = experiment.getExperimentProto();
        assertEquals(result.sensorLayouts.length, 1);
        assertEquals(result.sensorTriggers.length, 1);
        assertEquals(result.experimentSensors.length, 1);

        List<GoosciSensorLayout.SensorLayout> layouts = experiment.getSensorLayouts();
        GoosciSensorLayout.SensorLayout sensorLayout = new GoosciSensorLayout.SensorLayout();
        sensorLayout.sensorId = "secondSensorId";
        layouts.add(sensorLayout);

        // Now update the layouts and make sure it still works as expected.
        experiment.setSensorLayouts(layouts);
        result = experiment.getExperimentProto();
        assertEquals(result.sensorLayouts.length, 2);
        assertEquals(result.sensorLayouts[1].sensorId, "secondSensorId");
    }
}
