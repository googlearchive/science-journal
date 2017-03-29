package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.common.annotations.VisibleForTesting;

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
    public static final Comparator<Trial> COMPARATOR_BY_TIMESTAMP = new Comparator<Trial>() {
        @Override
        public int compare(Trial first, Trial second) {
            // Sort based on the recording first timestamp.
            return Long.compare(first.getOriginalFirstTimestamp(),
                    second.getOriginalFirstTimestamp());
        }
    };

    private GoosciTrial.Trial mTrial;
    private Map<String, TrialStats> mTrialStats;

    /**
     * Populates the Trial from an existing proto.
     */
    public static Trial fromTrial(GoosciTrial.Trial trial) {
        return new Trial(trial);
    }

    /**
     * When recording starts on a trial, this is the bare minimum of information needed to save it.
     */
    public static Trial newTrial(long startTimeMs, GoosciSensorLayout.SensorLayout[] sensorLayouts) {
        String trialId = java.util.UUID.randomUUID().toString();
        return new Trial(startTimeMs, sensorLayouts, trialId);
    }

    private Trial(GoosciTrial.Trial trial) {
        mTrial = trial;
        mTrialStats = TrialStats.fromTrial(mTrial);
        mLabels = new ArrayList<>();
        for (GoosciLabel.Label proto : mTrial.labels) {
            mLabels.add(Label.fromLabel(proto));
        }
    }

    private Trial(long startTimeMs, GoosciSensorLayout.SensorLayout[] sensorLayouts,
            String trialId) {
        mTrial = new GoosciTrial.Trial();
        mTrial.recordingRange = new GoosciTrial.Range();
        mTrial.recordingRange.startMs = startTimeMs;
        mTrial.sensorLayouts = sensorLayouts;
        mTrial.trialId = trialId;
        mLabels = new ArrayList<>();
        mTrialStats = new HashMap<>();
    }

    public Label getCoverPictureLabel() {
        for (Label label : mLabels) {
            if (label.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
                return label;
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

    public String getTitle(Context context) {
        if (TextUtils.isEmpty(mTrial.title)) {
            // TODO: Naming depends on UX answer to b/36514241.
            return context.getString(R.string.run_label, "");
            /*if (mRun.getRunIndex() != -1) {
                return context.getString(R.string.run_label,
                        Integer.toString(mRun.getRunIndex() + 1));
            } else {
                return context.getString(R.string.run_label, "");
            }*/
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
        mTrial.sensorLayouts = sensorLayouts.toArray(new GoosciSensorLayout.SensorLayout[
                sensorLayouts.size()]);
    }

    public boolean getAutoZoomEnabled() {
        return mTrial.autoZoomEnabled;
    }

    public void setAutoZoomEnabled(boolean enableAutoZoom) {
        mTrial.autoZoomEnabled = enableAutoZoom;
    }

    public TrialStats getStatsForSensor(String sensorId) {
        return mTrialStats.get(sensorId);
    }

    public void setStats(TrialStats newTrialStats) {
        mTrialStats.put(newTrialStats.getSensorId(), newTrialStats);
    }

    // The Trial ID cannot be set after it is created.
    public String getTrialId() {
        return mTrial.trialId;
    }

    /**
     * Deletes the trial and any assets associated with it, including labels and label pictures,
     * run data, etc.
     */
    public void deleteContents() {
        for (Label label : mLabels) {
            deleteLabel(label);
        }
        // TODO: Also delete any assets associated with this trial, inc. sensor data, icons, etc.
        // May need a reference to the FileMetadataManager to do this.
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
}
