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

import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.Version;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.functions.Consumer;

/**
 * Represents a Science Journal experiment.
 * All changes should be made using the getters and setters provided, rather than by getting the
 * underlying protocol buffer and making changes to that directly. Changes to the underlying proto
 * outside this class may be overwritten and may not be saved.
 */
// TODO: Get the ExperimentOverview photo path from labels and trials at load and change.
public class Experiment extends LabelListHolder {
    private GoosciUserMetadata.ExperimentOverview mExperimentOverview;
    private GoosciExperiment.Experiment mProto;
    private List<GoosciSensorLayout.SensorLayout> mSensorLayouts;
    private List<GoosciExperiment.ExperimentSensor> mExperimentSensors;
    private List<SensorTrigger> mSensorTriggers;
    private List<Trial> mTrials;
    private String mTitle;
    private String mDescription;
    private String mImagePath;
    private int mTrialCount;
    private int mTotalTrials;
    private boolean mIsArchived;
    private long mLastUsedTimeMs;

    public static Experiment newExperiment(long creationTime, String experimentId, int colorIndex) {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();
        GoosciUserMetadata.ExperimentOverview experimentOverview =
                new GoosciUserMetadata.ExperimentOverview();
        experimentOverview.lastUsedTimeMs = creationTime;
        experimentOverview.isArchived = false;
        experimentOverview.experimentId = experimentId;
        experimentOverview.colorIndex = colorIndex;
        proto.creationTimeMs = creationTime;
        proto.totalTrials = 0;

        // This experiment is being created with the latest VERSION available.
        proto.fileVersion = new Version.FileVersion();
        proto.fileVersion.version = ExperimentCache.VERSION;
        proto.fileVersion.minorVersion = ExperimentCache.MINOR_VERSION;
        proto.fileVersion.platformVersion = ExperimentCache.PLATFORM_VERSION;
        proto.fileVersion.platform = GoosciGadgetInfo.GadgetInfo.ANDROID;

        return new Experiment(proto, experimentOverview);
    }

    /**
     * Populates the Experiment from an existing proto.
     */
    public static Experiment fromExperiment(GoosciExperiment.Experiment experiment,
            GoosciUserMetadata.ExperimentOverview experimentOverview) {
        return new Experiment(experiment, experimentOverview);
    }

