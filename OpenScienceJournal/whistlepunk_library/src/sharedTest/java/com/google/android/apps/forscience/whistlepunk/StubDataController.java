package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.metadata.Run;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
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
    public void addLabel(Label label, MaybeConsumer<Label> onSuccess) {

    }

    @Override
    public void startRun(Experiment experiment, MaybeConsumer<ApplicationLabel> onSuccess) {

    }

    @Override
    public void stopRun(Experiment experiment, String runId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts,
            MaybeConsumer<ApplicationLabel> onSuccess) {

    }

    @Override
    public void updateRun(Run run, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void deleteRun(String runId, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void createExperiment(Project project, MaybeConsumer<Experiment> onSuccess) {

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
    public void getExperimentRun(String startLabelId, MaybeConsumer<ExperimentRun> onSuccess) {

    }

    @Override
    public void getExperimentRuns(String experiment, boolean includeArchived,
            MaybeConsumer<List<ExperimentRun>> onSuccess) {

    }

    @Override
    public void createProject(MaybeConsumer<Project> onSuccess) {

    }

    @Override
    public void updateProject(Project project, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void deleteProject(Project project, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void getProjects(int maxNumber, boolean includeArchived,
            MaybeConsumer<List<Project>> onSuccess) {

    }

    @Override
    public void editLabel(Label updatedLabel, MaybeConsumer<Label> onSuccess) {

    }

    @Override
    public void deleteLabel(Label label, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public String generateNewLabelId() {
        return null;
    }

    @Override
    public void getLastUsedProject(MaybeConsumer<Project> onSuccess) {

    }

    @Override
    public void getExperimentsForProject(Project project, boolean includeArchived,
            MaybeConsumer<List<Experiment>> onSuccess) {

    }

    @Override
    public void getProjectById(String projectId, MaybeConsumer<Project> onSuccess) {

    }

    @Override
    public void getExternalSensors(MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess) {

    }

    @Override
    public void getExternalSensorsByExperiment(String experimentId,
            MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess) {

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
    public void getLabelsForExperiment(Experiment experiment,
            MaybeConsumer<List<Label>> onSuccess) {

    }

    @Override
    public void updateLastUsedExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void getStats(String runId, String sensorId, MaybeConsumer<RunStats> onSuccess) {

    }

    @Override
    public void getExperimentStats(String experimentId,
            MaybeConsumer<Map<String, RunStats>> onSuccess) {

    }

    @Override
    public void setSensorLayouts(String experimentId, List<GoosciSensorLayout.SensorLayout> layouts,
            MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void getSensorLayouts(String experimentId,
            MaybeConsumer<List<GoosciSensorLayout.SensorLayout>> onSuccess) {

    }

    @Override
    public void updateSensorLayout(String experimentId, int position,
            GoosciSensorLayout.SensorLayout layout, MaybeConsumer<Success> onSuccess) {

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
    public void addSensorTrigger(SensorTrigger trigger, String experimentId,
            MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void updateSensorTrigger(SensorTrigger trigger, MaybeConsumer<Success> onSuccess) {

    }

    @Override
    public void getSensorTriggers(String[] triggerIds,
            MaybeConsumer<List<SensorTrigger>> onSuccess) {

    }

    @Override
    public void getSensorTriggersForSensor(String sensorId,
            MaybeConsumer<List<SensorTrigger>> onSuccess) {

    }

    @Override
    public void deleteSensorTrigger(SensorTrigger trigger, MaybeConsumer<Success> onSuccess) {

    }
}
