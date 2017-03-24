package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a recorded trial
 */
public class Trial {
    private GoosciTrial.Trial mTrial;
    private List<Label> mLabels;
    private Map<String, TrialStats> mTrialStats;

    // TODO: Does a trial need to track the index and name of the experiment it is in for
    // user navigation? Probably we can track that elsewhere.

    public Trial(GoosciTrial.Trial trial, List<Label> labels) {
        mTrial = trial;
        mLabels = labels;
        mTrialStats = TrialStats.fromTrial(mTrial);
    }

    // When recording starts on a trial, this is the bare minimum of information needed to save it.
    public Trial(long startTimeMs, GoosciSensorLayout.SensorLayout[] sensorLayouts,
            String dataResourceId) {
        mTrial = new GoosciTrial.Trial();
        mTrial.recordingRange = new GoosciTrial.Range();
        mTrial.recordingRange.startMs = startTimeMs;
        mTrial.sensorLayouts = sensorLayouts;
        mTrial.trialDataResourceId = dataResourceId;
        mLabels = new ArrayList<>();
        mTrialStats = new HashMap<>();
    }

    public List<Label> getLabels() {
        return mLabels;
    }

    public void setLabels(List<Label> labels) {
        mLabels = labels;
    }

    public Label getCoverPictureLabel() {
        for (Label label : mLabels) {
            if (label.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
                return label;
            }
        }
        return null;
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

    public GoosciTrial.Range getCropRange() {
        return mTrial.cropRange;
    }

    public void setCropRange(GoosciTrial.Range cropRange) {
        mTrial.cropRange = cropRange;
    }

    public List<String> getSensorTags() {
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
        return mTrial;
    }

    public List<GoosciSensorLayout.SensorLayout> getSensorLayouts() {
        return Arrays.asList(mTrial.sensorLayouts);
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

    public String getDataResourceId() {
        return mTrial.trialDataResourceId;
    }

    public void setDataResourceId(String resourceId) {
        mTrial.trialDataResourceId = resourceId;
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
}
