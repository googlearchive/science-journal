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
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciSensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.Version;
import com.google.common.base.Preconditions;
import io.reactivex.functions.Consumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a Science Journal experiment. All changes should be made using the getters and setters
 * provided, rather than by getting the underlying protocol buffer and making changes to that
 * directly. Changes to the underlying proto outside this class may be overwritten and may not be
 * saved.
 */
// TODO: Get the ExperimentOverview photo path from labels and trials at load and change.
public class Experiment extends LabelListHolder {
  private GoosciUserMetadata.ExperimentOverview experimentOverview;
  private GoosciExperiment.Experiment proto;
  private List<GoosciSensorLayout.SensorLayout> sensorLayouts;
  private List<GoosciExperiment.ExperimentSensor> experimentSensors;
  private List<SensorTrigger> sensorTriggers;
  private List<Trial> trials;
  private final List<Change> changes;
  private String title;
  private String description;
  private String imagePath;
  private int trialCount;
  private int totalTrials;
  private boolean isArchived;
  private long lastUsedTimeMs;

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
    proto.fileVersion.platform = GoosciGadgetInfo.GadgetInfo.Platform.ANDROID;

    return new Experiment(proto, experimentOverview);
  }

  /** Populates the Experiment from an existing proto. */
  public static Experiment fromExperiment(
      GoosciExperiment.Experiment experiment,
      GoosciUserMetadata.ExperimentOverview experimentOverview) {
    return new Experiment(experiment, experimentOverview);
  }

  // Archived state is set per account, so if you archive something on one device and share it
  // it will not show up as archived on another account. Therefore it is stored outside of the
  // experiment proto.
  private Experiment(
      GoosciExperiment.Experiment experimentProto,
      GoosciUserMetadata.ExperimentOverview experimentOverview) {
    this.proto = experimentProto;
    this.experimentOverview = experimentOverview;
    labels = new ArrayList<>();
    for (GoosciLabel.Label labelProto : this.proto.labels) {
      labels.add(Label.fromLabel(labelProto));
    }
    trials = new ArrayList<>();
    for (GoosciTrial.Trial trial : this.proto.trials) {
      trials.add(Trial.fromTrial(trial));
    }
    sensorTriggers = new ArrayList<>();
    for (GoosciSensorTrigger.SensorTrigger proto : this.proto.sensorTriggers) {
      sensorTriggers.add(SensorTrigger.fromProto(proto));
    }
    changes = new ArrayList<>();
    for (GoosciExperiment.Change proto : this.proto.changes) {
      addChange(Change.fromProto(proto));
    }

    title = this.proto.title;
    description = this.proto.description;
    lastUsedTimeMs = this.experimentOverview.lastUsedTimeMs;
    imagePath = this.proto.imagePath;
    isArchived = this.experimentOverview.isArchived;
    totalTrials = this.proto.totalTrials;
    trialCount = this.experimentOverview.trialCount;
  }

  /**
   * Gets the proto underlying this experiment. The resulting proto should *not* be modified outside
   * of this class because changes to it will not be saved.
   *
   * @return The experiment's underlying protocolbuffer.
   */
  public GoosciExperiment.Experiment getExperimentProto() {
    updateExperimentProto();
    return proto;
  }

  public GoosciUserMetadata.ExperimentOverview getExperimentOverview() {
    experimentOverview.trialCount = trials.size();
    experimentOverview.title = title;
    experimentOverview.isArchived = isArchived;
    experimentOverview.imagePath = imagePath;
    experimentOverview.lastUsedTimeMs = lastUsedTimeMs;
    experimentOverview.trialCount = trialCount;
    return experimentOverview;
  }

  public String getExperimentId() {
    return experimentOverview.experimentId;
  }

  public static String getExperimentId(Experiment experiment) {
    if (experiment != null) {
      return experiment.getExperimentOverview().experimentId;
    }
    return "";
  }

  public long getCreationTimeMs() {
    return proto.creationTimeMs;
  }

  public void setArchived(Context context, boolean archived) {
    isArchived = archived;
    AppSingleton.getInstance(context)
        .getExperimentLibraryManager()
        .setArchived(getExperimentId(), archived);
  }

  public boolean isArchived() {
    return isArchived;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
    addChange(
        Change.newModifyTypeChange(
            GoosciExperiment.ChangedElement.ElementType.EXPERIMENT, getExperimentId()));
  }

  public String getDisplayTitle(Context context) {
    return getDisplayTitle(context, getTitle());
  }

  public static String getDisplayTitle(Context context, String title) {
    return !TextUtils.isEmpty(title) ? title : context.getString(R.string.default_experiment_name);
  }

  @Deprecated
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public long getLastUsedTime() {
    return lastUsedTimeMs;
  }

  public void setLastUsedTime(long lastUsedTime) {
    lastUsedTimeMs = lastUsedTime;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  /**
   * Gets the labels which fall during a certain time range. Objects in this list should not be
   * modified and expect that state to be saved, instead editing of labels should happen using
   * updateLabel, addTrialLabel, removeLabel.
   *
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
   * Temporary method used to populate labels from the database. TODO: Deprecate this after moving
   * to a file-based system where labels are stored as part of the proto and don't need a separate
   * set.
   *
   * @param labels
   */
  public void populateLabels(List<Label> labels) {
    setLabels(labels);
  }

  /**
   * Gets the current list of trials in this experiment. Objects in this list should not be modified
   * and expect that state to be saved, instead editing of trials should happen using updateTrial,
   * addTrial, deleteTrial.
   */
  public List<Trial> getTrials() {
    return trials;
  }

  /**
   * Gets the current list of trials in this experiment. Objects in this list should not be modified
   * and expect that state to be saved, instead editing of trials should happen using updateTrial,
   * addTrial, deleteTrial.
   */
  public List<Trial> getTrials(boolean includeArchived, boolean includeInvalid) {
    if (includeArchived && includeInvalid) {
      return getTrials();
    }
    List<Trial> result = new ArrayList<>();
    for (Trial trial : trials) {
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
   * This wipes the current trial list and should be used only when populating an experiment from
   * the database.
   */
  public void setTrials(List<Trial> trials) {
    this.trials = Preconditions.checkNotNull(trials);
    experimentOverview.trialCount = this.trials.size();
    proto.totalTrials = this.trials.size();

    // Make sure it isn't any larger than this. That's possible if runs were deleted.
    for (int i = 0; i < trials.size(); i++) {
      if (trials.get(i).getTrialNumberInExperiment() > proto.totalTrials) {
        proto.totalTrials = trials.get(i).getTrialNumberInExperiment();
      }
    }
  }

  /**
   * Get the count separately from getting all the trials avoids unnecessary processing if the
   * caller just has to know how many there are.
   *
   * @return The number of trials in this experiment.
   */
  public int getTrialCount() {
    return trials.size();
  }

  /**
   * Gets a trial by its unique ID. Note that Trial IDs are only guaranteed to be unique in an
   * experiment.
   */
  public Trial getTrial(String trialId) {
    for (Trial trial : trials) {
      if (TextUtils.equals(trial.getTrialId(), trialId)) {
        return trial;
      }
    }
    return null;
  }

  /**
   * Updates a trial.
   *
   * @param trial
   */
  public void updateTrial(Trial trial) {
    for (int i = 0; i < trials.size(); i++) {
      Trial next = trials.get(i);
      if (TextUtils.equals(trial.getTrialId(), next.getTrialId())) {
        trials.set(i, trial);
        break;
      }
    }
    // Update may involve crop, so re-sort just in case!
    sortTrials();
    addChange(
        Change.newModifyTypeChange(
            GoosciExperiment.ChangedElement.ElementType.TRIAL, trial.getTrialId()));
  }

  /**
   * Adds a new trial to the experiment.
   *
   * @param trial
   */
  public void addTrial(Trial trial) {
    trials.add(trial);
    trialCount = trials.size();
    trial.setTrialNumberInExperiment(++totalTrials);
    sortTrials();
    addChange(
        Change.newAddTypeChange(
            GoosciExperiment.ChangedElement.ElementType.TRIAL, trial.getTrialId()));
  }

  /** Removes a trial from the experiment. */
  //TODO(b/79353972) Test this
  public void deleteTrial(Trial trial, Context context) {
    trial.deleteContents(context, getExperimentId());
    trials.remove(trial);
    trialCount = trials.size();
    addChange(
        Change.newDeleteTypeChange(
            GoosciExperiment.ChangedElement.ElementType.TRIAL, trial.getTrialId()));
  }

  /** Removes the assets from this experiment. */
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
    trials.remove(trial);
  }

  private void sortTrials() {
    Collections.sort(trials, Trial.COMPARATOR_BY_TIMESTAMP);
  }

  public List<GoosciSensorLayout.SensorLayout> getSensorLayouts() {
    if (sensorLayouts == null) {
      sensorLayouts = new ArrayList<>(Arrays.asList(proto.sensorLayouts));
    }
    return sensorLayouts;
  }

  public void setSensorLayouts(List<GoosciSensorLayout.SensorLayout> layouts) {
    sensorLayouts = Preconditions.checkNotNull(layouts);
  }

  public void updateSensorLayout(int layoutPosition, GoosciSensorLayout.SensorLayout layout) {
    if (layoutPosition == 0 && sensorLayouts.size() == 0) {
      // First one! RecordFragment calls this function when first observing;
      // make sure to handle the empty state correctly by doing this.
      sensorLayouts.add(layout);
    }
    if (layoutPosition < sensorLayouts.size()) {
      sensorLayouts.set(layoutPosition, layout);
    }
  }

  public List<GoosciExperiment.ExperimentSensor> getExperimentSensors() {
    if (experimentSensors == null) {
      experimentSensors = new ArrayList<>(Arrays.asList(proto.experimentSensors));
    }
    return experimentSensors;
  }

  public void setExperimentSensors(List<GoosciExperiment.ExperimentSensor> experimentSensors) {
    this.experimentSensors = Preconditions.checkNotNull(experimentSensors);
  }

  /**
   * Gets the current list of sensor triggers in this experiment for all sensors. Objects in this
   * list should not be modified and expect that state to be saved, instead editing of triggers
   * should happen using update/add/remove functions.
   */
  public List<SensorTrigger> getSensorTriggers() {
    return sensorTriggers;
  }

  public List<SensorTrigger> getActiveSensorTriggers(GoosciSensorLayout.SensorLayout layout) {
    List<SensorTrigger> result = new ArrayList<>(layout.activeSensorTriggerIds.length);
    for (String triggerId : layout.activeSensorTriggerIds) {
      SensorTrigger trigger = getSensorTrigger(triggerId);
      if (trigger != null) {
        result.add(trigger);
      }
    }
    return result;
  }

  /**
   * Gets the current list of sensor triggers in this experiment for a particular sensor. Objects in
   * this list should not be modified and expect that state to be saved, instead editing of triggers
   * should happen using update/add/remove functions.
   */
  public List<SensorTrigger> getSensorTriggersForSensor(String sensorId) {
    List<SensorTrigger> result = new ArrayList<>();
    for (SensorTrigger trigger : sensorTriggers) {
      if (TextUtils.equals(trigger.getSensorId(), sensorId)) {
        result.add(trigger);
      }
    }
    return result;
  }

  /** Gets a sensor trigger by trigger ID. */
  public SensorTrigger getSensorTrigger(String triggerId) {
    for (SensorTrigger trigger : sensorTriggers) {
      if (TextUtils.equals(trigger.getTriggerId(), triggerId)) {
        return trigger;
      }
    }
    return null;
  }

  /**
   * Sets the whole list of SensorTriggers on the experiment.
   *
   * @param sensorTriggers
   */
  public void setSensorTriggers(List<SensorTrigger> sensorTriggers) {
    this.sensorTriggers = Preconditions.checkNotNull(sensorTriggers);
  }

  /** Adds a sensor trigger. */
  public void addSensorTrigger(SensorTrigger trigger) {
    sensorTriggers.add(trigger);
  }

  /** Updates a sensor trigger. */
  public void updateSensorTrigger(SensorTrigger triggerToUpdate) {
    for (int i = 0; i < sensorTriggers.size(); i++) {
      SensorTrigger next = sensorTriggers.get(i);
      if (TextUtils.equals(triggerToUpdate.getTriggerId(), next.getTriggerId())) {
        sensorTriggers.set(i, triggerToUpdate);
        break;
      }
    }
  }

  /**
   * Removes a sensor trigger using its ID to find the matching trigger.
   *
   * @param triggerToRemove
   */
  public void removeSensorTrigger(SensorTrigger triggerToRemove) {
    for (SensorTrigger trigger : sensorTriggers) {
      if (TextUtils.equals(trigger.getTriggerId(), triggerToRemove.getTriggerId())) {
        sensorTriggers.remove(trigger);
        return;
      }
    }
  }

  /**
   * Updates the experiment protocol buffer. This should be done before the protocol buffer is saved
   * because otherwise it may not reflect the latest changes to the experiment.
   */
  private void updateExperimentProto() {
    // All local fields that represent experiment state must be merged back into the proto here.

    if (sensorLayouts != null) {
      proto.sensorLayouts =
          sensorLayouts.toArray(new GoosciSensorLayout.SensorLayout[sensorLayouts.size()]);
    }

    if (experimentSensors != null) {
      proto.experimentSensors =
          experimentSensors.toArray(
              new GoosciExperiment.ExperimentSensor[experimentSensors.size()]);
    }

    if (sensorTriggers != null) {
      proto.sensorTriggers = new GoosciSensorTrigger.SensorTrigger[sensorTriggers.size()];
      int index = 0;
      for (SensorTrigger trigger : sensorTriggers) {
        proto.sensorTriggers[index++] = trigger.getTriggerProto();
      }
    }

    if (trials != null) {
      proto.trials = new GoosciTrial.Trial[trials.size()];
      int index = 0;
      for (Trial trial : trials) {
        proto.trials[index++] = trial.getTrialProto();
      }
    }

    if (labels != null) {
      proto.labels = new GoosciLabel.Label[labels.size()];
      int index = 0;
      for (Label label : labels) {
        proto.labels[index++] = label.getLabelProto();
      }
    }
    if (changes != null) {
      proto.changes = new GoosciExperiment.Change[changes.size()];
      int index = 0;
      for (Change change : changes) {
        proto.changes[index++] = change.getChangeProto();
      }
    }
    proto.imagePath = imagePath;
    proto.title = title;
    proto.description = description;
    proto.totalTrials = totalTrials;
  }

  public List<String> getSensorIds() {
    List<String> sensorIds = new ArrayList<>();
    for (GoosciSensorLayout.SensorLayout layout : sensorLayouts) {
      sensorIds.add(layout.sensorId);
    }
    return sensorIds;
  }

  @Override
  protected void onPictureLabelAdded(Label label) {
    if (TextUtils.isEmpty(imagePath)) {
      imagePath =
          PictureUtils.getExperimentOverviewRelativeImagePath(
              getExperimentId(), label.getPictureLabelValue().filePath);
    }
  }

  @Override
  protected void beforeDeletingPictureLabel(Label label) {
    if (TextUtils.equals(
        imagePath,
        PictureUtils.getExperimentOverviewRelativeImagePath(
            getExperimentId(), label.getPictureLabelValue().filePath))) {
      // This is the picture label which is used as the cover photo for this experiment.
      // Try to find another, oldest first.
      for (int i = labels.size() - 1; i >= 0; i--) {
        Label other = labels.get(i);
        if (!TextUtils.equals(other.getLabelId(), label.getLabelId())
            && other.getType() == GoosciLabel.Label.ValueType.PICTURE) {
          imagePath =
              PictureUtils.getExperimentOverviewRelativeImagePath(
                  getExperimentId(), other.getPictureLabelValue().filePath);
          return;
        }
      }
      // Couldn't find another, so just set it to nothing.
      imagePath = "";
    }
  }

  @Override
  public void addLabel(Experiment experiment, Label label) {
    addLabel(label);
    addChange(
        Change.newAddTypeChange(
            GoosciExperiment.ChangedElement.ElementType.NOTE, label.getLabelId()));
  }

  @Override
  public void updateLabel(Experiment experiment, Label label) {
    updateLabel(label);
    addChange(
        Change.newModifyTypeChange(
            GoosciExperiment.ChangedElement.ElementType.NOTE, label.getLabelId()));
  }

  @Override
  public void updateLabelWithoutSorting(Experiment experiment, Label label) {
    updateLabelWithoutSorting(label);
    addChange(
        Change.newModifyTypeChange(
            GoosciExperiment.ChangedElement.ElementType.NOTE, label.getLabelId()));
  }

  public Consumer<Context> deleteLabelAndReturnAssetDeleter(Label item) {
    return deleteLabelAndReturnAssetDeleter(item, getExperimentId());
  }

  public boolean isEmpty() {
    return getLabelCount() == 0
        && getTrialCount() == 0
        && TextUtils.isEmpty(getExperimentOverview().imagePath)
        && TextUtils.isEmpty(getTitle())
        && !isArchived();
  }

  public void addChange(Change change) {
    changes.add(change);
  }

  public List<Change> getChanges() {
    return changes;
  }

  @VisibleForTesting
  public int getVersion() {
    return proto.fileVersion.version;
  }

  int getMinorVersion() {
    return proto.fileVersion.minorVersion;
  }
}
