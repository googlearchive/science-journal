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

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.MetaDataManager;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.SensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public class DataControllerImpl implements DataController, RecordingDataController {
    private final SensorDatabase mSensorDatabase;
    private final Executor mUiThread;
    private final Executor mMetaDataThread;
    private final Executor mSensorDataThread;
    private MetaDataManager mMetaDataManager;
    private Clock mClock;
    private Map<String, FailureListener> mSensorFailureListeners = new HashMap<>();
    private final Map<String, ExternalSensorProvider> mProviderMap;
    private long mPrevLabelTimestamp = 0;

    public DataControllerImpl(SensorDatabase sensorDatabase, Executor uiThread,
            Executor metaDataThread,
            Executor sensorDataThread, MetaDataManager metaDataManager, Clock clock,
            Map<String, ExternalSensorProvider> providerMap) {
        mSensorDatabase = sensorDatabase;
        mUiThread = uiThread;
        mMetaDataThread = metaDataThread;
        mSensorDataThread = sensorDataThread;
        mMetaDataManager = metaDataManager;
        mClock = clock;
        mProviderMap = providerMap;
    }

    public void replaceSensorInExperiment(final String experimentId, final String oldSensorId,
            final String newSensorId, final MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.removeSensorFromExperiment(oldSensorId, experimentId);
                mMetaDataManager.addSensorToExperiment(newSensorId, experimentId);
                replaceIdInLayouts(experimentId, oldSensorId, newSensorId);
                return Success.SUCCESS;
            }
        });
    }

    private void replaceIdInLayouts(String experimentId, String oldSensorId, String newSensorId) {
        List<GoosciSensorLayout.SensorLayout> layouts = mMetaDataManager.getExperimentSensorLayouts(
                experimentId);
        for (GoosciSensorLayout.SensorLayout layout : layouts) {
            if (layout.sensorId.equals(oldSensorId)) {
                layout.sensorId = newSensorId;
            }
        }
        mMetaDataManager.setExperimentSensorLayouts(experimentId, layouts);
    }

    public void stopRun(final Experiment experiment, final String runId,
            final List<GoosciSensorLayout.SensorLayout> layouts,
            final MaybeConsumer<ApplicationLabel> onSuccess) {
        addApplicationLabel(experiment, ApplicationLabel.TYPE_RECORDING_STOP, runId,
                MaybeConsumers.chainFailure(onSuccess, new Consumer<ApplicationLabel>() {
                    @Override
                    public void take(final ApplicationLabel label) {
                        background(DataControllerImpl.this.mMetaDataThread, onSuccess,
                                new Callable<ApplicationLabel>() {
                                    @Override
                                    public ApplicationLabel call() throws Exception {
                                        mMetaDataManager.updateTrialLayouts(runId, layouts);
                                        return label;
                                    }
                                });
                    }
                }));
    }

    @Override
    public void updateTrial(final Trial trial, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.updateTrial(trial);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void deleteRun(final ExperimentRun run, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.deleteTrial(run.getTrialId());
                removeRunSensorData(run);
                return Success.SUCCESS;
            }
        });
    }

    private void removeRunSensorData(final ExperimentRun run) {
        mSensorDataThread.execute(new Runnable() {

            @Override
            public void run() {
                TimeRange times = TimeRange.oldest(Range.closed(run.getFirstTimestamp(),
                        run.getLastTimestamp()));
                for (String tag : run.getSensorIds()) {
                    mSensorDatabase.deleteScalarReadings(tag, times);
                }
            }
        });
    }

    @Override
    public void addScalarReading(final String sensorId, final int resolutionTier,
            final long timestampMillis, final double value) {
        mSensorDataThread.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mSensorDatabase.addScalarReading(sensorId, resolutionTier, timestampMillis,
                            value);
                } catch (final Exception e) {
                    mUiThread.execute(new Runnable() {
                        @Override
                        public void run() {
                            notifyFailureListener(sensorId, e);
                        }
                    });
                }
            }
        });
    }

    private void notifyFailureListener(String sensorId, Exception e) {
        FailureListener listener = mSensorFailureListeners.get(sensorId);
        if (listener != null) {
            listener.fail(e);
        }
    }

    @Override
    public void getScalarReadings(final String databaseTag, final int resolutionTier,
            final TimeRange timeRange, final int maxRecords,
            final MaybeConsumer<ScalarReadingList> onSuccess) {
        Preconditions.checkNotNull(databaseTag);
        background(mSensorDataThread, onSuccess, new Callable<ScalarReadingList>() {
            @Override
            public ScalarReadingList call() throws Exception {
                return mSensorDatabase.getScalarReadings(databaseTag, timeRange, resolutionTier,
                        maxRecords);
            }
        });
    }

    public void addLabel(final Label label, final String experimentId, final String trialId,
            final MaybeConsumer<Label> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Label>() {
            @Override
            public Label call() throws Exception {
                mMetaDataManager.addLabel(experimentId, trialId, label);
                return label;
            }
        });
    }

    @Override
    public void addExperimentLabel(Label label, String experimentId,
            MaybeConsumer<Label> onSuccess) {
        // An experiment label has a Trial ID of NOT_RECORDING_RUN_ID.
        addLabel(label, experimentId, RecorderController.NOT_RECORDING_RUN_ID, onSuccess);
    }

    @Override
    public void addApplicationLabel(final ApplicationLabel label,
            MaybeConsumer<ApplicationLabel> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<ApplicationLabel>() {
            @Override
            public ApplicationLabel call() throws Exception {
                mMetaDataManager.addApplicationLabel(label.getExperimentId(), label);
                return label;
            }
        });
    }

    @Override
    public void editLabel(final Label updatedLabel, final MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.editLabel(updatedLabel);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void editApplicationLabel(final ApplicationLabel updatedLabel,
            MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.editApplicationLabel(updatedLabel);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void deleteLabel(final Label label, final MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.deleteLabel(label);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void getLastUsedProject(MaybeConsumer<Project> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Project>() {
            @Override
            public Project call() throws Exception {
                return mMetaDataManager.getLastUsedProject();
            }
        });
    }

    @Override public void startRun(
            final Experiment experiment, final List<GoosciSensorLayout.SensorLayout> sensorLayouts,
            final MaybeConsumer<ApplicationLabel> onSuccess) {
        String id = generateNewLabelId();
        addApplicationLabelWithId(experiment, ApplicationLabel.TYPE_RECORDING_START, id, id,
                MaybeConsumers.chainFailure(onSuccess, new Consumer<ApplicationLabel>() {
                    @Override
                    public void take(final ApplicationLabel label) {
                        background(DataControllerImpl.this.mMetaDataThread, onSuccess,
                                new Callable<ApplicationLabel>() {
                                    @Override
                                    public ApplicationLabel call() throws Exception {
                                        mMetaDataManager.newTrial(experiment, label.getTrialId(),
                                                label.getTimeStamp(), sensorLayouts);
                                        return label;
                                    }
                                });
                    }
                }));
    }

    private void addApplicationLabel(
            final Experiment experiment, final @ApplicationLabel.Type int type,
            final String startLabelId, final MaybeConsumer<ApplicationLabel> onSuccess) {
        addApplicationLabelWithId(experiment, type, generateNewLabelId(), startLabelId, onSuccess);
    }

    private void addApplicationLabelWithId(
            final Experiment experiment, final @ApplicationLabel.Type int type, final String id,
            final String startLabelId, final MaybeConsumer<ApplicationLabel> onSuccess) {
        // Adds an application label with the given ID and startLabelId.
        background(mMetaDataThread, onSuccess, new Callable<ApplicationLabel>() {
            @Override
            public ApplicationLabel call() throws Exception {
                final ApplicationLabel label = new ApplicationLabel(type, id, startLabelId,
                        mClock.getNow());
                mMetaDataManager.addApplicationLabel(experiment.getExperimentId(), label);
                return label;
            }
        });
    }

    @Override
    public void createExperiment(final Project project,
                                 final MaybeConsumer<Experiment> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Experiment>() {
            @Override
            public Experiment call() throws Exception {
                Experiment experiment = mMetaDataManager.newExperiment(project);
                mMetaDataManager.updateLastUsedExperiment(experiment);
                mMetaDataManager.updateLastUsedProject(project);
                return experiment;
            }
        });
    }

    @Override
    public void deleteExperiment(final Experiment experiment,
                                 final MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {

            @Override
            public Success call() throws Exception {
                deleteExperimentOnDataThread(experiment);
                return Success.SUCCESS;
            }
        });
    }

    private void deleteExperimentOnDataThread(Experiment experiment) {
        // TODO: delete invalid run data, as well (b/35794788)
        List<ExperimentRun> runsToDelete = getExperimentRunsOnDataThread(
                experiment.getExperimentId(), true, false);
        mMetaDataManager.deleteExperiment(experiment);
        for (ExperimentRun run : runsToDelete) {
            removeRunSensorData(run);
        }
    }

    @Override
    public void getExperimentById(final String experimentId,
                                  final MaybeConsumer<Experiment> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Experiment>() {
            @Override
            public Experiment call() throws Exception {
                return mMetaDataManager.getExperimentById(experimentId);
            }
        });
    }

    @Override
    public void updateExperiment(final Experiment experiment, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.updateExperiment(experiment);
                return Success.SUCCESS;
            }
        });

    }

    @Override
    public String generateNewLabelId() {
        long nextLabelTimestamp = mClock.getNow();
        if (nextLabelTimestamp <= mPrevLabelTimestamp) {
            // Make sure we never use the same label ID twice.
            nextLabelTimestamp = mPrevLabelTimestamp + 1;
        }
        mPrevLabelTimestamp = nextLabelTimestamp;
        return "label_" + nextLabelTimestamp;
    }

    // TODO(saff): test
    @Override
    public void getExperimentRun(final String experimentId, final String startLabelId,
            final MaybeConsumer<ExperimentRun> onSuccess) {
        Preconditions.checkNotNull(startLabelId);
        background(mMetaDataThread, onSuccess, new Callable<ExperimentRun>() {
            @Override
            public ExperimentRun call() throws Exception {
                return buildExperimentRunOnDataThread(experimentId, startLabelId);
            }
        });
    }

    @Override
    public void getExperimentRuns(final String experimentId, final boolean includeArchived,
            final boolean includeInvalid, final MaybeConsumer<List<ExperimentRun>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<List<ExperimentRun>>() {
            @Override
            public List<ExperimentRun> call() throws Exception {
                return getExperimentRunsOnDataThread(experimentId, includeArchived, includeInvalid);
            }
        });
    }

    private List<ExperimentRun> getExperimentRunsOnDataThread(final String experimentId,
            final boolean includeArchived, boolean includeInvalid) {
        final List<ExperimentRun> runs = new ArrayList<>();
        List<String> startLabelIds = mMetaDataManager.getExperimentRunIds(experimentId,
                includeArchived);
        for (String startLabelId : startLabelIds) {
            ExperimentRun run = buildExperimentRunOnDataThread(experimentId, startLabelId);
            if (run.isValidRun() || includeInvalid) {
                runs.add(run);
            }
        }
        return runs;
    }

    private ExperimentRun buildExperimentRunOnDataThread(String experimentId, String startLabelId) {
        final List<Label> labels = mMetaDataManager.getLabelsForTrial(startLabelId);
        final List<ApplicationLabel> applicationLabels =
                mMetaDataManager.getApplicationLabelsWithStartId(startLabelId);
        Trial trial = mMetaDataManager.getTrial(startLabelId, applicationLabels, labels);
        return ExperimentRun.fromLabels(trial, experimentId, applicationLabels);
    }

    @Override public void createProject(final MaybeConsumer<Project> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Project>() {
            @Override
            public Project call() throws Exception {
                Project project = mMetaDataManager.newProject();
                mMetaDataManager.updateLastUsedProject(project);
                return project;
            }
        });
    }

    @Override
    public void updateProject(final Project project, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.updateProject(project);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void deleteProject(final Project project, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                for (Experiment e : mMetaDataManager.getExperimentsForProject(project, true)) {
                    deleteExperimentOnDataThread(e);
                }
                mMetaDataManager.deleteProject(project);
                return Success.SUCCESS;
            }
        });
    }

    @Override public void getProjects(final int maxNumber, final boolean includeArchived,
                                      final MaybeConsumer<List<Project>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<List<Project>>() {
            @Override
            public List<Project> call() throws Exception {
                return mMetaDataManager.getProjects(maxNumber, includeArchived);
            }
        });
    }

    @Override public void getExperimentsForProject(final Project project,
            final boolean includeArchived, final MaybeConsumer<List<Experiment>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<List<Experiment>>() {
            @Override
            public List<Experiment> call() throws Exception {
                return mMetaDataManager.getExperimentsForProject(project, includeArchived);
            }
        });
    }

    @Override
    public void getProjectById(final String projectId, final MaybeConsumer<Project> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Project>() {
            @Override
            public Project call() throws Exception {
                return mMetaDataManager.getProjectById(projectId);
            }
        });
    }

    @Override
    public void getExternalSensors(final MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Map<String, ExternalSensorSpec>>() {
            @Override
            public Map<String, ExternalSensorSpec> call() throws Exception {
                return mMetaDataManager.getExternalSensors(mProviderMap);
            }
        });
    }

    @Override
    public void getExternalSensorsByExperiment(final String experimentId,
            final MaybeConsumer<ExperimentSensors> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<ExperimentSensors>() {
            @Override
            public ExperimentSensors call() throws Exception {
                return mMetaDataManager.getExperimentExternalSensors(experimentId, mProviderMap);
            }
        });
    }


    @Override
    public void getExternalSensorById(final String id,
                                      final MaybeConsumer<ExternalSensorSpec> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<ExternalSensorSpec>() {
            @Override
            public ExternalSensorSpec call() throws Exception {
                return mMetaDataManager.getExternalSensorById(id, mProviderMap);
            }
        });
    }

    @Override
    public void addSensorToExperiment(final String experimentId, final String sensorId,
            MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.addSensorToExperiment(sensorId, experimentId);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void removeSensorFromExperiment(final String experimentId, final String sensorId,
            MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.removeSensorFromExperiment(sensorId, experimentId);
                replaceIdInLayouts(experimentId, sensorId, "");
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void getLabelsForExperiment(final Experiment experiment,
                                       MaybeConsumer<List<Label>> onSuccess) {
        Preconditions.checkNotNull(experiment);
        background(mMetaDataThread, onSuccess, new Callable<List<Label>>() {
            @Override
            public List<Label> call() throws Exception {
                return mMetaDataManager.getLabelsForExperiment(experiment);
            }
        });
    }

    @Override
    public void getLabelsForTrial(final String trialId, MaybeConsumer<List<Label>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<List<Label>>() {
            @Override
            public List<Label> call() throws Exception {
                return mMetaDataManager.getLabelsForTrial(trialId);
            }
        });
    }

    @Override
    public void updateLastUsedExperiment(
            final Experiment experiment, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.updateLastUsedExperiment(experiment);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void getStats(final String runId, final String sensorId,
            MaybeConsumer<TrialStats> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<TrialStats>() {
            @Override
            public TrialStats call() throws Exception {
                return mMetaDataManager.getStats(runId, sensorId);
            }
        });
    }

    @Override
    public void setSensorStatsStatus(final String runId, final String sensorId, final int status,
            MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                TrialStats trialStats = mMetaDataManager.getStats(runId, sensorId);
                trialStats.setStatStatus(status);
                mMetaDataManager.setStats(runId, sensorId, trialStats);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void updateTrialStats(final String runId, final String sensorId,
            final TrialStats trialStats, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.setStats(runId, sensorId, trialStats);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void setStats(final String runId, final String sensorId, final TrialStats trialStats,
            final MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.setStats(runId, sensorId, trialStats);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void setDataErrorListenerForSensor(String sensorId, FailureListener listener) {
        mSensorFailureListeners.put(sensorId, listener);
    }

    @Override
    public void clearDataErrorListenerForSensor(String sensorId) {
        mSensorFailureListeners.remove(sensorId);
    }

    @Override
    public void setSensorLayouts(final String experimentId,
            final List<GoosciSensorLayout.SensorLayout> layouts, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.setExperimentSensorLayouts(experimentId, layouts);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void getSensorLayouts(final String experimentId,
            MaybeConsumer<List<GoosciSensorLayout.SensorLayout>> onSuccess) {
        background(mMetaDataThread, onSuccess,
                new Callable<List<GoosciSensorLayout.SensorLayout>>() {
            @Override
            public List<GoosciSensorLayout.SensorLayout> call() throws Exception {
                List<GoosciSensorLayout.SensorLayout> sensorLayout =
                        mMetaDataManager.getExperimentSensorLayouts(experimentId);
                return sensorLayout;
            }
        });
    }

    @Override
    public void updateSensorLayout(final String experimentId, final int position,
            final GoosciSensorLayout.SensorLayout layout, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.updateSensorLayout(experimentId, position, layout);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void addOrGetExternalSensor(final ExternalSensorSpec sensor,
            final MaybeConsumer<String> onSensorId) {
        background(mMetaDataThread, onSensorId, new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mMetaDataManager.addOrGetExternalSensor(sensor, mProviderMap);
            }
        });
    }

    private <T> void background(Executor dataThread, final MaybeConsumer<T> onSuccess,
            final Callable<T> job) {
        dataThread.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final T result = job.call();
                    mUiThread.execute(new Runnable() {
                        @Override
                        public void run() {
                            onSuccess.success(result);
                        }
                    });
                } catch (final Exception e) {
                    mUiThread.execute(new Runnable() {
                        @Override
                        public void run() {
                            onSuccess.fail(e);
                        }
                    });
                }
            }
        });
    }


    @Override
    public void addSensorTrigger(final SensorTrigger trigger, final String experimentId,
            MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.addSensorTrigger(trigger, experimentId);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void updateSensorTrigger(final SensorTrigger trigger, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.updateSensorTrigger(trigger);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void getSensorTriggers(final String[] triggerIds,
            MaybeConsumer<List<SensorTrigger>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<List<SensorTrigger>>() {
            @Override
            public List<SensorTrigger> call() throws Exception {
                return mMetaDataManager.getSensorTriggers(triggerIds);
            }
        });
    }

    @Override
    public void getSensorTriggersForSensor(final String sensorId,
            MaybeConsumer<List<SensorTrigger>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<List<SensorTrigger>>() {
            @Override
            public List<SensorTrigger> call() throws Exception {
                return mMetaDataManager.getSensorTriggersForSensor(sensorId);
            }
        });
    }

    @Override
    public void deleteSensorTrigger(final SensorTrigger trigger, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.deleteSensorTrigger(trigger);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void getMyDevices(MaybeConsumer<List<InputDeviceSpec>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<List<InputDeviceSpec>>() {
            @Override
            public List<InputDeviceSpec> call() throws Exception {
                return mMetaDataManager.getMyDevices();
            }
        });
    }

    @Override
    public void addMyDevice(final InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.addMyDevice(spec);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void forgetMyDevice(final InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.removeMyDevice(spec);
                return Success.SUCCESS;
            }
        });
    }
}