    // Archived state is set per account, so if you archive something on one device and share it
    // it will not show up as archived on another account. Therefore it is stored outside of the
    // experiment proto.
    private Experiment(GoosciExperiment.Experiment experimentProto,
            GoosciUserMetadata.ExperimentOverview experimentOverview) {
        mProto = experimentProto;
        mExperimentOverview = experimentOverview;
        mLabels = new ArrayList<>();
        for (GoosciLabel.Label labelProto : mProto.labels) {
            mLabels.add(Label.fromLabel(labelProto));
        }
        mTrials = new ArrayList<>();
        for (GoosciTrial.Trial trial : mProto.trials) {
            mTrials.add(Trial.fromTrial(trial));
        }
        mSensorTriggers = new ArrayList<>();
        for (GoosciSensorTrigger.SensorTrigger proto : mProto.sensorTriggers) {
            mSensorTriggers.add(SensorTrigger.fromProto(proto));
        }
        mTitle = mProto.title;
        mDescription = mProto.description;
        mLastUsedTimeMs = mExperimentOverview.lastUsedTimeMs;
        mImagePath = mExperimentOverview.imagePath;
        mIsArchived = mExperimentOverview.isArchived;
        mTotalTrials = mProto.totalTrials;
        mTrialCount = mExperimentOverview.trialCount;
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

    public GoosciUserMetadata.ExperimentOverview getExperimentOverview() {
        mExperimentOverview.trialCount = mTrials.size();
        mExperimentOverview.title = mTitle;
        mExperimentOverview.isArchived = mIsArchived;
        mExperimentOverview.imagePath = mImagePath;
        mExperimentOverview.lastUsedTimeMs = mLastUsedTimeMs;
        mExperimentOverview.trialCount = mTrialCount;
        return mExperimentOverview;
    }

    public String getExperimentId() {
        return mExperimentOverview.experimentId;
    }

    public static String getExperimentId(Experiment experiment) {
        if (experiment != null) {
            return experiment.getExperimentOverview().experimentId;
        }
        return "";
    }

    public long getCreationTimeMs() {
        return mProto.creationTimeMs;
    }

    public boolean isArchived() {
        return mIsArchived;
    }

    public void setArchived(boolean archived) {
        mIsArchived = archived;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getDisplayTitle(Context context) {
        return getDisplayTitle(context, getTitle());
    }

    public static String getDisplayTitle(Context context, String title) {
        return !TextUtils.isEmpty(title) ? title : context.getString(
                R.string.default_experiment_name);
    }

    @Deprecated
    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public long getLastUsedTime() {
        return mLastUsedTimeMs;
    }

    public void setLastUsedTime(long lastUsedTime) {
        mLastUsedTimeMs = lastUsedTime;
    }

    public void setImagePath(String imagePath) {
        mImagePath = imagePath;
    }

    /**
     * Gets the labels which fall during a certain time range.
     * Objects in this list should not be modified and expect that state to be saved, instead
     * editing of labels should happen using updateLabel, addTrialLabel, removeLabel.
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
     * editing of trials should happen using updateTrial, addTrial, deleteTrial.
     */
    public List<Trial> getTrials() {
        return mTrials;
    }

    /**
     * Gets the current list of trials in this experiment.
     * Objects in this list should not be modified and expect that state to be saved, instead
     * editing of trials should happen using updateTrial, addTrial, deleteTrial.
     */
    public List<Trial> getTrials(boolean includeArchived, boolean includeInvalid) {
        if (includeArchived && includeInvalid) {
            return getTrials();
        }
        List<Trial> result = new ArrayList<>();
        for (Trial trial : mTrials) {
            if (!includeInvalid && !trial.isValid()) {
                // Invalid trial, don't add it.
            } else if (!includeArchived && trial.isArchived()) {
                // Don't add it.
            } else {
                result.add(trial);
            }
        }
        return result;
    }

    /**
     * This wipes the current trial list and should be used only when populating an experiment
     * from the database.
     */
    public void setTrials(List<Trial> trials) {
        mTrials = Preconditions.checkNotNull(trials);
        mExperimentOverview.trialCount = mTrials.size();
        mProto.totalTrials = mTrials.size();

        // Make sure it isn't any larger than this. That's possible if runs were deleted.
        for (int i = 0; i < trials.size(); i++) {
            if (trials.get(i).getTrialNumberInExperiment() > mProto.totalTrials) {
                mProto.totalTrials = trials.get(i).getTrialNumberInExperiment();
            }
        }
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
     * Note that Trial IDs are only guaranteed to be unique in an experiment.
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
     * Updates a trial.
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
        mTrialCount = mTrials.size();
        trial.setTrialNumberInExperiment(++mTotalTrials);
        sortTrials();
    }

    /**
     * Removes a trial from the experiment.
     */
    public void deleteTrial(Trial trial, Context context) {
        trial.deleteContents(context, getExperimentId());
        mTrials.remove(trial);
        mTrialCount = mTrials.size();
    }

    /**
     * Removes the assets from this experiment.
     */
    public void deleteContents(Context context) {
        for (Label label : getLabels()) {
            deleteLabelAssets(label, context, getExperimentId());
        }
        for (Trial trial : getTrials()) {
            trial.deleteContents(context, getExperimentId());
        }
    }

    @VisibleForTesting
    public void deleteTrialOnlyForTesting(Trial trial) {
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

    public void updateSensorLayout(int layoutPosition, GoosciSensorLayout.SensorLayout layout) {
        if (layoutPosition == 0 && mSensorLayouts.size() == 0) {
            // First one! RecordFragment calls this function when first observing;
            // make sure to handle the empty state correctly by doing this.
            mSensorLayouts.add(layout);
        }
        if (layoutPosition < mSensorLayouts.size()) {
            mSensorLayouts.set(layoutPosition, layout);
        }
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
     * Gets the current list of sensor triggers in this experiment for all sensors.
     * Objects in this list should not be modified and expect that state to be saved, instead
     * editing of triggers should happen using update/add/remove functions.
     */
    public List<SensorTrigger> getSensorTriggers() {
        return mSensorTriggers;
    }

    public List<SensorTrigger> getActiveSensorTriggers(GoosciSensorLayout.SensorLayout layout) {
        List<SensorTrigger> result =
                new ArrayList<>(layout.activeSensorTriggerIds.length);
        for (String triggerId : layout.activeSensorTriggerIds) {
            SensorTrigger trigger = getSensorTrigger(triggerId);
            if (trigger != null) {
                result.add(trigger);
            }
        }
        return result;
    }

    /**
     * Gets the current list of sensor triggers in this experiment for a particular sensor.
     * Objects in this list should not be modified and expect that state to be saved, instead
     * editing of triggers should happen using update/add/remove functions.
     */
    public List<SensorTrigger> getSensorTriggersForSensor(String sensorId) {
        List<SensorTrigger> result = new ArrayList<>();
        for (SensorTrigger trigger : mSensorTriggers) {
            if (TextUtils.equals(trigger.getSensorId(), sensorId)) {
                result.add(trigger);
            }
        }
        return result;
    }

    /**
     * Gets a sensor trigger by trigger ID.
     */
    public SensorTrigger getSensorTrigger(String triggerId) {
        for (SensorTrigger trigger : mSensorTriggers) {
            if (TextUtils.equals(trigger.getTriggerId(), triggerId)) {
                return trigger;
            }
        }
        return null;
    }

    /**
     * Sets the whole list of SensorTriggers on the experiment.
     * @param sensorTriggers
     */
    public void setSensorTriggers(List<SensorTrigger> sensorTriggers) {
        mSensorTriggers = Preconditions.checkNotNull(sensorTriggers);
    }

    /**
     * Adds a sensor trigger.
     */
    public void addSensorTrigger(SensorTrigger trigger) {
        mSensorTriggers.add(trigger);
    }

    /**
     * Updates a sensor trigger.
     */
    public void updateSensorTrigger(SensorTrigger triggerToUpdate) {
        for (int i = 0; i < mSensorTriggers.size(); i++) {
            SensorTrigger next = mSensorTriggers.get(i);
            if (TextUtils.equals(triggerToUpdate.getTriggerId(), next.getTriggerId())) {
                mSensorTriggers.set(i, triggerToUpdate);
                break;
            }
        }
    }

    /**
     * Removes a sensor trigger using its ID to find the matching trigger.
     * @param triggerToRemove
     */
    public void removeSensorTrigger(SensorTrigger triggerToRemove) {
        for (SensorTrigger trigger : mSensorTriggers) {
            if (TextUtils.equals(trigger.getTriggerId(), triggerToRemove.getTriggerId())) {
                mSensorTriggers.remove(trigger);
                return;
            }
        }
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

        mProto.title = mTitle;
        mProto.description = mDescription;
        mProto.totalTrials = mTotalTrials;
    }

    public List<String> getSensorIds() {
        List<String> sensorIds = new ArrayList<>();
        for (GoosciSensorLayout.SensorLayout layout : mSensorLayouts) {
            sensorIds.add(layout.sensorId);
        }
        return sensorIds;
    }

    @Override
    protected void onPictureLabelAdded(Label label) {
        if (TextUtils.isEmpty(mImagePath)) {
            mImagePath = PictureUtils.getExperimentOverviewRelativeImagePath(getExperimentId(),
                            label.getPictureLabelValue().filePath);
        }
    }

    @Override
    protected void beforeDeletingPictureLabel(Label label) {
        if (TextUtils.equals(mImagePath,
                PictureUtils.getExperimentOverviewRelativeImagePath(getExperimentId(),
                        label.getPictureLabelValue().filePath))) {
            // This is the picture label which is used as the cover photo for this experiment.
            // Try to find another, oldest first.
            for (int i = mLabels.size() - 1; i >= 0; i--) {
                Label other = mLabels.get(i);
                if (!TextUtils.equals(other.getLabelId(), label.getLabelId()) &&
                        other.getType() == GoosciLabel.Label.PICTURE) {
                    mImagePath =
                            PictureUtils.getExperimentOverviewRelativeImagePath(getExperimentId(),
                                    other.getPictureLabelValue().filePath);
                    return;
                }
            }
            // Couldn't find another, so just set it to nothing.
            mImagePath = "";
        }
    }

    public Consumer<Context> deleteLabelAndReturnAssetDeleter(Label item) {
        return deleteLabelAndReturnAssetDeleter(item, getExperimentId());
    }

    @VisibleForTesting
    public int getVersion() {
        return mProto.fileVersion.version;
    }

    int getMinorVersion() {
        return mProto.fileVersion.minorVersion;
    }
}
