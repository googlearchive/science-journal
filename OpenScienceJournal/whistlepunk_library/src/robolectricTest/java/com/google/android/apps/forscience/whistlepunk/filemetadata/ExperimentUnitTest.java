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

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for the Experiment class.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class ExperimentUnitTest {

    @Test
    public void testNewExperiment() {
        Experiment experiment = Experiment.newExperiment(10, "localId", 0);
        assertEquals(experiment.getCreationTimeMs(), 10);
        assertEquals(experiment.getLastUsedTime(), 10);
        assertEquals(experiment.isArchived(), false);
    }

    @Test
    public void testTriggers() {
        // No triggers on creation
        Experiment experiment = Experiment.newExperiment(10, "localId", 0);
        assertEquals(experiment.getSensorTriggersForSensor("sensorId"),
                Collections.<SensorTrigger>emptyList());

        GoosciSensorTrigger.SensorTrigger triggerProto = new GoosciSensorTrigger.SensorTrigger();
        triggerProto.sensorId = "sensorId";
        SensorTrigger trigger = SensorTrigger.fromProto(triggerProto);
        experiment.addSensorTrigger(trigger);
        assertEquals(experiment.getSensorTriggersForSensor("sensorId").size(), 1);
        assertEquals(experiment.getExperimentProto().sensorTriggers.length, 1);
    }

    @Test
    public void testLayouts() {
        // No layouts on creation
        Experiment experiment = Experiment.newExperiment(10, "localId", 0);
        assertEquals(experiment.getSensorLayouts(), Collections.emptyList());

        GoosciSensorLayout.SensorLayout sensorLayout = new GoosciSensorLayout.SensorLayout();
        sensorLayout.sensorId = "sensorId";
        experiment.setSensorLayouts(Arrays.asList(sensorLayout));

        assertEquals(experiment.getSensorLayouts().size(), 1);
        assertEquals(experiment.getExperimentProto().sensorLayouts.length, 1);
    }

    @Test
    public void testLayoutsWithUpdate() {
        // No layouts on creation
        Experiment experiment = Experiment.newExperiment(10, "localId", 0);
        assertEquals(experiment.getSensorLayouts(), Collections.emptyList());

        GoosciSensorLayout.SensorLayout sensorLayout = new GoosciSensorLayout.SensorLayout();
        sensorLayout.sensorId = "sensorId";
        experiment.updateSensorLayout(0, sensorLayout);

        assertEquals(experiment.getSensorLayouts().size(), 1);
        assertEquals(experiment.getExperimentProto().sensorLayouts.length, 1);
    }

    @Test
    public void testExperimentSensors() {
        // No sensors on creation
        Experiment experiment = Experiment.newExperiment(10, "localId", 0);
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

        GoosciUserMetadata.ExperimentOverview overview = new GoosciUserMetadata
                .ExperimentOverview();
        overview.experimentId = "cheese";

        Experiment experiment = Experiment.fromExperiment(proto, overview);

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
