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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a Science Journal experiment.
 * All changes should be made using the getters and setters provided, rather than by getting the
 * underlying protocol buffer and making changes to that directly. Changes to the underlying proto
 * outside this class may be overwritten and may not be saved.
 */
public class Experiment {
    private boolean mArchived;
    private GoosciExperiment.Experiment mProto;
    private List<GoosciSensorLayout.SensorLayout> mSensorLayouts;
    private List<GoosciExperiment.ExperimentSensor> mExperimentSensors;
    private List<SensorTrigger> mSensorTriggers;
    private List<Label> mLabels;
    private List<Trial> mTrials;

    public static Experiment newExperiment(long creationTime) {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();
        proto.lastUsedTimeMs = creationTime;
        proto.creationTimeMs = creationTime;
        return new Experiment(proto, false);
    }

    // Archived state is set per account, so if you archive something on one device and share it
    // it will not show up as archived on another account. Therefore it is stored outside of the
    // experiment proto.
    public Experiment(GoosciExperiment.Experiment experimentProto, boolean isArchived) {
        mProto = experimentProto;
        mArchived = isArchived;
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

    public List<Label> getLabels() {
        if (mLabels == null) {
            mLabels = new ArrayList<>();
            for (GoosciLabel.Label labelProto : mProto.labels) {
                mLabels.add(new Label(labelProto));
            }
        }
        return mLabels;
    }

    @VisibleForTesting
    void setLabels(List<Label> labels) {
        mLabels = Preconditions.checkNotNull(labels);;
    }

    /**
     * Get the count separately from getting all the labels avoids unnecessary processing if the
     * caller just has to know how many there are.
     * @return The number of labels in this experiment.
     */
    public int getLabelCount() {
        if (mLabels == null) {
            return mProto.labels.length;
        }
        return mLabels.size();
    }

    /**
     * Gets the labels which fall during a certain time range.
     * @param range The time range in which to search for labels
     * @return A list of labels in that range, or an empty list if none are found.
     */
    public List<Label> getLabelsForRange(GoosciTrial.Range range) {
        List<Label> result = new ArrayList<>();
        for (Label label : getLabels()) {
            if (range.startMs <= label.getTimeStamp() && range.endMs >= label.getTimeStamp()) {
                result.add(label);
            }
        }
        return result;
    }

    public List<Trial> getTrials() {
        if (mTrials == null) {
            mTrials = new ArrayList<>();
            for (GoosciTrial.Trial trial : mProto.trials) {
                mTrials.add(new Trial(trial, getLabelsForRange(trial.recordingRange)));
            }
        }
        return mTrials;
    }

    @VisibleForTesting
    void setTrials(List<Trial> trials) {
        mTrials = Preconditions.checkNotNull(trials);
    }

    /**
     * Sets a trial's labels appropriately.
     * @param trial Trial to add
     */
    public void populateTrialLabels(Trial trial) {
        GoosciTrial.Range range = new GoosciTrial.Range();
        range.startMs = trial.getOriginalFirstTimestamp();
        range.endMs = trial.getOriginalLastTimestamp();
        trial.setLabels(getLabelsForRange(range));
    }

    /**
     * Get the count separately from getting all the trials avoids unnecessary processing if the
     * caller just has to know how many there are.
     * @return The number of trials in this experiment.
     */
    public int getTrialCount() {
        if (mTrials == null) {
            return mProto.trials.length;
        }
        return mTrials.size();
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
        assert experimentSensors != null;
        mExperimentSensors = experimentSensors;
    }

    public List<SensorTrigger> getSensorTriggers() {
        if (mSensorTriggers == null) {
            mSensorTriggers = new ArrayList<>();
            for (GoosciSensorTrigger.SensorTrigger proto : mProto.sensorTriggers) {
                mSensorTriggers.add(new SensorTrigger(proto));
            }
        }
        return mSensorTriggers;
    }

    public void setSensorTriggers(List<SensorTrigger> sensorTriggers) {
        assert sensorTriggers != null;
        mSensorTriggers = sensorTriggers;
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
            for (SensorTrigger trigger : mSensorTriggers) {
                mProto.sensorTriggers[index++] = trigger.getTriggerProto();
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
