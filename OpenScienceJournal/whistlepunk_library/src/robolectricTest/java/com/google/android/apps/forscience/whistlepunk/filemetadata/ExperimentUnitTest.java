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
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ExperimentSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciExperiment;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs.Destination;
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

    GoosciSensorTrigger.SensorTrigger.Builder triggerProto =
        GoosciSensorTrigger.SensorTrigger.newBuilder().setSensorId("sensorId");
    SensorTrigger trigger = SensorTrigger.fromProto(triggerProto.build());
    experiment.addSensorTrigger(trigger);
    assertThat(experiment.getSensorTriggersForSensor("sensorId")).hasSize(1);
    assertThat(experiment.getExperimentProto().sensorTriggers).hasLength(1);
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
    assertThat(experiment.getExperimentProto().sensorLayouts).hasLength(1);
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
    assertThat(experiment.getExperimentProto().sensorLayouts).hasLength(1);
  }

  @Test
  public void testExperimentSensors() {
    // No sensors on creation
    Experiment experiment = ExperimentCreator.newExperimentForTesting(10, "localId", 0);
    assertThat(experiment.getExperimentSensors()).isEmpty();

    com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ExperimentSensor
            .Builder
        sensor = ExperimentSensor.newBuilder().setSensorId("sensorId");
    experiment.setExperimentSensors(Arrays.asList(sensor.build()));

    assertThat(experiment.getExperimentSensors()).hasSize(1);
    assertThat(experiment.getExperimentProto().experimentSensors).hasLength(1);
  }

  @Test
  public void testUpdatesProtoOnlyWhenNeeded() {
    GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();
    @MigrateAs(Destination.BUILDER)
    GoosciSensorLayout.SensorLayout layoutProto = new GoosciSensorLayout.SensorLayout();
    layoutProto.sensorId = "sensorId";
    proto.sensorLayouts = new GoosciSensorLayout.SensorLayout[] {layoutProto};
    GoosciSensorTrigger.SensorTrigger.Builder triggerProto =
        GoosciSensorTrigger.SensorTrigger.newBuilder().setSensorId("sensorId");
    proto.sensorTriggers = new GoosciSensorTrigger.SensorTrigger[] {triggerProto.build()};
    com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ExperimentSensor
            .Builder
        expSensorProto = ExperimentSensor.newBuilder().setSensorId("sensorId");
    proto.experimentSensors = new ExperimentSensor[] {expSensorProto.build()};

    ExperimentOverviewPojo overview = new ExperimentOverviewPojo();
    overview.setExperimentId("cheese");

    Experiment experiment = Experiment.fromExperiment(proto, overview);

    // Try to get the proto *before* converting the objects into lists.
    GoosciExperiment.Experiment result = experiment.getExperimentProto();
    assertThat(result.sensorLayouts).hasLength(1);
    assertThat(result.sensorTriggers).hasLength(1);
    assertThat(result.experimentSensors).hasLength(1);

    List<SensorLayoutPojo> layouts = experiment.getSensorLayouts();
    SensorLayoutPojo sensorLayout = new SensorLayoutPojo();
    sensorLayout.setSensorId("secondSensorId");
    layouts.add(sensorLayout);

    // Now update the layouts and make sure it still works as expected.
    experiment.setSensorLayouts(layouts);
    result = experiment.getExperimentProto();
    assertThat(result.sensorLayouts).hasLength(2);
    assertThat(result.sensorLayouts[1].sensorId).isEqualTo("secondSensorId");
  }
}
