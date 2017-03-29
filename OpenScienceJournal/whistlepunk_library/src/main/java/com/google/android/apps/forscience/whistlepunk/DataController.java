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
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.metadata.Run;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;

import java.util.List;
import java.util.Map;

/**
 * Provides access to any data outside of the UI.  All methods should be called from the UI thread;
 * callers can expect that methods will return quickly, and the provided callback will be called
 * later on the UI thread.
 */
public interface DataController {
    void getScalarReadings(String databaseTag, final int resolutionTier, TimeRange timeRange,
            int maxRecords, MaybeConsumer<ScalarReadingList> onSuccess);

    void addLabel(Label label, MaybeConsumer<Label> onSuccess);

    void startRun(Experiment experiment, List<GoosciSensorLayout.SensorLayout> sensorLayouts,
            MaybeConsumer<ApplicationLabel> onSuccess);

    void stopRun(Experiment experiment, String runId,
            List<GoosciSensorLayout.SensorLayout> layouts,
            MaybeConsumer<ApplicationLabel> onSuccess);

    void updateTrial(final Trial trial, MaybeConsumer<Success> onSuccess);

    void deleteRun(ExperimentRun run, MaybeConsumer<Success> onSuccess);

    void createExperiment(Project project, MaybeConsumer<Experiment> onSuccess);

    void deleteExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess);

    void getExperimentById(String experimentId,
                           MaybeConsumer<Experiment> onSuccess);

    void updateExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess);

    void getExperimentRun(String startLabelId, MaybeConsumer<ExperimentRun> onSuccess);

    void getExperimentRuns(String experiment, boolean includeArchived,
            final boolean includeInvalid, MaybeConsumer<List<ExperimentRun>> onSuccess);

    void createProject(MaybeConsumer<Project> onSuccess);

    void updateProject(Project project, MaybeConsumer<Success> onSuccess);

    void deleteProject(Project project, MaybeConsumer<Success> onSuccess);

    void getProjects(int maxNumber, boolean includeArchived,
                     MaybeConsumer<List<Project>> onSuccess);

    void editLabel(Label updatedLabel, MaybeConsumer<Label> onSuccess);

    void deleteLabel(Label label, MaybeConsumer<Success> onSuccess);

    String generateNewLabelId();

    void getLastUsedProject(MaybeConsumer<Project> onSuccess);

    void getExperimentsForProject(Project project, boolean includeArchived,
                                  MaybeConsumer<List<Experiment>> onSuccess);

    void getProjectById(String projectId, MaybeConsumer<Project> onSuccess);

    void getExternalSensors(MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess);

    // TODO: fix docs, rename?
    /**
     * Passes to onSuccess a map from sensor ids to external sensor specs
     */
    void getExternalSensorsByExperiment(final String experimentId,
            MaybeConsumer<ExperimentSensors> onSuccess);

    /**
     * Passes to onSuccess a map from sensor ids to external sensor specs
     */
    void getExternalSensorById(String id, MaybeConsumer<ExternalSensorSpec> onSuccess);

    /**
     * Adds the external sensor to the experiment.
     *
     * @return {@code true} to the consumer if the sensor was added, {@code false} if the sensor
     * was already present.
     */
    void addSensorToExperiment(final String experimentId, String sensorId,
            MaybeConsumer<Success> onSuccess);

    /**
     * Removes the external sensor from the experiment.
     *
     * @return {@code true} to the consumer if the sensor needed to be removed, {@code false} if
     * the sensor was not already present.
     */
    void removeSensorFromExperiment(final String experimentId, final String sensorId,
            MaybeConsumer<Success> onSuccess);

    void getLabelsForExperiment(Experiment experiment, MaybeConsumer<List<Label>> onSuccess);

    void updateLastUsedExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess);

    /**
     * Get the statistics for the given run and sensor
     *
     * @param runId (previously startLabelId) identifies the run
     */
    void getStats(String runId, String sensorId, MaybeConsumer<TrialStats> onSuccess);

    /**
     * Sets the stat status for a sensor and run.
     */
    void setSensorStatsStatus(final String runId, final String sensorId,
            final int status, MaybeConsumer<Success> onSuccess);

    /**
     * Recalculates the statistics for all the sensors in a run
     */
    void updateTrialStats(final String runId, final String sensorId, final TrialStats trialStats,
            MaybeConsumer<Success> onSuccess);

    /**
     * Set the sensor selection and layout for an experiment
     */
    void setSensorLayouts(String experimentId, List<GoosciSensorLayout.SensorLayout> layouts,
            MaybeConsumer<Success> onSuccess);

    /**
     * Retrieve the sensor selection and layout for an experiment
     */
    void getSensorLayouts(String experimentId,
            MaybeConsumer<List<GoosciSensorLayout.SensorLayout>> onSuccess);

    /**
     * Updates a sensor layout in a given position for an experiment
     */
    void updateSensorLayout(String experimentId, int position,
            GoosciSensorLayout.SensorLayout layout, MaybeConsumer<Success> onSuccess);

    /**
     * Makes sure there is an external sensor already registered in the database with the given
     * spec, and returns its id to {@code onSensorId}
     */
    void addOrGetExternalSensor(ExternalSensorSpec sensor, MaybeConsumer<String> onSensorId);

    // TODO: layout and external sensor storage is redundant
    /**
     * Replace oldSensorId with newSensorId in experiment.  All cards that were showing oldSensorId
     * will now show newSensorId
     */
    void replaceSensorInExperiment(String experimentId, String oldSensorId, String newSensorId,
            final MaybeConsumer<Success> onSuccess);

    /**
     * Adds a new SensorTrigger to the database.
     */
    void addSensorTrigger(SensorTrigger trigger, String experimentId,
            final MaybeConsumer<Success> onSuccess);

    /**
     * Updates a SensorTrigger in the database.
     */
    void updateSensorTrigger(SensorTrigger trigger, final MaybeConsumer<Success> onSuccess);

    /**
     * Gets a list of SensorTrigger by their IDs.
     */
    void getSensorTriggers(String[] triggerIds, final MaybeConsumer<List<SensorTrigger>> onSuccess);

    /**
     * Gets a list of SensorTriggers for a given sensor from the database.
     */
    void getSensorTriggersForSensor(String sensorId,
            final MaybeConsumer<List<SensorTrigger>> onSuccess);

    /**
     * Deletes a SensorTrigger.
     */
    void deleteSensorTrigger(SensorTrigger trigger, final MaybeConsumer<Success> onSuccess);

    void getMyDevices(MaybeConsumer<List<InputDeviceSpec>> onSuccess);

    void forgetMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess);

    void addMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess);
}
