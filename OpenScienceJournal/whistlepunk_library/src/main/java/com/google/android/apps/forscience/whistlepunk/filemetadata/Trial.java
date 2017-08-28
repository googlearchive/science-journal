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
import android.util.Log;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProviderImpl;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a recorded trial.
 * All changes should be made using the getters and setters provided, rather than by getting the
 * underlying protocol buffer and making changes to that directly.
 */
public class Trial extends LabelListHolder {
    private static final String TAG = "Trial";

    public static final Comparator<Trial> COMPARATOR_BY_TIMESTAMP = (first, second) -> {
        // Sort based on the recording first timestamp.
        return Long.compare(first.getOriginalFirstTimestamp(),
                second.getOriginalFirstTimestamp());
    };

    interface OnLabelChangeListener {
        void onPictureLabelAdded(Label label);
        void beforeDeletingPictureLabel(Label label);
    }

    private GoosciTrial.Trial mTrial;
    private Map<String, TrialStats> mTrialStats;
    private OnLabelChangeListener mOnLabelChangeListener;

    /**
     * Populates the Trial from an existing proto.
     */
    public static Trial fromTrial(GoosciTrial.Trial trial) {
        return new Trial(trial);
    }

    /**
     * Invoked when recording begins to save the metadata about what's being recorded.
     */
    public static Trial newTrial(long startTimeMs, GoosciSensorLayout.SensorLayout[] sensorLayouts,
            SensorAppearanceProvider provider, Context context) {
        String trialId = java.util.UUID.randomUUID().toString();
        return new Trial(startTimeMs, sensorLayouts, trialId, provider, context);
    }

    private Trial(GoosciTrial.Trial trial) {
        mTrial = trial;
        mTrialStats = TrialStats.fromTrial(mTrial);
        mLabels = new ArrayList<>();
        for (GoosciLabel.Label proto : mTrial.labels) {
            mLabels.add(Label.fromLabel(proto));
        }
    }

    // TODO: eventually provider should go away, in favor of a different structure containing
    // sensor_specs
    private Trial(long startTimeMs, GoosciSensorLayout.SensorLayout[] sensorLayouts,
            String trialId, SensorAppearanceProvider provider, Context context) {
        mTrial = new GoosciTrial.Trial();
        mTrial.creationTimeMs = startTimeMs;
        mTrial.recordingRange = new GoosciTrial.Range();
        mTrial.recordingRange.startMs = startTimeMs;
        mTrial.sensorLayouts = sensorLayouts;
        mTrial.trialId = trialId;

        mTrial.sensorAppearances = new GoosciTrial.Trial.AppearanceEntry[sensorLayouts.length];
        for (int i = 0; i < sensorLayouts.length; i++) {
            GoosciTrial.Trial.AppearanceEntry entry = new GoosciTrial.Trial.AppearanceEntry();
            GoosciSensorLayout.SensorLayout layout = sensorLayouts[i];
            entry.sensorId = layout.sensorId;
            entry.rememberedAppearance =
                    SensorAppearanceProviderImpl.appearanceToProto(
                            provider.getAppearance(layout.sensorId), context);
            mTrial.sensorAppearances[i] = entry;
        }

        mLabels = new ArrayList<>();
        mTrialStats = new HashMap<>();
    }

    public GoosciPictureLabelValue.PictureLabelValue getCoverPictureLabelValue() {
        for (Label label : mLabels) {
            if (label.getType() == GoosciLabel.Label.PICTURE) {
                return label.getPictureLabelValue();
            }
        }
        return null;
    }

    public long getCreationTimeMs() {
        return mTrial.creationTimeMs;
    }

    public long getFirstTimestamp() {
        return mTrial.cropRange == null ?
                mTrial.recordingRange.startMs : mTrial.cropRange.startMs;
    }

    public long getLastTimestamp() {
        return mTrial.cropRange == null ?
                mTrial.recordingRange.endMs : mTrial.cropRange.endMs;
    }

    public long getOriginalFirstTimestamp() {
        return mTrial.recordingRange.startMs;
    }

    public long getOriginalLastTimestamp() {
        return mTrial.recordingRange.endMs;
    }

    public void setRecordingEndTime(long recordingEndTime) {
        mTrial.recordingRange.endMs = recordingEndTime;
    }

    public GoosciTrial.Range getOriginalRecordingRange() {
        return mTrial.recordingRange;
    }

    public GoosciTrial.Range getCropRange() {
        return mTrial.cropRange;
    }

    public void setCropRange(GoosciTrial.Range cropRange) {
        mTrial.cropRange = cropRange;
    }

