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
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ExperimentSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range;
import com.google.android.apps.forscience.whistlepunk.metadata.Version;
import com.google.android.apps.forscience.whistlepunk.metadata.Version.FileVersion;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import io.reactivex.functions.Consumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a Science Journal experiment. All changes should be made using the getters and setters
 * provided, rather than by getting the underlying protocol buffer and making changes to that
 * directly. Changes to the underlying proto outside this class may be overwritten and may not be
 * saved.
 */
// TODO: Get the ExperimentOverview photo path from labels and trials at load and change.
public class Experiment extends LabelListHolder {

  private static final String TAG = "Experiment";
  public static final String EXPERIMENTS = "experiments/";

  private ExperimentOverviewPojo experimentOverview;
  private List<SensorLayoutPojo> sensorLayouts;
  private List<ExperimentSensor> experimentSensors;
  private List<SensorTrigger> sensorTriggers;
  private List<Trial> trials;
  private final List<Change> changes;
  private String title;
  private String description;
  private FileVersion.Builder fileVersion;
  // Relative to the experiment, not the account root.
  private String imagePath;
  private int trialCount;
  private int totalTrials;
  private boolean isArchived;
  private long lastUsedTimeMs;
  private final long creationTimeMs;

  public static Experiment newExperiment(long creationTime, String experimentId, int colorIndex) {
    GoosciExperiment.Experiment.Builder proto = GoosciExperiment.Experiment.newBuilder();
    ExperimentOverviewPojo experimentOverview = new ExperimentOverviewPojo();
    experimentOverview.setLastUsedTimeMs(creationTime);
    experimentOverview.setArchived(false);
    experimentOverview.setExperimentId(experimentId);
    experimentOverview.setColorIndex(colorIndex);
    proto.setCreationTimeMs(creationTime);
    proto.setTotalTrials(0);

    // This experiment is being created with the latest VERSION available.
    proto.setFileVersion(
        Version.FileVersion.newBuilder()
            .setVersion(ExperimentCache.VERSION)
            .setMinorVersion(ExperimentCache.MINOR_VERSION)
            .setPlatformVersion(ExperimentCache.PLATFORM_VERSION)
            .setPlatform(GoosciGadgetInfo.GadgetInfo.Platform.ANDROID)
            .build());

    return new Experiment(proto.build(), experimentOverview);
  }

  public static Experiment newExperiment(
      Context context,
      AppAccount appAccount,
      ExperimentLibraryManager elm,
      long creationTime,
      String experimentId,
      int colorIndex,
      long lastUsedTime) {
    Experiment newExperiment = Experiment.newExperiment(creationTime, experimentId, colorIndex);
    newExperiment.setLastUsedTime(elm.getModified(experimentId));
    newExperiment.setArchived(context, appAccount, elm.isArchived(experimentId));
    return newExperiment;
  }

  /** Populates the Experiment from an existing proto. */
  public static Experiment fromExperiment(
      GoosciExperiment.Experiment experiment, ExperimentOverviewPojo experimentOverview) {
    return new Experiment(experiment, experimentOverview);
  }

  // Archived state is set per account, so if you archive something on one device and share it
  // it will not show up as archived on another account. Therefore it is stored outside of the
  // experiment proto.
  private Experiment(
      GoosciExperiment.Experiment experimentProto, ExperimentOverviewPojo experimentOverview) {

    labels = new ArrayList<>();
    for (GoosciLabel.Label labelProto : experimentProto.getLabelsList()) {
      labels.add(Label.fromLabel(labelProto));
    }
    trials = new ArrayList<>();
    for (GoosciTrial.Trial trial : experimentProto.getTrialsList()) {
      trials.add(Trial.fromTrial(trial));
    }
    sensorTriggers = new ArrayList<>();
    for (GoosciSensorTrigger.SensorTrigger proto : experimentProto.getSensorTriggersList()) {
      sensorTriggers.add(SensorTrigger.fromProto(proto));
    }
    changes = new ArrayList<>();
    for (com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.Change proto :
        experimentProto.getChangesList()) {
      addChange(Change.fromProto(proto));
    }

    sensorLayouts = new ArrayList<>();
    for (SensorLayout layout : experimentProto.getSensorLayoutsList()) {
      sensorLayouts.add(SensorLayoutPojo.fromProto(layout));
    }

    experimentSensors = new ArrayList<>();
    experimentSensors.addAll(experimentProto.getExperimentSensorsList());

    title = experimentProto.getTitle();
    description = experimentProto.getDescription();
    lastUsedTimeMs = experimentOverview.getLastUsedTimeMs();
    if (!experimentProto.getImagePath().isEmpty()) {
      // Relative to the experiment, not the account root.
      imagePath = getPathRelativeToExperiment(experimentProto.getImagePath());
    } else {
      // Overview is relative to the account root. Be sure to trim 2 levels
      imagePath = getPathRelativeToExperiment(experimentOverview.getImagePath());
    }
    isArchived = experimentOverview.isArchived();
    totalTrials = experimentProto.getTotalTrials();
    trialCount = experimentOverview.getTrialCount();
    creationTimeMs = experimentProto.getCreationTimeMs();
    if (experimentProto.hasFileVersion()) {
      fileVersion = experimentProto.getFileVersion().toBuilder();
    } else {
      fileVersion = FileVersion.newBuilder();
    }

    this.experimentOverview = experimentOverview;
  }

