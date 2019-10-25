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
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Trial.AppearanceEntry;
import java.util.ArrayList;
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

  // private GoosciTrial.Trial trial;

  private String trialId;
  private final long creationTimeMs;
  private String title;
  private boolean archived;
  private Range recordingRange;
  private Range cropRange;
  private boolean autoZoomEnabled;
  private List<SensorLayoutPojo> sensorLayouts = new ArrayList<>();
  private final List<AppearanceEntry> sensorAppearances = new ArrayList<>();
  private int trialNumberInExperiment;
  private Caption caption;
  private Map<String, TrialStats> trialStats = new HashMap<>();
  private OnLabelChangeListener onLabelChangeListener;

  /** Populates the Trial from an existing proto. */
  public static Trial fromTrial(GoosciTrial.Trial trial) {
    return new Trial(trial);
  }

  /** Populates the Trial from an existing proto, but changes the TrialId. */
  public static Trial fromTrialWithNewId(GoosciTrial.Trial trial) {
    Trial t = new Trial(trial);
    t.trialId = java.util.UUID.randomUUID().toString();
    return t;
  }

  /** Invoked when recording begins to save the metadata about what's being recorded. */
  public static Trial newTrial(
      long startTimeMs,
      SensorLayout[] sensorLayouts,
      SensorAppearanceProvider provider,
      Context context) {
    String trialId = java.util.UUID.randomUUID().toString();
    return new Trial(startTimeMs, sensorLayouts, trialId, provider, context);
  }

  private Trial(GoosciTrial.Trial trial) {
    labels = new ArrayList<>();
    trialId = trial.getTrialId();
    creationTimeMs = trial.getCreationTimeMs();
    title = trial.getTitle();
    archived = trial.getArchived();
    recordingRange = trial.getRecordingRange();
    if (trial.hasCropRange()) {
      cropRange = trial.getCropRange();
    }
    autoZoomEnabled = trial.getAutoZoomEnabled();
    trialNumberInExperiment = trial.getTrialNumberInExperiment();
    for (SensorLayout layoutProto : trial.getSensorLayoutsList()) {
      sensorLayouts.add(SensorLayoutPojo.fromProto(layoutProto));
    }

    sensorAppearances.addAll(trial.getSensorAppearancesList());

    for (GoosciLabel.Label labelProto : trial.getLabelsList()) {
      labels.add(Label.fromLabel(labelProto));
    }

    trialStats = TrialStats.fromTrial(trial);
  }

  public static Trial fromProto(GoosciTrial.Trial trial) {
    return new Trial(trial);
  }

  // TODO: eventually provider should go away, in favor of a different structure containing
  // sensor_specs
  private Trial(
      long startTimeMs,
      SensorLayout[] sensorLayouts,
      String trialId,
      SensorAppearanceProvider provider,
      Context context) {
    labels = new ArrayList<>();
    creationTimeMs = startTimeMs;
    recordingRange = Range.newBuilder().setStartMs(startTimeMs).build();

    for (SensorLayout layoutProto : sensorLayouts) {
      this.sensorLayouts.add(SensorLayoutPojo.fromProto(layoutProto));
    }
    this.trialId = trialId;

    for (SensorLayout layout : sensorLayouts) {
      sensorAppearances.add(
          AppearanceEntry.newBuilder()
              .setSensorId(layout.getSensorId())
              .setRememberedAppearance(
                  SensorAppearanceProviderImpl.appearanceToProto(
                      provider.getAppearance(layout.getSensorId()), context))
              .build());
    }
  }

  public GoosciPictureLabelValue.PictureLabelValue getCoverPictureLabelValue() {
    for (Label label : labels) {
      if (label.getType() == ValueType.PICTURE) {
        return label.getPictureLabelValue();
      }
    }
    return null;
  }

  public long getCreationTimeMs() {
    return creationTimeMs;
  }

  public long getFirstTimestamp() {
    return cropRange == null ? recordingRange.getStartMs() : cropRange.getStartMs();
  }

  public long getLastTimestamp() {
    return cropRange == null ? recordingRange.getEndMs() : cropRange.getEndMs();
  }

  public long getOriginalFirstTimestamp() {
    return recordingRange.getStartMs();
  }

  public long getOriginalLastTimestamp() {
    return recordingRange.getEndMs();
  }

  public void setRecordingEndTime(long recordingEndTime) {
    recordingRange = recordingRange.toBuilder().setEndMs(recordingEndTime).build();
  }

  public Range getOriginalRecordingRange() {
    return recordingRange;
  }

  public Range getCropRange() {
    return cropRange;
  }

  public void setCropRange(Range cropRange) {
    this.cropRange = cropRange;
  }

  public List<String> getSensorIds() {
    List<String> result = new ArrayList<>();
    for (SensorLayoutPojo layout : sensorLayouts) {
      result.add(layout.getSensorId());
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
    if (TextUtils.isEmpty(title)) {
      return context.getString(R.string.default_trial_title, trialNumberInExperiment);
    } else {
      return title;
    }
  }

  public String getRawTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean isArchived) {
    this.archived = isArchived;
  }

  public GoosciTrial.Trial getTrialProto() {
    GoosciTrial.Trial.Builder trial =
        GoosciTrial.Trial.newBuilder()
            .setTrialId(trialId)
            .setCreationTimeMs(creationTimeMs)
            .setArchived(archived)
            .setRecordingRange(recordingRange)
            .setAutoZoomEnabled(autoZoomEnabled)
            .setTrialNumberInExperiment(trialNumberInExperiment);
    for (TrialStats stats : trialStats.values()) {
      trial.addTrialStats(stats.getSensorTrialStatsProto());
    }
    for (Label label : labels) {
      trial.addLabels(label.getLabelProto());
    }
    if (title != null) {
      trial.setTitle(title);
    }
    if (cropRange != null) {
      trial.setCropRange(cropRange);
    }
    ArrayList<SensorLayout> layouts = new ArrayList<>();
    for (SensorLayoutPojo layoutPojo : sensorLayouts) {
      layouts.add(layoutPojo.toProto());
    }
    trial.addAllSensorLayouts(layouts);
    return trial.build();
  }

  public GoosciTrial.Trial toProto() {
    return getTrialProto();
  }

  public List<SensorLayoutPojo> getSensorLayouts() {
    return sensorLayouts;
  }

  @VisibleForTesting
  public void setSensorLayouts(List<SensorLayoutPojo> sensorLayouts) {
    this.sensorLayouts = sensorLayouts;
  }

  public boolean getAutoZoomEnabled() {
    return autoZoomEnabled;
  }

  public void setAutoZoomEnabled(boolean enableAutoZoom) {
    this.autoZoomEnabled = enableAutoZoom;
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
    return trialId;
  }

  public String getCaptionText() {
    if (caption == null) {
      return "";
    }
    return caption.getText();
  }

  public void setCaption(GoosciCaption.Caption caption) {
    this.caption = caption;
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
    for (AppearanceEntry entry : sensorAppearances) {
      appearances.put(entry.getSensorId(), entry.getRememberedAppearance());
    }
    return appearances;
  }

  @Override
  public String toString() {
    return "Trial{"
        + "trialId='"
        + trialId
        + '\''
        + ", creationTimeMs="
        + creationTimeMs
        + ", title='"
        + title
        + '\''
        + ", archived="
        + archived
        + ", recordingRange="
        + recordingRange
        + ", cropRange="
        + cropRange
        + ", autoZoomEnabled="
        + autoZoomEnabled
        + ", sensorLayouts="
        + sensorLayouts
        + ", labels="
        + labels
        + ", sensorAppearances="
        + sensorAppearances
        + ", trialNumberInExperiment="
        + trialNumberInExperiment
        + ", caption="
        + caption
        + ", trialStats="
        + trialStats
        + ", onLabelChangeListener="
        + onLabelChangeListener
        + ", labels="
        + labels
        + '}';
  }

  public void setTrialNumberInExperiment(int trialNumberInExperiment) {
    this.trialNumberInExperiment = trialNumberInExperiment;
  }

  public int getTrialNumberInExperiment() {
    return trialNumberInExperiment;
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