    public List<String> getSensorIds() {
        List<String> result = new ArrayList<>();
        for (GoosciSensorLayout.SensorLayout layout : mTrial.sensorLayouts) {
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
        return getOriginalFirstTimestamp() > 0 && getOriginalLastTimestamp() >
                getOriginalFirstTimestamp();
    }

    public String getTitleWithDuration(Context context) {
        return context.getString(R.string.title_with_duration, getTitle(context),
                ElapsedTimeFormatter.getInstance(context).format(elapsedSeconds()));
    }

    public String getTitle(Context context) {
        if (TextUtils.isEmpty(mTrial.title)) {
            return context.getString(R.string.default_trial_title, mTrial.trialNumberInExperiment);
        } else {
            return mTrial.title;
        }
    }

    public String getRawTitle() {
        return mTrial.title;
    }

    public void setTitle(String title) {
        mTrial.title = title;
    }

    public boolean isArchived() {
        return mTrial.archived;
    }

    public void setArchived(boolean isArchived) {
        mTrial.archived = isArchived;
    }

    public GoosciTrial.Trial getTrialProto() {
        updateTrialProtoWithStats();
        updateTrialProtoWithLabels();
        return mTrial;
    }

    public List<GoosciSensorLayout.SensorLayout> getSensorLayouts() {
        return new ArrayList(Arrays.asList(mTrial.sensorLayouts));
    }

    @VisibleForTesting
    public void setSensorLayouts(List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        Preconditions.checkNotNull(sensorLayouts);
        mTrial.sensorLayouts = sensorLayouts.toArray(new GoosciSensorLayout.SensorLayout[
                sensorLayouts.size()]);
    }

    public boolean getAutoZoomEnabled() {
        return mTrial.autoZoomEnabled;
    }

    public void setAutoZoomEnabled(boolean enableAutoZoom) {
        mTrial.autoZoomEnabled = enableAutoZoom;
    }

    /**
     * Gets a list of the stats for all sensors.
     */
    public List<TrialStats> getStats() {
        return new ArrayList<>(mTrialStats.values());
    }

    /**
     * Gets the stats for a sensor.
     */
    public TrialStats getStatsForSensor(String sensorId) {
        return mTrialStats.get(sensorId);
    }

    /**
     * Sets the stats for a sensor. This will overwrite existing stats.
     * @param newTrialStats The new stats to save.
     */
    public void setStats(TrialStats newTrialStats) {
        mTrialStats.put(newTrialStats.getSensorId(), newTrialStats);
    }

    // The Trial ID cannot be set after it is created.
    public String getTrialId() {
        return mTrial.trialId;
    }

    public String getCaptionText() {
        if (mTrial.caption == null) {
            return "";
        }
        return mTrial.caption.text;
    }

    public void setCaption(GoosciCaption.Caption caption) {
        mTrial.caption = caption;
    }

    /**
     * Deletes the trial and any assets associated with it, including labels and label pictures,
     * run data, etc.
     */
    public void deleteContents(Context context, String experimentId) {
        for (Label label : mLabels) {
            deleteLabelAssets(label, context, experimentId);
        }
        AppSingleton.getInstance(context).getDataController().deleteTrialData(this,
                MaybeConsumers.expectSuccess(new FailureListener() {
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
        GoosciTrial.SensorTrialStats[] result =
                new GoosciTrial.SensorTrialStats[mTrialStats.size()];
        int i = 0;
        for (String key : mTrialStats.keySet()) {
            result[i++] = mTrialStats.get(key).getSensorTrialStatsProto();
        }
        mTrial.trialStats = result;
    }

    private void updateTrialProtoWithLabels() {
        GoosciLabel.Label[] result = new GoosciLabel.Label[mLabels.size()];
        for (int i = 0; i < mLabels.size(); i++) {
            result[i] = mLabels.get(i).getLabelProto();
        }
        mTrial.labels = result;
    }

    public void setOnLabelChangeListener(OnLabelChangeListener listener) {
        mOnLabelChangeListener = listener;
    }

    @Override
    protected void onPictureLabelAdded(Label label) {
        if (mOnLabelChangeListener != null) {
            mOnLabelChangeListener.onPictureLabelAdded(label);
        }
    }

    @Override
    protected void beforeDeletingPictureLabel(Label label) {
        if (mOnLabelChangeListener != null) {
            mOnLabelChangeListener.beforeDeletingPictureLabel(label);
        }
    }

    /**
     * @return a map of sensor ids (as returned from {@link #getSensorIds()}) to appearance
     * protos.  This map should not be changed; changes have no effect.
     */
    public Map<String, GoosciSensorAppearance.BasicSensorAppearance> getAppearances() {
        // TODO: need a putAppearance method for changes
        HashMap<String, GoosciSensorAppearance.BasicSensorAppearance> appearances = new HashMap<>();
        for (GoosciTrial.Trial.AppearanceEntry entry : mTrial.sensorAppearances) {
            appearances.put(entry.sensorId, entry.rememberedAppearance);
        }
        return appearances;
    }

    @Override
    public String toString() {
        return "Trial{" +
               "mTrial=" + mTrial +
               ", mTrialStats=" + mTrialStats +
               '}';
    }

    public void setTrialNumberInExperiment(int trialNumberInExperiment) {
        mTrial.trialNumberInExperiment = trialNumberInExperiment;
    }

    public int getTrialNumberInExperiment() {
        return mTrial.trialNumberInExperiment;
    }
}