  public GoosciExperiment.Experiment toProto() {
    return getExperimentProto();
  }
  /**
   * Gets the proto underlying this experiment. The resulting proto should *not* be modified outside
   * of this class because changes to it will not be saved.
   *
   * @return The experiment's underlying protocolbuffer.
   */
  public GoosciExperiment.Experiment getExperimentProto() {
    // All local fields that represent experiment state must be merged back into the proto here.
    GoosciExperiment.Experiment.Builder proto = GoosciExperiment.Experiment.newBuilder();
    if (sensorLayouts != null) {
      for (SensorLayoutPojo pojo : sensorLayouts) {
        proto.addSensorLayouts(pojo.toProto());
      }
    }

    if (experimentSensors != null) {
      proto.addAllExperimentSensors(experimentSensors);
    }

    if (sensorTriggers != null) {
      for (SensorTrigger trigger : sensorTriggers) {
        proto.addSensorTriggers(trigger.getTriggerProto());
      }
    }

    if (trials != null) {
      for (Trial trial : trials) {
        proto.addTrials(trial.getTrialProto());
      }
    }

    if (labels != null) {
      for (Label label : labels) {
        proto.addLabels(label.getLabelProto());
      }
    }
    if (changes != null) {
      for (Change change : changes) {
        proto.addChanges(change.getChangeProto());
      }
    }
    // Relative to the experiment.
    proto.setImagePath(getPathRelativeToExperiment(imagePath));
    if (title != null) {
      proto.setTitle(title);
    }
    if (description != null) {
      proto.setDescription(description);
    }
    proto.setTotalTrials(totalTrials);
    proto.setFileVersion(fileVersion.build());
    proto.setCreationTimeMs(creationTimeMs);
    proto.setVersion(fileVersion.getVersion());
    proto.setMinorVersion(fileVersion.getMinorVersion());
    return proto.build();
  }

  public ExperimentOverviewPojo getExperimentOverview() {
    experimentOverview.setTitle(title);
    experimentOverview.setArchived(isArchived);
    // Relative to the account root. Be sure to add 2 levels.
    experimentOverview.setImagePath(getPathRelativeToAccountRoot(imagePath));
    experimentOverview.setLastUsedTimeMs(lastUsedTimeMs);
    experimentOverview.setTrialCount(trialCount);
    return experimentOverview;
  }

  public String getExperimentId() {
    return experimentOverview.getExperimentId();
  }

  public static String getExperimentId(Experiment experiment) {
    if (experiment != null) {
      return experiment.getExperimentOverview().getExperimentId();
    }
    return "";
  }



  public long getCreationTimeMs() {
    return creationTimeMs;
  }

  public void setArchived(Context context, AppAccount appAccount, boolean archived) {
    isArchived = archived;
  }

  public boolean isArchived() {
    return isArchived;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    setTitle(title, Change.newModifyTypeChange(ElementType.EXPERIMENT, getExperimentId()));
  }

  public void setTitle(String title, Change change) {
    this.title = title;
    addChange(change);
  }

  private void setTitleWithoutRecordingChange(String title) {
    this.title = title;
  }

  public String getDisplayTitle(Context context) {
    return getDisplayTitle(context, getTitle());
  }

  public static String getDisplayTitle(Context context, String title) {
    return !TextUtils.isEmpty(title) ? title : context.getString(R.string.default_experiment_name);
  }

  public FileVersion getFileVersion() {
    return fileVersion.build();
  }

  public void setFileVersion(FileVersion fileVersion) {
    this.fileVersion = fileVersion.toBuilder();
  }

  public void setPlatformVersion(int version) {
    this.fileVersion.setPlatformVersion(version);
  }

