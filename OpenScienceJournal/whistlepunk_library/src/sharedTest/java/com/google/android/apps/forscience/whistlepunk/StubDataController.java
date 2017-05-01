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

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;

import java.util.List;
import java.util.Map;

/**
 * A DataController with empty implementations of all the methods, for tests to
 * extend and override only what they need.
 */
public class StubDataController implements DataController {
    @Override
    public void getScalarReadings(String databaseTag, int resolutionTier, TimeRange timeRange,
            int maxRecords, MaybeConsumer<ScalarReadingList> onSuccess) {

    }

    @Override
    public void addCropApplicationLabel(ApplicationLabel label,
            MaybeConsumer<ApplicationLabel> onSuccess) {

    }

    @Override
    public void startTrial(Experiment experiment, List<GoosciSensorLayout.SensorLayout> sensorLayouts,
            MaybeConsumer<Trial> onSuccess) {

    }

    @Override
    public void stopTrial(Experiment experiment, Trial trial, MaybeConsumer<Trial> onSuccess) {

    }

    @Override
    public void updateTrial(Trial trial, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void deleteRun(ExperimentRun run, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void createExperiment(MaybeConsumer<Experiment> onSuccess) {

    }

    @Override
    public void deleteExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void getExperimentById(String experimentId, MaybeConsumer<Experiment> onSuccess) {

    }

    @Override
    public void updateExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void getExperimentRun(String experimentId, String startLabelId,
            MaybeConsumer<ExperimentRun> onSuccess) {

    }

    @Override
    public void getExperimentRuns(String experiment, boolean includeArchived,
            final boolean includeInvalid, MaybeConsumer<List<ExperimentRun>> onSuccess) {

    }

    @Override
    public void editApplicationLabel(ApplicationLabel updatedLabel,
            MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public String generateNewLabelId() {
        return null;
    }

    @Override
    public void getExperiments(boolean includeArchived, MaybeConsumer<List<Experiment>> onSuccess) {

    }

    @Override
    public void getLastUsedUnarchivedExperiment(MaybeConsumer<Experiment> onSuccess) {

    }

    @Override
    public void getExternalSensors(MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess) {

    }

    @Override
    public void getExternalSensorsByExperiment(String experimentId,
            MaybeConsumer<ExperimentSensors> onSuccess) {

    }

    @Override
    public void getExternalSensorById(String id, MaybeConsumer<ExternalSensorSpec> onSuccess) {

    }

    @Override
    public void addSensorToExperiment(String experimentId, String sensorId,
            MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void removeSensorFromExperiment(String experimentId, String sensorId,
            MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void updateLastUsedExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void addOrGetExternalSensor(ExternalSensorSpec sensor,
            MaybeConsumer<String> onSensorId) {

    }

    @Override
    public void replaceSensorInExperiment(String experimentId, String oldSensorId,
            String newSensorId, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void getMyDevices(MaybeConsumer<List<InputDeviceSpec>> onSuccess) {

    }

    @Override
    public void forgetMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void addMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {

    }
}
