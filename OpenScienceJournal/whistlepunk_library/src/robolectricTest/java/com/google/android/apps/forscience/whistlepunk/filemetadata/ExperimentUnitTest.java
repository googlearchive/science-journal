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

import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.forscience.whistlepunk.ExperimentCreator;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ExperimentSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the Experiment class. */
@RunWith(RobolectricTestRunner.class)
public class ExperimentUnitTest {

  @Test
  public void testNewExperiment() {
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(10, "localId", 0);
    assertThat(experiment.getCreationTimeMs()).isEqualTo(10);
    assertThat(experiment.getLastUsedTime()).isEqualTo(10);
    assertThat(experiment.isArchived()).isFalse();
  }

  @Test
  public void testTriggers() {
    // No triggers on creation
    Experiment experiment = ExperimentCreator.newExperimentForTesting(10, "localId", 0);
    assertThat(experiment.getSensorTriggersForSensor("sensorId")).isEmpty();

    GoosciSensorTrigger.SensorTrigger triggerProto =
        GoosciSensorTrigger.SensorTrigger.newBuilder().setSensorId("sensorId").build();
    SensorTrigger trigger = SensorTrigger.fromProto(triggerProto);
    experiment.addSensorTrigger(trigger);
    assertThat(experiment.getSensorTriggersForSensor("sensorId")).hasSize(1);
    assertThat(experiment.getExperimentProto().getSensorTriggersList()).hasSize(1);
  }

  @Test
  public void testLayouts() {
    // No layouts on creation
    Experiment experiment = ExperimentCreator.newExperimentForTesting(10, "localId", 0);
    assertThat(experiment.getSensorLayouts()).isEmpty();
    SensorLayoutPojo sensorLayout = new SensorLayoutPojo();
    sensorLayout.setSensorId("sensorId");
    experiment.setSensorLayouts(Arrays.asList(sensorLayout));

    assertThat(experiment.getSensorLayouts()).hasSize(1);
    assertThat(experiment.getExperimentProto().getSensorLayoutsList()).hasSize(1);
  }

  @Test
  public void testLayoutsWithUpdate() {
    // No layouts on creation
    Experiment experiment = ExperimentCreator.newExperimentForTesting(10, "localId", 0);
    assertThat(experiment.getSensorLayouts()).isEmpty();

    SensorLayoutPojo sensorLayout = new SensorLayoutPojo();
    sensorLayout.setSensorId("sensorId");
    experiment.updateSensorLayout(0, sensorLayout);

    assertThat(experiment.getSensorLayouts()).hasSize(1);
    assertThat(experiment.getExperimentProto().getSensorLayoutsList()).hasSize(1);
  }

  @Test
  public void testExperimentSensors() {
    // No sensors on creation
    Experiment experiment = ExperimentCreator.newExperimentForTesting(10, "localId", 0);
    assertThat(experiment.getExperimentSensors()).isEmpty();

    com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ExperimentSensor
        sensor = ExperimentSensor.newBuilder().setSensorId("sensorId").build();
    experiment.setExperimentSensors(Arrays.asList(sensor));

    assertThat(experiment.getExperimentSensors()).hasSize(1);
    assertThat(experiment.getExperimentProto().getExperimentSensorsList()).hasSize(1);
  }

  @Test
  public void testUpdatesProtoOnlyWhenNeeded() {
    GoosciExperiment.Experiment.Builder proto = GoosciExperiment.Experiment.newBuilder();
    GoosciSensorLayout.SensorLayout layoutProto =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("sensorId").build();
    proto.addSensorLayouts(layoutProto);
    GoosciSensorTrigger.SensorTrigger triggerProto =
        GoosciSensorTrigger.SensorTrigger.newBuilder().setSensorId("sensorId").build();
    proto.addSensorTriggers(triggerProto);
    com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ExperimentSensor
        expSensorProto = ExperimentSensor.newBuilder().setSensorId("sensorId").build();
    proto.addExperimentSensors(expSensorProto);

    ExperimentOverviewPojo overview = new ExperimentOverviewPojo();
    overview.setExperimentId("cheese");

    Experiment experiment = Experiment.fromExperiment(proto.build(), overview);

    // Try to get the proto *before* converting the objects into lists.
    GoosciExperiment.Experiment result = experiment.getExperimentProto();
    assertThat(result.getSensorLayoutsList()).hasSize(1);
    assertThat(result.getSensorTriggersList()).hasSize(1);
    assertThat(result.getExperimentSensorsList()).hasSize(1);

    List<SensorLayoutPojo> layouts = experiment.getSensorLayouts();
    SensorLayoutPojo sensorLayout = new SensorLayoutPojo();
    sensorLayout.setSensorId("secondSensorId");
    layouts.add(sensorLayout);

    // Now update the layouts and make sure it still works as expected.
    experiment.setSensorLayouts(layouts);
    result = experiment.getExperimentProto();
    assertThat(result.getSensorLayoutsList()).hasSize(2);
    assertThat(result.getSensorLayouts(1).getSensorId()).isEqualTo("secondSensorId");
  }
}
