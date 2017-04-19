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

import android.content.Context;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a Science Journal experiment.
 * All changes should be made using the getters and setters provided, rather than by getting the
 * underlying protocol buffer and making changes to that directly. Changes to the underlying proto
 * outside this class may be overwritten and may not be saved.
 */
public class Experiment extends LabelListHolder {
    private boolean mArchived;
    private GoosciExperiment.Experiment mProto;
    private List<GoosciSensorLayout.SensorLayout> mSensorLayouts;
    private List<GoosciExperiment.ExperimentSensor> mExperimentSensors;
    private ArrayListMultimap<String, SensorTrigger> mSensorTriggers;
    private List<Trial> mTrials;

    public static Experiment newExperiment(long creationTime) {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();
        proto.lastUsedTimeMs = creationTime;
        proto.creationTimeMs = creationTime;
        return new Experiment(proto, false);
    }

    /**
     * Populates the Experiment from an existing proto.
     */
    public static Experiment fromExperiment(GoosciExperiment.Experiment experiment,
            boolean isArchived) {
        return new Experiment(experiment, isArchived);
    }

    // Archived state is set per account, so if you archive something on one device and share it
    // it will not show up as archived on another account. Therefore it is stored outside of the
    // experiment proto.
    private Experiment(GoosciExperiment.Experiment experimentProto, boolean isArchived) {
        mProto = experimentProto;
        mArchived = isArchived;
        mLabels = new ArrayList<>();
        for (GoosciLabel.Label labelProto : mProto.labels) {
            mLabels.add(Label.fromLabel(labelProto));
        }
        mTrials = new ArrayList<>();
        for (GoosciTrial.Trial trial : mProto.trials) {
            mTrials.add(Trial.fromTrial(trial));
        }
    }

    /**
     * Gets the proto underlying this experiment. The resulting proto should *not* be modified
     * outside of this class because changes to it will not be saved.
     * @return The experiment's underlying protocolbuffer.
     */
    public GoosciExperiment.Experiment getExperimentProto() {
        updateExperimentProto();
        return mProto;
    }

    public long getTimestamp() {
        return mProto.creationTimeMs;
    }

    public boolean isArchived() {
        return mArchived;
    }

    public void setArchived(boolean archived) {
        mArchived = archived;
    }

    public String getTitle() {
        return mProto.title;
    }

    public void setTitle(String title) {
        mProto.title = title;
    }

    public String getDisplayTitle(Context context) {
        return !TextUtils.isEmpty(getTitle()) ? getTitle() : context.getString(
                R.string.default_experiment_name);
    }

    public String getDescription() {
        return mProto.description;
    }

    public void setDescription(String description) {
        mProto.description = description;
    }

    public long getLastUsedTime() {
        return mProto.lastUsedTimeMs;
    }

    public void setLastUsedTime(long lastUsedTime) {
        mProto.lastUsedTimeMs = lastUsedTime;
    }

    /**
     * Gets the labels which fall during a certain time range.
     * Objects in this list should not be modified and expect that state to be saved, instead
     * editing of labels should happen using updateLabel, addLabel, removeLabel.
     * @param range The time range in which to search for labels
     * @return A list of labels in that range, or an empty list if none are found.
     */
    public List<Label> getLabelsForRange(GoosciTrial.Range range) {
        List<Label> result = new ArrayList<>();
        for (Label label : getLabels()) {
            if (range.startMs <= label.getTimeStamp() && range.endMs >= label.getTimeStamp()) {
                result.add(label);
            } else if (range.endMs < label.getTimeStamp()) {
                // These are sorted, so we can stop looking once we've found labels that are too
                // recent.
                break;
            }
        }
        return result;
    }

    /**
     * Temporary method used to populate labels from the database.
     * TODO: Deprecate this after moving to a file-based system where labels are stored as
     * part of the proto and don't need a separate set.
     * @param labels
     */
    public void populateLabels(List<Label> labels) {
        setLabels(labels);
    }

    /**
     * Gets the current list of trials in this experiment.
     * Objects in this list should not be modified and expect that state to be saved, instead
     * editing of trials should happen using updateTrial, addTrial, removeTrial.
     */
    public List<Trial> getTrials() {
        return mTrials;
    }

    @VisibleForTesting
    void setTrials(List<Trial> trials) {
        mTrials = Preconditions.checkNotNull(trials);
    }

    /**
     * Get the count separately from getting all the trials avoids unnecessary processing if the
     * caller just has to know how many there are.
     * @return The number of trials in this experiment.
     */
    public int getTrialCount() {
        return mTrials.size();
    }

    /**
     * Gets a trial by its unique ID.
     * Note that Trial IDs are only guarenteed to be unqiue in an experiment.
     */
    public Trial getTrial(String trialId) {
        for (Trial trial : mTrials) {
            if (TextUtils.equals(trial.getTrialId(), trialId)) {
                return trial;
            }
        }
        return null;
    }