  /**
   * Sets the total trials in the experiment. This should generally not be used, except when reading
   * older protos from disk.
   *
   * @param totalTrials The number of trials in the experiment.
   */
  public void setTotalTrials(int totalTrials) {
    this.totalTrials = totalTrials;
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

  // Relative to the experiment. Check for account root.
  public void setImagePath(String imagePath) {
    setImagePathWithoutRecordingChange(imagePath);
    addChange(Change.newModifyTypeChange(ElementType.EXPERIMENT, getExperimentId()));
  }

  /**
   * Sets the image path in the Experiment without adding a change to the history. Should only be
   * used for merging experiments.
   */
  public void setImagePathWithoutRecordingChange(String imagePath) {
    // Relative to the experiment.
    this.imagePath = getPathRelativeToExperiment(imagePath);
  }

  // Relative to the experiment.
  public String getImagePath() {
    return getPathRelativeToExperiment(imagePath);
  }

  /**
   * Gets the labels which fall during a certain time range. Objects in this list should not be
   * modified and expect that state to be saved, instead editing of labels should happen using
   * updateLabel, addTrialLabel, removeLabel.
   *
   * @param range The time range in which to search for labels
   * @return A list of labels in that range, or an empty list if none are found.
   */
  public List<Label> getLabelsForRange(Range range) {
    List<Label> result = new ArrayList<>();
    for (Label label : getLabels()) {
      if (range.getStartMs() <= label.getTimeStamp() && range.getEndMs() >= label.getTimeStamp()) {
        result.add(label);
      } else if (range.getEndMs() < label.getTimeStamp()) {
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
   * Deletes all invalid trials.
   *
   * Only call this method from a background thread, for example, when syncing or exporting.
   */
  public void cleanTrials(Context context, AppAccount appAccount) {
    List<Trial> allTrials = new ArrayList<>(trials);
    for (Trial trial : allTrials) {
      if (!trial.isValid()) {
        deleteTrialWithoutRecordingChange(trial, context, appAccount);
      }
    }
  }

  @VisibleForTesting
  public void cleanTrialsOnlyForTesting() {
    List<Trial> allTrials = new ArrayList<>(trials);
    for (Trial trial : allTrials) {
      if (!trial.isValid()) {
        deleteTrialOnlyForTesting(trial);
      }
    }
  }

  /**
   * This wipes the current trial list and should be used only when populating an experiment from
   * the database.
   */
  public void setTrials(List<Trial> trials) {
    this.trials = Preconditions.checkNotNull(trials);
    experimentOverview.setTrialCount(this.trials.size());
    totalTrials = this.trials.size();

    // Make sure it isn't any larger than this. That's possible if runs were deleted.
    for (int i = 0; i < trials.size(); i++) {
      if (trials.get(i).getTrialNumberInExperiment() > totalTrials) {
        totalTrials = trials.get(i).getTrialNumberInExperiment();
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

  /** Updates a trial without writing a change. Used for merging. */
  private void updateTrialWithoutRecordingChange(Trial trial) {
    for (int i = 0; i < trials.size(); i++) {
      Trial next = trials.get(i);
      if (TextUtils.equals(trial.getTrialId(), next.getTrialId())) {
        trials.set(i, trial);
        break;
      }
    }

    // Update may involve crop, so re-sort just in case!
    sortTrials();
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
    addChange(Change.newModifyTypeChange(ElementType.TRIAL, trial.getTrialId()));
  }

  /**
   * Adds a new trial to the experiment.
   *
   * @param trial
   * @param change
   */
  public void addTrial(Trial trial, Change change) {
    trials.add(trial);
    trialCount = trials.size();
    trial.setTrialNumberInExperiment(++totalTrials);
    sortTrials();
    addChange(change);
  }

  public void addTrial(Trial trial) {
    addTrial(trial, Change.newAddTypeChange(ElementType.TRIAL, trial.getTrialId()));
  }

  /** Adds a new trial to the experiment without recording the change. Used for merges. */
  private void addTrialwithoutRecordingChange(Trial trial) {
    trials.add(trial);
    trialCount = trials.size();
    trial.setTrialNumberInExperiment(++totalTrials);
    sortTrials();
  }

  /** Removes a trial from the experiment, without recording a change. Used for merging */
  // TODO(b/79353972) Test this
  public void deleteTrialWithoutRecordingChange(
      Trial trial, Context context, AppAccount appAccount) {
    trial.deleteContents(context, appAccount, getExperimentId());
    trials.remove(trial);
    trialCount = trials.size();
  }

  /** Removes a trial from the experiment. */
  // TODO(b/79353972) Test this
  public void deleteTrial(Trial trial, Context context, AppAccount appAccount) {
    deleteTrial(
        trial,
        Change.newDeleteTypeChange(ElementType.TRIAL, trial.getTrialId()),
        context,
        appAccount);
  }

  public void deleteTrial(Trial trial, Change change, Context context, AppAccount appAccount) {
    trial.deleteContents(context, appAccount, getExperimentId());
    trials.remove(trial);
    trialCount = trials.size();
    addChange(change);
  }

  /** Removes the assets from this experiment. */
  public void deleteContents(Context context, AppAccount appAccount) {
    for (Label label : getLabels()) {
      deleteLabelAssets(label, context, appAccount, getExperimentId());
    }
    for (Trial trial : getTrials()) {
      trial.deleteContents(context, appAccount, getExperimentId());
    }
  }

  @VisibleForTesting
  public void deleteTrialOnlyForTesting(Trial trial) {
    trials.remove(trial);
    trialCount = trials.size();
    addChange(Change.newDeleteTypeChange(ElementType.TRIAL, trial.getTrialId()));
  }

  private void sortTrials() {
    Collections.sort(trials, Trial.COMPARATOR_BY_TIMESTAMP);
  }

  public List<SensorLayoutPojo> getSensorLayouts() {
    return sensorLayouts;
  }

  public void setSensorLayouts(List<SensorLayoutPojo> layouts) {
    sensorLayouts = Preconditions.checkNotNull(layouts);
  }

  public void updateSensorLayout(int layoutPosition, SensorLayoutPojo layout) {
    if (layoutPosition == 0 && sensorLayouts.size() == 0) {
      // First one! RecordFragment calls this function when first observing;
      // make sure to handle the empty state correctly by doing this.
      sensorLayouts.add(layout);
    }
    if (layoutPosition < sensorLayouts.size()) {
      sensorLayouts.set(layoutPosition, layout);
    }
  }

  public List<ExperimentSensor> getExperimentSensors() {
    return experimentSensors;
  }

  public void setExperimentSensors(List<ExperimentSensor> experimentSensors) {
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

  public List<SensorTrigger> getActiveSensorTriggers(SensorLayoutPojo layout) {
    List<SensorTrigger> result = new ArrayList<>(layout.getActiveSensorTriggerIds().size());
    for (String triggerId : layout.getActiveSensorTriggerIds()) {
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

  public List<String> getSensorIds() {
    List<SensorLayoutPojo> sensorLayoutList = getSensorLayouts();
    List<String> sensorIds = new ArrayList<>();
    for (SensorLayoutPojo layout : sensorLayoutList) {
      sensorIds.add(layout.getSensorId());
    }
    return sensorIds;
  }

  @Override
  protected void onPictureLabelAdded(Label label) {
    // Relative to Experiment.
    if (TextUtils.isEmpty(imagePath)) {
      imagePath = getPathRelativeToExperiment(label.getPictureLabelValue().getFilePath());
    }
  }

  @Override
  protected void beforeDeletingPictureLabel(Label label) {
    // Both relative to Experiment
    if (TextUtils.equals(
        imagePath, getPathRelativeToExperiment(label.getPictureLabelValue().getFilePath()))) {
      // This is the picture label which is used as the cover photo for this experiment.
      // Try to find another, oldest first.
      for (int i = labels.size() - 1; i >= 0; i--) {
        Label other = labels.get(i);
        if (!TextUtils.equals(other.getLabelId(), label.getLabelId())
            && other.getType() == ValueType.PICTURE) {
          // Should be relative to Experiment.
          imagePath = getPathRelativeToExperiment(other.getPictureLabelValue().getFilePath());
          return;
        }
      }
      // Couldn't find another, so just set it to nothing.
      imagePath = "";
    }
  }



  public boolean isEmpty() {
    return getLabelCount() == 0
        && getTrialCount() == 0
        && TextUtils.isEmpty(getExperimentOverview().getImagePath())
        && TextUtils.isEmpty(getTitle())
        && !isArchived();
  }

  public void addChange(Change change) {
    changes.add(change);
  }

  public List<Change> getChanges() {
    return changes;
  }

  /**
   * Finds a label with a given id within an experiment, whether in the experiment itself, or nested
   * under a trial.
   *
   * @param labelId the id of the label to find.
   * @return the label that corresponds to the Id, or null.
   */
  public Label getLabel(String labelId) {
    for (Label label : labels) {
      if (label.getLabelId().equals(labelId)) {
        return label;
      }
    }
    for (Trial trial : trials) {
      for (Label label : trial.getLabels()) {
        if (label.getLabelId().equals(labelId)) {
          return label;
        }
      }
    }
    return null;
  }

  /**
   * Finds a the id of the trial that contains a given labelId, or null if the id isn't found, or if
   * it is found in the root experiment.
   *
   * @param labelId the id of the label to find.
   * @return the id of the trial that contains the label, or null.
   */
  public String getTrialIdForLabel(String labelId) {
    for (Label label : labels) {
      if (label.getLabelId().equals(labelId)) {
        return null;
      }
    }
    for (Trial trial : trials) {
      for (Label label : trial.getLabels()) {
        if (label.getLabelId().equals(labelId)) {
          return trial.getTrialId();
        }
      }
    }
    return null;
  }

  public static String getChangeMapKey(Change change) {
    return change.getChangedElementId() + change.getChangedElementType().getNumber();
  }

  /**
   * Merges the supplied externalExperiment into this experiment. The externalExperiment is not
   * modified by this operation, but should no longer be used, as it is outdated. However, merging
   * an experiment twice should not cause any problems, as the mergeFrom is deterministic and
   * records all changes to the newly merged experiment.
   *
   * <p>Based on sj-merge documentation.
   *
   * @param externalExperiment The Experiment to mergeFrom into this one.
   * @param context The current Context.
   */
  public FileSyncCollection mergeFrom(
      Experiment externalExperiment, Context context, AppAccount appAccount, boolean overwrite) {
    if (overwrite) {
      changes.clear();
      changes.addAll(externalExperiment.changes);
      trials.clear();
      trials.addAll(externalExperiment.trials);
      labels.clear();
      labels.addAll(externalExperiment.labels);
      title = externalExperiment.title;
      description = externalExperiment.description;
      // Relative to Experiment.
      imagePath = getPathRelativeToExperiment(externalExperiment.imagePath);
      trialCount = externalExperiment.trialCount;
      totalTrials = externalExperiment.totalTrials;
      return new FileSyncCollection();
    } else {
      // First, we have to calculate the changes made in the local and external experiment.
      List<Change> localChanges = changes;
      List<Change> externalChanges = externalExperiment.getChanges();

      Set<Change> localOnly = new LinkedHashSet<>();
      Set<Change> externalOnly = new LinkedHashSet<>();

      FileSyncCollection filesToSync = new FileSyncCollection();

      localOnly.addAll(localChanges);
      localOnly.removeAll(externalChanges);

      externalOnly.addAll(externalChanges);
      externalOnly.removeAll(localChanges);

      // Next, we have to add all of the external-only change records to the local change log.
      for (Change c : externalOnly) {
        addChange(c);
      }

      // Now, build a set of every element that changed externally and locally. This way,
      // we can intersect those sets to find conflicts.
      HashMap<String, Change> changedExternalElements = new HashMap<>();
      for (Change external : externalOnly) {
        changedExternalElements.put(getChangeMapKey(external), external);
      }

      HashMap<String, Change> changedLocalElements = new HashMap<>();
      for (Change local : localOnly) {
        changedLocalElements.put(getChangeMapKey(local), local);
      }

      // For each external changed element, see if that element was also changed locally. If it was,
      // Solve the conflict. If it wasn't, copy the element to the local experiment.
      // N.B., this deals with changed ELEMENTS, not changes. So if there are 2 edits made to a
      // note,
      // we only have to deal with it once, as the final state is already recorded. We have copied
      // the change record above, so future merges will be aware of the full history.
      FileMetadataUtil fileMetadataUtil = FileMetadataUtil.getInstance();
      for (Change external : changedExternalElements.values()) {
        if (changedLocalElements.containsKey(getChangeMapKey(external))) {
          handleConflictMerge(
              externalExperiment, context, appAccount, fileMetadataUtil, external, filesToSync);
          changedLocalElements.remove(getChangeMapKey(external));
        } else {
          handleNoConflictMerge(
              externalExperiment, context, appAccount, fileMetadataUtil, external, filesToSync);
        }
      }
      for (Change local : changedLocalElements.values()) {
        handleLocalOnlyMerge(appAccount, fileMetadataUtil, local, filesToSync);
      }

      return filesToSync;
    }
  }

  private void handleLocalOnlyMerge(
      AppAccount appAccount,
      FileMetadataUtil fileMetadataUtil,
      Change local,
      FileSyncCollection filesToSync) {
    // If there is no conflict, we can just copy the change
    switch (local.getChangedElementType()) {
      case NOTE:
        Label label = getLabel(local.getChangedElementId());
        if (label != null && label.getType() == ValueType.PICTURE) {
          filesToSync.addImageUpload(label.getPictureLabelValue().getFilePath());
        }
        break;
      case TRIAL:
        if (getTrial(local.getChangedElementId()) != null) {
          filesToSync.addTrialUpload(local.getChangedElementId());
        }
        break;
      case EXPERIMENT:
        if (!Strings.isNullOrEmpty(getImagePath())) {
          // Will be relative to Experiment.
          java.io.File overviewImage =
              new java.io.File(
                  PictureUtils.getExperimentOverviewFullImagePath(
                      appAccount, getPathRelativeToAccountRoot(getImagePath())));
          filesToSync.addImageUpload(
              fileMetadataUtil.getRelativePathInExperiment(getExperimentId(), overviewImage));
        }
        break;
      case CAPTION: //Nothing to do with a local-only caption merge.
      default:
        break;
    }
  }

  private void handleNoConflictMerge(
      Experiment externalExperiment,
      Context context,
      AppAccount appAccount,
      FileMetadataUtil fileMetadataUtil,
      Change external,
      FileSyncCollection filesToSync) {
    // If there is no conflict, we can just copy the change
    switch (external.getChangedElementType()) {
      case NOTE:
        copyNoteChange(externalExperiment, context, external, appAccount, filesToSync);
        break;
      case EXPERIMENT:
        copyExperimentChange(fileMetadataUtil, appAccount, externalExperiment, filesToSync);
        break;
      case TRIAL:
        copyTrialChange(externalExperiment, context, external, appAccount, filesToSync);
        break;
      case CAPTION:
        copyCaptionChange(externalExperiment, external);
        break;
      default:
        break;
    }
  }

  private void copyCaptionChange(
      Experiment externalExperiment, Change external) {

    String trialId = external.getChangedElementId();
    Trial externalTrial = externalExperiment.getTrial(trialId);
    Trial localTrial = getTrial(trialId);

    if (localTrial != null) {
      // Since the local trial exists, set the caption.
      Caption caption = Caption.newBuilder().setText(externalTrial.getCaptionText()).build();
      localTrial.setCaption(caption);
    } else {
      String labelId = external.getChangedElementId();
      Label externalLabel = externalExperiment.getLabel(labelId);
      Label localLabel = getLabel(labelId);

      if (localLabel != null) {
        // Since the local trial exists, set the caption.
        Caption caption = Caption.newBuilder().setText(externalLabel.getCaptionText()).build();
        localLabel.setCaption(caption);
      }
    }
  }

  private void copyTrialChange(
      Experiment externalExperiment,
      Context context,
      Change external,
      AppAccount appAccount,
      FileSyncCollection filesToSync) {
    String trialId = external.getChangedElementId();
    Trial externalTrial = externalExperiment.getTrial(trialId);
    Trial localTrial = getTrial(trialId);

    if (externalTrial != null) {
      filesToSync.addTrialDownload(trialId);
      // If the external trial exists, we have to copy it to the local experiment.
      if (localTrial != null) {
        // If the local trial exists, this is an update, not an add. In either case, we don't want
        // to add our change to the changelog, as it has already been copied.
        updateTrialWithoutRecordingChange(externalTrial);
      } else {
        addTrialwithoutRecordingChange(externalTrial);
      }
    } else {
      // If the external trial does not exist, it was deleted, so we should delete from the local
      // experiment as well. Don't record the change to the changelog.
      if (localTrial != null) {
        deleteTrialWithoutRecordingChange(localTrial, context, appAccount);
      }
    }
  }

  private void copyExperimentChange(
      FileMetadataUtil fileMetadataUtil,
      AppAccount appAccount,
      Experiment externalExperiment,
      FileSyncCollection filesToSync) {
    // This copies changes to the overall experiment: the title and the image path. These can't
    // be added or deleted, really. They are just updated to/from blank strings.
    // Don't record the change to the changelog.
    setTitleWithoutRecordingChange(externalExperiment.getTitle());
    setImagePathWithoutRecordingChange(externalExperiment.getImagePath());
    if (!Strings.isNullOrEmpty(externalExperiment.getImagePath())) {
      java.io.File overviewImage =
          // will be relative to Experiment.
          new java.io.File(
              PictureUtils.getExperimentOverviewFullImagePath(
                  appAccount, getPathRelativeToAccountRoot(externalExperiment.getImagePath())));
      filesToSync.addImageDownload(
          fileMetadataUtil.getRelativePathInExperiment(
              externalExperiment.getExperimentId(), overviewImage));
    }
  }

  private void copyNoteChange(
      Experiment externalExperiment,
      Context context,
      Change external,
      AppAccount appAccount,
      FileSyncCollection filesToSync) {
    // Copying notes is a little complicated, because they can be in either the root experiment, or
    // attached to a trial, so we have to hunt around for them.
    Label externalLabel = externalExperiment.getLabel(external.getChangedElementId());
    if (externalLabel == null) {
      // This is a delete, so let's make sure the label gets deleted locally.
      // First, find out if this label already exists in a local trial.
      String trialId = getTrialIdForLabel(external.getChangedElementId());
      if (trialId != null) {
        // Yep, it is in a local trial. So, get the trial, as well as the Local (not-deleted) label.
        Trial trial = getTrial(trialId);
        Label label = trial.getLabel(external.getChangedElementId());
        // Delete the local label, without writing a change.
        Consumer<Context> assetDeleter =
            trial.deleteLabelAndReturnAssetDeleterWithoutRecordingChange(this, label, appAccount);
        try {
          assetDeleter.accept(context);
        } catch (Exception e) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Asset Deletion Failed", e);
          }
        }
      } else {
        // Nope, it's not in a local trial. It must be in the local experiment, or this label was
        // created and deleted between merges from the external source.
        Label label = getLabel(external.getChangedElementId());
        if (label != null) {
          // If the label does exist, delete it without writing the change to the changelog. If it
          // doesn't exist, that's fine, we wanted to delete it anyway.
          Consumer<Context> assetDeleter =
              deleteLabelAndReturnAssetDeleterWithoutRecordingChange(this, label, appAccount);
          try {
            assetDeleter.accept(context);
          } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
              Log.e(TAG, "Asset Deletion Failed", e);
            }
          }
        }
      }
    } else {
      if (externalLabel.getType() == ValueType.PICTURE) {
        filesToSync.addImageDownload(externalLabel.getPictureLabelValue().getFilePath());
      }
      // This is not a delete. The label still exists in the external experiment.
      // Find if the label is associated with a trial, externally.
      String trialId = externalExperiment.getTrialIdForLabel(external.getChangedElementId());
      if (trialId != null) {
        // It's a trial label! So get the trial from the local experiment.
        Trial trial = getTrial(trialId);
        if (trial != null) {
          // If the trial exists in the local experiment, let's copy the label from external to
          // local, without copying the change.
          Label localLabel = getLabel(external.getChangedElementId());
          // If the label exists locally, update it.
          if (localLabel != null) {
            trial.updateLabel(externalLabel);
          } else {
            // Otherwise, add it.
            trial.addLabel(externalLabel);
          }
        } else {
          // If the trial doesn't exist locally, let's copy the whole external trial. There's a
          // chance this will be reduntant, but it's unlikely that there will be many such copies,
          // so let's not worry about optimizing too much. Copying the trial brings al of the
          // associated labels with it.
          Trial externalTrial = externalExperiment.getTrial(trialId);
          addTrialwithoutRecordingChange(externalTrial);
        }
      } else {
        // This label is an experiment label, not trial. That's simpler.
        Label label = getLabel(external.getChangedElementId());
        if (label != null) {
          // The label exists locally already, so let's update it. Don't record the change.
          updateLabel(externalLabel);
        } else {
          // The label does not exist locally. We have to add it, without recording a change.
          addLabel(externalLabel);
        }
      }
    }
  }

  // Handles merges where there are potential conflicts between local and external.
  private void handleConflictMerge(
      Experiment externalExperiment,
      Context context,
      AppAccount appAccount,
      FileMetadataUtil fileMetadataUtil,
      Change external,
      FileSyncCollection filesToSync) {
    switch (external.getChangedElementType()) {
      case NOTE:
        handleNoteConflict(externalExperiment, context, appAccount, external, filesToSync);
        break;
      case EXPERIMENT:
        handleExperimentConflict(
            fileMetadataUtil, appAccount, context, externalExperiment, filesToSync);
        break;
      case TRIAL:
        handleTrialConflict(externalExperiment, context, appAccount, external);
        break;
      case CAPTION:
        handleCaptionConflict(externalExperiment, external);
        break;
      default:
        break;
    }
  }

  private void handleTrialConflict(
      Experiment externalExperiment,
      Context context,
      AppAccount appAccount,
      Change external) {
    Trial externalTrial = externalExperiment.getTrial(external.getChangedElementId());
    Trial localTrial = getTrial(external.getChangedElementId());

    if (localTrial != null) {
      if (externalTrial == null) {
        // Delete the local trial
        deleteTrialWithoutRecordingChange(localTrial, context, appAccount);
      } else {
        if (localTrial != null) {
          // Both local and external have been edited. This is a title change.
          if (!localTrial.getTitle(context).equals(externalTrial.getTitle(context))) {
            localTrial.setTitle(context
              .getResources()
              .getString(
                  R.string.experiment_title_concatenator,
                  localTrial.getTitle(context),
                  externalTrial.getTitle(context)));
            updateTrial(localTrial);
          }
        }
      }
    }
  }

  private void handleExperimentConflict(
      FileMetadataUtil fileMetadataUtil,
      AppAccount appAccount,
      Context context,
      Experiment externalExperiment,
      FileSyncCollection filesToSync) {
    // If the experiment is edited in both experiments, we will take the external imagepath, and
    // combine the titles (if the titles are different).
    // We won't get experiment delete changes, because those are reflected in the experimentlibrary,
    // and we won't get experiment add conflicts, because the IDs are UUIDs and won't conflict.
    if (!getTitle().equals(externalExperiment.getTitle())
        && !getTitle().equals(getExperimentId())) {
      setTitleWithoutRecordingChange(
          context
              .getResources()
              .getString(
                  R.string.experiment_title_concatenator,
                  getTitle(),
                  externalExperiment.getTitle()));
    } else if (!getTitle().equals(externalExperiment.getTitle())) {
      setTitleWithoutRecordingChange(externalExperiment.getTitle());
    }
    setImagePathWithoutRecordingChange(externalExperiment.getImagePath());
    if (!Strings.isNullOrEmpty(externalExperiment.getImagePath())) {
      // will be relative to Experiment.
      java.io.File overviewImage =
          new java.io.File(
              PictureUtils.getExperimentOverviewFullImagePath(
                  appAccount, getPathRelativeToAccountRoot(externalExperiment.getImagePath())));
      filesToSync.addImageDownload(
          fileMetadataUtil.getRelativePathInExperiment(
              externalExperiment.getExperimentId(), overviewImage));
    }
  }

  private void handleNoteConflict(
      Experiment externalExperiment,
      Context context,
      AppAccount appAccount,
      Change external,
      FileSyncCollection filesToSync) {
    Label externalLabel = externalExperiment.getLabel(external.getChangedElementId());
    Label localLabel = getLabel(external.getChangedElementId());
    if (localLabel == null) {
      // This is a delete. When there has been a local delete and a remote change, keep the local
      // delete. Alternatively, the remote was also deleted, so we can keep that delete, too.
    } else {
      // This is a local edit.
      // Determine if it's a trial note or an experiment one.
      String trialId = getTrialIdForLabel(external.getChangedElementId());
      if (trialId != null) {
        // It's a trial note
        Trial trial = getTrial(trialId);
        // Get the local trial.
        if (trial != null) {
          // The local trial exists. That means either a) the trial contains a different version
          // of the label, or b) the label has been deleted. If edited, we have to create a new
          // label and add it to the trial. If deleted, we have to delete the local trial.
          // This is a change that is not known to the change log so we DO have to write a change,
          // here. If the trial doesn't exist, it has been deleted itself, and we can move on.
          if (externalLabel != null) {
            trial.addLabel(this, Label.copyOf(externalLabel));
            if (externalLabel.getType() == ValueType.PICTURE) {
              filesToSync.addImageUpload(externalLabel.getPictureLabelValue().getFilePath());
            }

            if (localLabel != null) {
              if (localLabel.getType() == ValueType.PICTURE) {
                filesToSync.addImageDownload(localLabel.getPictureLabelValue().getFilePath());
              }
            }
          } else {
            // The label was deleted externally
            Consumer<Context> assetDeleter =
                trial.deleteLabelAndReturnAssetDeleterWithoutRecordingChange(
                    this, localLabel, appAccount);
            try {
              assetDeleter.accept(context);
            } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
              Log.e(TAG, "Asset Deletion Failed", e);
            }
            }
          }
        }
      } else {
        // This is an experiment label. Either the experiment label has been deleted remotely, or
        // it has been edited. If it was deleted, we have to delete it locally, and if it's been
        // edited, we need to add a new label to the experiment. Once again, that ID is NOT known
        // to the change log, so we have to add this to the log.
        if (externalLabel != null) {
          addLabel(this, Label.copyOf(externalLabel));
          if (externalLabel.getType() == ValueType.PICTURE) {
            filesToSync.addImageDownload(externalLabel.getPictureLabelValue().getFilePath());
          }

          if (localLabel != null) {
            if (localLabel.getType() == ValueType.PICTURE) {
              filesToSync.addImageUpload(localLabel.getPictureLabelValue().getFilePath());
            }
          }
        } else {
          // The label was deleted externally.
          Consumer<Context> assetDeleter =
              deleteLabelAndReturnAssetDeleterWithoutRecordingChange(this, localLabel, appAccount);
          try {
            assetDeleter.accept(context);
          } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
              Log.e(TAG, "Asset Deletion Failed", e);
            }
          }
        }
      }
    }
  }

  private void handleCaptionConflict(
      Experiment externalExperiment, Change external) {
    Label externalLabel = externalExperiment.getLabel(external.getChangedElementId());
    Label localLabel = getLabel(external.getChangedElementId());

    if (localLabel != null) {
      Caption newCaption =
          Caption.newBuilder()
              .setText(localLabel.getCaptionText() + " " + externalLabel.getCaptionText())
              .build();
      localLabel.setCaption(newCaption);
      addChange(Change.newModifyTypeChange(ElementType.CAPTION, localLabel.getLabelId()));
      return;
    }

    Trial externalTrial = externalExperiment.getTrial(external.getChangedElementId());
    Trial localTrial = getTrial(external.getChangedElementId());
    if (localTrial != null) {
      Caption newCaption =
          Caption.newBuilder()
              .setText(localTrial.getCaptionText() + " " + externalTrial.getCaptionText())
              .build();
      localTrial.setCaption(newCaption);
      addChange(Change.newModifyTypeChange(ElementType.CAPTION, localTrial.getTrialId()));
    }
  }

  /** Returns a path that starts after the experiment id. Probably starts with "assets". */
  private String getPathRelativeToExperiment(String path) {
    if (Strings.isNullOrEmpty(path)) {
      return path;
    }
    if (path.startsWith(EXPERIMENTS)) {
      List<String> splitList = Splitter.on('/').splitToList(path);
      StringBuilder experimentPath = new StringBuilder();
      String delimiter = "";
      for (int i = 2; i < splitList.size(); i++) {
        experimentPath.append(delimiter).append(splitList.get(i));
        delimiter = "/";
      }
      return experimentPath.toString();
    }
    return path;
  }

  /** Returns a path that starts after the account id. Starts with "experiments/". */
  public String getPathRelativeToAccountRoot(String path) {
    if (Strings.isNullOrEmpty(path)) {
      return path;
    } else if (path.startsWith(EXPERIMENTS)) {
      return path;
    }
    return PictureUtils.getExperimentOverviewRelativeImagePath(getExperimentId(), path);
  }
}
