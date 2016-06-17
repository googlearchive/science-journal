package com.google.android.apps.forscience.whistlepunk.metadata;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;

import java.util.ArrayList;
import java.util.List;

// TODO(saff): get all of this from the actual database
public class ExperimentRun {
    private final long mStartTime;
    private final long mStopTime;
    private final List<TextLabel> mTextLabels;
    private final List<PictureLabel> mPictureLabels;
    private final Run mRun;
    private String mExperimentId;

    public static ExperimentRun fromLabels(Run run, List<Label> allLabels) {
        long startTime = -1;
        long stopTime = -1;
        List<TextLabel> textLabels = new ArrayList<>();
        List<PictureLabel> pictureLabels = new ArrayList<>();
        String experimentId = null;
        for (Label label : allLabels) {
            if (label instanceof ApplicationLabel) {
                ApplicationLabel appLabel = (ApplicationLabel) label;
                if (appLabel.getType() == ApplicationLabel.TYPE_RECORDING_START) {
                    startTime = appLabel.getTimeStamp();
                    experimentId = appLabel.getExperimentId();
                } else if (appLabel.getType() == ApplicationLabel.TYPE_RECORDING_STOP) {
                    stopTime = appLabel.getTimeStamp();
                }
            } else if (label instanceof TextLabel) {
                textLabels.add((TextLabel) label);
            } else if (label instanceof PictureLabel) {
                pictureLabels.add((PictureLabel) label);
            }
        }
        return new ExperimentRun(run, experimentId, startTime, stopTime, textLabels, pictureLabels);
    }

    private ExperimentRun(Run run, String experimentId, long startTime, long stopTime,
                          List<TextLabel> textLabels, List<PictureLabel> pictureLabels) {
        mExperimentId = experimentId;
        mStartTime = startTime;
        mStopTime = stopTime;
        mTextLabels = textLabels;
        mPictureLabels = pictureLabels;
        mRun = run;
    }

    public int getNoteCount() {
        return mTextLabels.size() + mPictureLabels.size();
    }

    public List<TextLabel> getPinnedNotes() {
        return mTextLabels;
    }

    public List<PictureLabel> getPictureLabels() {
        return mPictureLabels;
    }

    public PictureLabel getCoverPictureLabel() {
        if (mPictureLabels.size() > 0) {
            return mPictureLabels.get(0);
        } else {
            return null;
        }
    }

    public String getExperimentId() {
        return mExperimentId;
    }

    public long getFirstTimestamp() {
        return mStartTime;
    }

    public long getLastTimestamp() {
        return mStopTime;
    }

    public List<String> getSensorTags() {
        return mRun.getSensorIds();
    }

    public long elapsedSeconds() {
        return Math.round((mStopTime - mStartTime) / 1000.0);
    }

    public String getRunId() {
        return mRun.getId();
    }

    public boolean isValidRun() {
        return mStartTime > 0 && mStopTime > mStartTime;
    }

    public int getRunIndex() {
        return mRun.getRunIndex();
    }

    public String getRunTitle(Context context) {
        if (TextUtils.isEmpty(mRun.getTitle())) {
            if (mRun.getRunIndex() != -1) {
                return context.getString(R.string.run_label, mRun.getRunIndex() + 1);
            } else {
                return context.getString(R.string.run_label, "");
            }
        } else {
            return mRun.getTitle();
        }
    }

    public void setRunTitle(String title) {
        mRun.setTitle(title);
    }

    public boolean isArchived() {
        return mRun.isArchived();
    }

    public void setArchived(boolean isArchived) {
        mRun.setArchived(isArchived);
    }

    public CharSequence getDisplayTime(Context context) {
        return DateUtils.getRelativeDateTimeString(context, getFirstTimestamp(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    public Run getRun() {
        return mRun;
    }

    public List<GoosciSensorLayout.SensorLayout> getSensorLayouts() {
        return mRun.getSensorLayouts();
    }

    public boolean getAutoZoomEnabled() {
        return mRun.getAutoZoomEnabled();
    }

    public void setAutoZoomEnabled(boolean enableAutoZoom) {
        mRun.setAutoZoomEnabled(enableAutoZoom);
    }
}