    /**
     * Updates a trial. If the trial is not yet in the experiment, it gets added.
     * @param trial
     */
    public void updateTrial(Trial trial) {
        for (int i = 0; i < mTrials.size(); i++) {
            Trial next = mTrials.get(i);
            if (TextUtils.equals(trial.getTrialId(), next.getTrialId())) {
                mTrials.set(i, trial);
                break;
            }
        }
        // Update may involve crop, so re-sort just in case!
        sortTrials();
    }

    /**
     * Adds a new trial to the experiment.
     * @param trial
     */
    public void addTrial(Trial trial) {
        mTrials.add(trial);
        sortTrials();
    }

    /**
     * Removes a trial from the experiment.
     */
    public void deleteTrial(Trial trial) {
        trial.deleteContents();
        mTrials.remove(trial);
    }

    private void sortTrials() {
        Collections.sort(mTrials, Trial.COMPARATOR_BY_TIMESTAMP);
    }

    public List<GoosciSensorLayout.SensorLayout> getSensorLayouts() {
        if (mSensorLayouts == null) {
            mSensorLayouts = new ArrayList<>(Arrays.asList(mProto.sensorLayouts));
        }
        return mSensorLayouts;
    }

    public void setSensorLayouts(List<GoosciSensorLayout.SensorLayout> layouts) {
        mSensorLayouts = Preconditions.checkNotNull(layouts);
    }

    public List<GoosciExperiment.ExperimentSensor> getExperimentSensors() {
        if (mExperimentSensors == null) {
            mExperimentSensors = new ArrayList<>(Arrays.asList(mProto.experimentSensors));
        }
        return mExperimentSensors;
    }

    public void setExperimentSensors(List<GoosciExperiment.ExperimentSensor> experimentSensors) {
        mExperimentSensors = Preconditions.checkNotNull(experimentSensors);
    }

    /**
     * Gets the current list of sensor triggers in this experiment for a particular sensor.
     * Objects in this list should not be modified and expect that state to be saved, instead
     * editing of triggers should happen using update/add/remove functions.
     */
    public List<SensorTrigger> getSensorTriggers(String sensorId) {
        if (mSensorTriggers == null) {
            mSensorTriggers = ArrayListMultimap.create();
            for (GoosciSensorTrigger.SensorTrigger proto : mProto.sensorTriggers) {
                mSensorTriggers.put(sensorId, new SensorTrigger(proto));
            }
        }
        return mSensorTriggers.get(sensorId);
    }

    @VisibleForTesting
    void setSensorTriggers(ArrayListMultimap<String, SensorTrigger> sensorTriggers) {
        mSensorTriggers = Preconditions.checkNotNull(sensorTriggers);
    }

    /**
     * Updates the sensor triggers for a sensor.
     * @param sensorId
     * @param triggers
     */
    public void updateSensorTriggers(String sensorId, List<SensorTrigger> triggers) {
        // TODO: Do this by index? Or update a whole list at once? We can't use a trigger to update
        // a trigger because it has no ID!
        mSensorTriggers.replaceValues(sensorId, triggers);
    }

    /**
     * Updates the experiment protocol buffer. This should be done before the protocol buffer is
     * saved because otherwise it may not reflect the latest changes to the experiment.
     */
    private void updateExperimentProto() {
        // All local fields that represent experiment state must be merged back into the proto here.

        if (mSensorLayouts != null) {
            mProto.sensorLayouts = mSensorLayouts.toArray(
                    new GoosciSensorLayout.SensorLayout[mSensorLayouts.size()]);
        }

        if (mExperimentSensors != null) {
            mProto.experimentSensors = mExperimentSensors.toArray(
                    new GoosciExperiment.ExperimentSensor[mExperimentSensors.size()]) ;
        }

        if (mSensorTriggers != null) {
            mProto.sensorTriggers = new GoosciSensorTrigger.SensorTrigger[mSensorTriggers.size()];
            int index = 0;
            for (String sensorId : mSensorTriggers.keySet()) {
                for (SensorTrigger trigger : mSensorTriggers.get(sensorId)) {
                    mProto.sensorTriggers[index++] = trigger.getTriggerProto();
                }
            }
        }

        if (mTrials != null) {
            mProto.trials = new GoosciTrial.Trial[mTrials.size()];
            int index = 0;
            for (Trial trial : mTrials) {
                mProto.trials[index++] = trial.getTrialProto();
            }
        }

        if (mLabels != null) {
            mProto.labels = new GoosciLabel.Label[mLabels.size()];
            int index = 0;
            for (Label label : mLabels) {
                mProto.labels[index++] = label.getLabelProto();
            }
        }
    }
}
