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
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProviderImpl;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a recorded trial. All changes should be made using the getters and setters provided,
 * rather than by getting the underlying protocol buffer and making changes to that directly.
 */
public class Trial extends LabelListHolder {
  private static final String TAG = "Trial";

  public static final Comparator<Trial> COMPARATOR_BY_TIMESTAMP =
      (first, second) -> {
        // Sort based on the recording first timestamp.
        return Long.compare(first.getOriginalFirstTimestamp(), second.getOriginalFirstTimestamp());
      };

  interface OnLabelChangeListener {
    void onPictureLabelAdded(Label label);

    void beforeDeletingPictureLabel(Label label);
  }

  private GoosciTrial.Trial trial;
  private Map<String, TrialStats> trialStats;
  private OnLabelChangeListener onLabelChangeListener;

  /** Populates the Trial from an existing proto. */
  public static Trial fromTrial(GoosciTrial.Trial trial) {
    return new Trial(trial);
  }

  /** Populates the Trial from an existing proto, but changes the TrialId. */
  public static Trial fromTrialWithNewId(GoosciTrial.Trial trial) {
    Trial t = new Trial(trial);
    t.trial.trialId = java.util.UUID.randomUUID().toString();
    return t;
  }

  /** Invoked when recording begins to save the metadata about what's being recorded. */
  public static Trial newTrial(
      long startTimeMs,
      GoosciSensorLayout.SensorLayout[] sensorLayouts,
      SensorAppearanceProvider provider,
      Context context) {
    String trialId = java.util.UUID.randomUUID().toString();
    return new Trial(startTimeMs, sensorLayouts, trialId, provider, context);
  }

  private Trial(GoosciTrial.Trial trial) {
    this.trial = trial;
    trialStats = TrialStats.fromTrial(this.trial);
    labels = new ArrayList<>();
    for (GoosciLabel.Label proto : this.trial.labels) {
      labels.add(Label.fromLabel(proto));
    }
  }

  // TODO: eventually provider should go away, in favor of a different structure containing
  // sensor_specs
  private Trial(
      long startTimeMs,
      GoosciSensorLayout.SensorLayout[] sensorLayouts,
      String trialId,
      SensorAppearanceProvider provider,
      Context context) {
    trial = new GoosciTrial.Trial();
    trial.creationTimeMs = startTimeMs;
    trial.recordingRange = new GoosciTrial.Range();
    trial.recordingRange.startMs = startTimeMs;
    trial.sensorLayouts = sensorLayouts;
    trial.trialId = trialId;

    trial.sensorAppearances = new GoosciTrial.Trial.AppearanceEntry[sensorLayouts.length];
    for (int i = 0; i < sensorLayouts.length; i++) {
      GoosciTrial.Trial.AppearanceEntry entry = new GoosciTrial.Trial.AppearanceEntry();
      GoosciSensorLayout.SensorLayout layout = sensorLayouts[i];
      entry.sensorId = layout.sensorId;
      entry.rememberedAppearance =
          SensorAppearanceProviderImpl.appearanceToProto(
              provider.getAppearance(layout.sensorId), context);
      trial.sensorAppearances[i] = entry;
    }

    labels = new ArrayList<>();
    trialStats = new HashMap<>();
  }

  public GoosciPictureLabelValue.PictureLabelValue getCoverPictureLabelValue() {
    for (Label label : labels) {
      if (label.getType() == GoosciLabel.Label.ValueType.PICTURE) {
        return label.getPictureLabelValue();
      }
    }
    return null;
  }

  public long getCreationTimeMs() {
    return trial.creationTimeMs;
  }

  public long getFirstTimestamp() {
    return trial.cropRange == null ? trial.recordingRange.startMs : trial.cropRange.startMs;
  }

  public long getLastTimestamp() {
    return trial.cropRange == null ? trial.recordingRange.endMs : trial.cropRange.endMs;
  }

  public long getOriginalFirstTimestamp() {
    return trial.recordingRange.startMs;
  }

  public long getOriginalLastTimestamp() {
    return trial.recordingRange.endMs;
  }

  public void setRecordingEndTime(long recordingEndTime) {
    trial.recordingRange.endMs = recordingEndTime;
  }

  public GoosciTrial.Range getOriginalRecordingRange() {
    return trial.recordingRange;
  }

  public GoosciTrial.Range getCropRange() {
    return trial.cropRange;
  }

  public void setCropRange(GoosciTrial.Range cropRange) {
    trial.cropRange = cropRange;
  }

  public List<String> getSensorIds() {
    List<String> result = new ArrayList<>();
    for (GoosciSensorLayout.SensorLayout layout : trial.sensorLayouts) {
      result.add(layout.sensorId);
    }
    return result;
  }

  public long elapsedSeconds() {
    if (!isValid()) {
      return 0;
    }
    return Math.round((getLastTimestamp() - getFirstTimestamp()) / 1000.0);
  }

  public boolean isValid() {
    return getOriginalFirstTimestamp() > 0
        && getOriginalLastTimestamp() > getOriginalFirstTimestamp();
  }

  public String getTitleWithDuration(Context context) {
    return context.getString(
        R.string.title_with_duration,
        getTitle(context),
        ElapsedTimeFormatter.getInstance(context).format(elapsedSeconds()));
  }

