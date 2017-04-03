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

package com.google.android.apps.forscience.whistlepunk.metadata;

import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;

import java.util.List;
import java.util.Map;

/**
 * Loads and saves meta data.
 */
public interface MetaDataManager {

    /**
     * @return the project with matching projectId
     */
    Project getProjectById(String projectId);

    /**
     * @return the list of saved projects
     */
    List<Project> getProjects(int maxNumber, boolean archived);

    /**
     * @return a new project with a random ID.
     */
    Project newProject();

    /**
     * Updates project data.
     */
    void updateProject(Project project);

    /**
     * Removes a project, all its associated experiments and labels.
     */
    void deleteProject(Project project);

    Experiment getExperimentById(String experimentId);

    /**
     * Creates a new experiment for a given project.
     */
    Experiment newExperiment(Project project);

    /**
     * Deletes the experiment and any associated runs and labels.
     */
    void deleteExperiment(Experiment experiment);

    /**
     * Updates experiment details.
     */
    void updateExperiment(Experiment experiment);

    /**
     * @return the list of experiments for a given project.
     */
    List<Experiment> getExperimentsForProject(Project project, boolean includeArchived);

    /**
     * Saves label to storage for the given experiment ID.
     */
    void addLabel(String experimentId, Label label);

    void addApplicationLabel(String experimentId, ApplicationLabel label);

    /**
     * @return the list of labels for a given experiment
     */
    List<Label> getLabelsForExperiment(Experiment experiment);

    List<Label> getLabelsWithStartId(String startLabelId);

    List<ApplicationLabel> getApplicationLabelsWithStartId(String startLabelId);

    void setStats(String startLabelId, String sensorId, TrialStats stats);

    TrialStats getStats(String startLabelId, String sensorId);

    List<String> getExperimentRunIds(String experimentId, boolean includeArchived);

    /**
     * Updates the value and timestamp of a label in the database.
     * @param updatedLabel
     */
    void editLabel(Label updatedLabel);

    void editApplicationLabel(ApplicationLabel updatedLabel);

    void deleteLabel(Label label);

    /**
     * Gets all the external sensors previously saved.
     * @param providerMap
     */
    Map<String, ExternalSensorSpec> getExternalSensors(
            Map<String, ExternalSensorProvider> providerMap);

    /**
     * Gets the external sensor or {@null} if no sensor with that ID was added.
     */
    ExternalSensorSpec getExternalSensorById(String id,
            Map<String, ExternalSensorProvider> providerMap);

    /**
     *
     * @param sensor
     * @param providerMap
     * @return
     */
    String addOrGetExternalSensor(ExternalSensorSpec sensor,
            Map<String, ExternalSensorProvider> providerMap);

    /**
     * Removes an external sensor from the database.
     * <p>Note that this will also delete it from any experiments linked to that sensor which could
     * affect display of saved experiments.
     * </p>
     */
    void removeExternalSensor(String databaseTag);

    /**
     * Adds a linkage between an experiment and a sensor, which could be external or internal.
     */
    void addSensorToExperiment(String databaseTag, String experimentId);

    /**
     * Removes a linkage between an experiment and a sensor, which could be external or internal.
     */
    void removeSensorFromExperiment(String databaseTag, String experimentId);

    /**
     * Gets all the external sensors which are linked to an experiment, in insertion order
     */
    ExperimentSensors getExperimentExternalSensors(String experimentId,
            Map<String, ExternalSensorProvider> providerMap);

    /**
     * Adds this device as one to be remembered as "my device" in the manage devices screen from
     * here on out.
     */
    void addMyDevice(InputDeviceSpec deviceSpec);

    /**
     * Removes this device from "my devices"
     */
    void removeMyDevice(InputDeviceSpec deviceSpec);

    /**
     * @return all of "my devices", in insertion order
     */
    List<InputDeviceSpec> getMyDevices();

    /**
     * @returns Last used experiment.
     */
    Experiment getLastUsedExperiment();

    /**
     * @returns Last used project.
     */
    Project getLastUsedProject();

    void updateLastUsedProject(Project project);

    void updateLastUsedExperiment(Experiment experiment);

    /**
     * @param experiment which experiment this trial is attached to
     * @param trialId the label that marks the start of this trial
     * @param sensorLayouts sensor layouts of sensors recording during the trial
     * @return a new Trial object, which has been stored in the database
     */
    Trial newTrial(Experiment experiment, String trialId, long startTimestamp,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts);

    /**
     * @param trialId
     * @return the Trial stored with that id, or null if no such Trial exists.
     */
    Trial getTrial(String trialId, List<ApplicationLabel> applicationLabels);

    /**
     * Set the sensor selection and layout for an experiment
     */
    void setExperimentSensorLayouts(String experimentId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts);

    /**
     * Retrieve the sensor selection and layout for an experiment
     */
    List<GoosciSensorLayout.SensorLayout> getExperimentSensorLayouts(String experimentId);

    /**
     * Updates a sensor layout in a given position for an experiment. If the experimentID is
     * invalid or the position is too large for that experimentID, nothing happens.
     */
    void updateSensorLayout(String experimentId, int position,
            GoosciSensorLayout.SensorLayout layout);

    void close();

    /**
     * Updates a trial.
     */
    void updateTrial(Trial trial);

    /**
     * Deletes a trial and any of its associated labels.
     * @param trialId The ID of the trial to delete
     */
    void deleteTrial(String trialId);

    /**
     * Adds a new trigger.
     * @param trigger
     * @param experimentId The experiment active when the trigger was first added. Note that this is
     *                     currently not used for retrieval.
     */
    void addSensorTrigger(SensorTrigger trigger, String experimentId);

    /**
     * Updates an existing SensorTrigger. note that only the last used timestamp and
     * TriggerInformation can be mutated.
     * @param trigger
     */
    void updateSensorTrigger(SensorTrigger trigger);

    /**
     * Gets a list of SensorTrigger by their IDs.
     * @param triggerIds
     */
    List<SensorTrigger> getSensorTriggers(String[] triggerIds);

    /**
     * Gets a list of sensor triggers that are applicable to a given Sensor ID.
     * TODO: Experiment could be added to these params if we decide that is reasonable.
     * @param sensorId
     * @return A list of SensorTriggers that apply to that Sensor ID
     */
    List<SensorTrigger> getSensorTriggersForSensor(String sensorId);

    /**
     * Deletes the SensorTrigger from the database.
     */
    void deleteSensorTrigger(SensorTrigger trigger);

    /**
     * Updates the layouts for a trial (usually to reflect any changes between starting to
     * record and stopping.
     */
    void updateTrialLayouts(String trialId, List<GoosciSensorLayout.SensorLayout> layouts);
}
