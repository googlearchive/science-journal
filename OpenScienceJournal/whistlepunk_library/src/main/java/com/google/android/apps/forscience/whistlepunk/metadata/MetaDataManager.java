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
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;

import java.util.List;
import java.util.Map;

/**
 * Loads and saves meta data.
 */
public interface MetaDataManager {

    /**
     * @return the project with matching projectId
     */
    public Project getProjectById(String projectId);

    /**
     * @return the list of saved projects
     */
    public List<Project> getProjects(int maxNumber, boolean archived);

    /**
     * @return a new project with a random ID.
     */
    public Project newProject();

    /**
     * Updates project data.
     */
    public void updateProject(Project project);

    /**
     * Removes a project, all its associated experiments and labels.
     */
    public void deleteProject(Project project);

    public Experiment getExperimentById(String experimentId);

    /**
     * Creates a new experiment for a given project.
     */
    public Experiment newExperiment(Project project);

    /**
     * Deletes the experiment and any associated runs and labels.
     */
    public void deleteExperiment(Experiment experiment);

    /**
     * Updates experiment details.
     */
    public void updateExperiment(Experiment experiment);

    /**
     * @return the list of experiments for a given project.
     */
    public List<Experiment> getExperimentsForProject(Project project, boolean includeArchived);

    /**
     * Saves label to storage.
     */
    public void addLabel(Experiment experiment, Label label);

    /**
     * Saves label to storage for the given experiment ID.
     */
    public void addLabel(String experimentId, Label label);

    /**
     * @return the list of labels for a given experiment
     */
    public List<Label> getLabelsForExperiment(Experiment experiment);

    List<Label> getLabelsWithStartId(String startLabelId);

    void setStats(String startLabelId, String sensorId, RunStats stats);

    RunStats getStats(String startLabelId, String sensorId);

    List<String> getExperimentRunIds(String experimentId, boolean includeArchived);

    /**
     * Updates the value and timestamp of a label in the database.
     * @param updatedLabel
     */
    void editLabel(Label updatedLabel);

    void deleteLabel(Label label);

    /**
     * Gets all the external sensors previously saved.
     * @param providerMap
     */
    public Map<String, ExternalSensorSpec> getExternalSensors(
            Map<String, ExternalSensorProvider> providerMap);

    /**
     * Gets the external sensor or {@null} if no sensor with that ID was added.
     */
    public ExternalSensorSpec getExternalSensorById(String id,
            Map<String, ExternalSensorProvider> providerMap);

    /**
     *
     * @param sensor
     * @param providerMap
     * @return
     */
    public String addOrGetExternalSensor(ExternalSensorSpec sensor,
            Map<String, ExternalSensorProvider> providerMap);

    /**
     * Removes an external sensor from the database.
     * <p>Note that this will also delete it from any experiments linked to that sensor which could
     * affect display of saved experiments.
     * </p>
     */
    public void removeExternalSensor(String databaseTag);

    /**
     * Adds a linkage between an experiment and a sensor, which could be external or internal.
     */
    public void addSensorToExperiment(String databaseTag, String experimentId);

    /**
     * Removes a linkage between an experiment and a sensor, which could be external or internal.
     */
    public void removeSensorFromExperiment(String databaseTag, String experimentId);

    /**
     * Gets all the external sensors which are linked to an experiment, in insertion order
     */
    public List<ConnectableSensor> getExperimentExternalSensors(String experimentId,
            Map<String, ExternalSensorProvider> providerMap);

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
     * @param experiment which experiment this run is attached to
     * @param runId the label that marks the start of this run
     * @param sensorLayouts sensor layouts of sensors recording during the run
     * @return a new Run object, which has been stored in the database
     */
    Run newRun(Experiment experiment, String runId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts);

    /**
     * @param runId
     * @return the Run stored with that id, or null if no such Run exists.
     */
    Run getRun(String runId);

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
     * Updates a run.
     */
    void updateRun(Run run);

    /**
     * Deletes a run and any of its associated labels.
     * @param runId The ID of the run to delete
     */
    void deleteRun(String runId);

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
}