  public String getTitle(Context context) {
    if (TextUtils.isEmpty(trial.title)) {
      return context.getString(R.string.default_trial_title, trial.trialNumberInExperiment);
    } else {
      return trial.title;
    }
  }

  public String getRawTitle() {
    return trial.title;
  }

  public void setTitle(String title) {
    trial.title = title;
  }

  public boolean isArchived() {
    return trial.archived;
  }

  public void setArchived(boolean isArchived) {
    trial.archived = isArchived;
  }

  public GoosciTrial.Trial getTrialProto() {
    updateTrialProtoWithStats();
    updateTrialProtoWithLabels();
    return trial;
  }

  public List<GoosciSensorLayout.SensorLayout> getSensorLayouts() {
    return new ArrayList(Arrays.asList(trial.sensorLayouts));
  }

  @VisibleForTesting
  public void setSensorLayouts(List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
    Preconditions.checkNotNull(sensorLayouts);
    trial.sensorLayouts =
        sensorLayouts.toArray(new GoosciSensorLayout.SensorLayout[sensorLayouts.size()]);
  }

  public boolean getAutoZoomEnabled() {
    return trial.autoZoomEnabled;
  }

  public void setAutoZoomEnabled(boolean enableAutoZoom) {
    trial.autoZoomEnabled = enableAutoZoom;
  }

  /** Gets a list of the stats for all sensors. */
  public List<TrialStats> getStats() {
    return new ArrayList<>(trialStats.values());
  }

  /** Gets the stats for a sensor. */
  public TrialStats getStatsForSensor(String sensorId) {
    return trialStats.get(sensorId);
  }

  /**
   * Sets the stats for a sensor. This will overwrite existing stats.
   *
   * @param newTrialStats The new stats to save.
   */
  public void setStats(TrialStats newTrialStats) {
    trialStats.put(newTrialStats.getSensorId(), newTrialStats);
  }

  // The Trial ID cannot be set after it is created.
  public String getTrialId() {
    return trial.trialId;
  }

  public String getCaptionText() {
    if (trial.caption == null) {
      return "";
    }
    return trial.caption.text;
  }

  public void setCaption(GoosciCaption.Caption caption) {
    trial.caption = caption;
  }

  /**
   * Deletes the trial and any assets associated with it, including labels and label pictures, run
   * data, etc.
   */
  public void deleteContents(Context context, AppAccount appAccount, String experimentId) {
    for (Label label : labels) {
      deleteLabelAssets(label, context, appAccount, experimentId);
    }
    AppSingleton.getInstance(context)
        .getDataController(appAccount)
        .deleteTrialData(
            this,
            MaybeConsumers.expectSuccess(
                new FailureListener() {
                  @Override
                  public void fail(Exception e) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                      Log.d(TAG, "Error deleting trial data");
                    }
                  }
                }));
    // TODO: Also delete any other assets associated with this trial, including icons, etc
    // from the sensor appearance.
  }

  private void updateTrialProtoWithStats() {
    GoosciTrial.SensorTrialStats[] result = new GoosciTrial.SensorTrialStats[trialStats.size()];
    int i = 0;
    for (String key : trialStats.keySet()) {
      result[i++] = trialStats.get(key).getSensorTrialStatsProto();
    }
    trial.trialStats = result;
  }

  private void updateTrialProtoWithLabels() {
    GoosciLabel.Label[] result = new GoosciLabel.Label[labels.size()];
    for (int i = 0; i < labels.size(); i++) {
      result[i] = labels.get(i).getLabelProto();
    }
    trial.labels = result;
  }

  public void setOnLabelChangeListener(OnLabelChangeListener listener) {
    onLabelChangeListener = listener;
  }

  @Override
  protected void onPictureLabelAdded(Label label) {
    if (onLabelChangeListener != null) {
      onLabelChangeListener.onPictureLabelAdded(label);
    }
  }

  @Override
  protected void beforeDeletingPictureLabel(Label label) {
    if (onLabelChangeListener != null) {
      onLabelChangeListener.beforeDeletingPictureLabel(label);
    }
  }

  /**
   * @return a map of sensor ids (as returned from {@link #getSensorIds()}) to appearance protos.
   *     This map should not be changed; changes have no effect.
   */
  public Map<String, GoosciSensorAppearance.BasicSensorAppearance> getAppearances() {
    // TODO: need a putAppearance method for changes
    HashMap<String, GoosciSensorAppearance.BasicSensorAppearance> appearances = new HashMap<>();
    for (GoosciTrial.Trial.AppearanceEntry entry : trial.sensorAppearances) {
      appearances.put(entry.sensorId, entry.rememberedAppearance);
    }
    return appearances;
  }

  @Override
  public String toString() {
    return "Trial{" + "mTrial=" + trial + ", mTrialStats=" + trialStats + '}';
  }

  public void setTrialNumberInExperiment(int trialNumberInExperiment) {
    trial.trialNumberInExperiment = trialNumberInExperiment;
  }

  public int getTrialNumberInExperiment() {
    return trial.trialNumberInExperiment;
  }

  public Label getLabel(String labelId) {
    for (Label label : labels) {
      if (label.getLabelId().equals(labelId)) {
        return label;
      }
    }
    return null;
  }
}
