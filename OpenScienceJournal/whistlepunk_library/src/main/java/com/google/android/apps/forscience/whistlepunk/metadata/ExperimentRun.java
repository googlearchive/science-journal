/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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
    public static final long NO_TIME_SPECIFIED = -1;

    private final long mStartTime;
    private final long mStopTime;
    private final ArrayList<Label> mLabels;
    private final Run mRun;
    private String mExperimentId;
    private CropHelper.CropLabels mCropLabels;

    public static ExperimentRun fromLabels(Run run, List<Label> allLabels) {
        long startTime = NO_TIME_SPECIFIED;
        long stopTime = NO_TIME_SPECIFIED;
        ArrayList<Label> labels = new ArrayList<>();
        CropHelper.CropLabels cropLabels = new CropHelper.CropLabels();
        String experimentId = null;
        for (Label label : allLabels) {
            if (label instanceof ApplicationLabel) {
                ApplicationLabel appLabel = (ApplicationLabel) label;
                if (appLabel.getType() == ApplicationLabel.TYPE_RECORDING_START) {
                    startTime = appLabel.getTimeStamp();
                    experimentId = appLabel.getExperimentId();
                } else if (appLabel.getType() == ApplicationLabel.TYPE_RECORDING_STOP) {
                    stopTime = appLabel.getTimeStamp();
                } else if (appLabel.getType() == ApplicationLabel.TYPE_CROP_START) {
                    cropLabels.cropStartLabel = (ApplicationLabel) label;
                } else if (appLabel.getType() == ApplicationLabel.TYPE_CROP_END) {
                    cropLabels.cropEndLabel = (ApplicationLabel) label;
                }
            } else {
                labels.add(label);
            }
        }
        return new ExperimentRun(run, experimentId, startTime, stopTime, labels, cropLabels);
    }

    private ExperimentRun(Run run, String experimentId, long startTime, long stopTime,
            ArrayList<Label> labels, CropHelper.CropLabels cropLabels) {
        mExperimentId = experimentId;
        mStartTime = startTime;
        mStopTime = stopTime;
        mLabels = labels;
        mCropLabels = cropLabels;
        mRun = run;
    }

    public int getNoteCount() {
        return mLabels.size();
    }

    public ArrayList<Label> getPinnedNotes() {
        return mLabels;
    }

    public PictureLabel getCoverPictureLabel() {
        for (Label label : mLabels) {
            if (label instanceof PictureLabel) {
                return (PictureLabel) label;
            }
        }
        return null;
    }

    public String getExperimentId() {
        return mExperimentId;
    }

    public long getFirstTimestamp() {
        return mCropLabels.cropStartLabel == null ?
                mStartTime : mCropLabels.cropStartLabel.getTimeStamp();
    }

    public long getLastTimestamp() {
        return mCropLabels.cropEndLabel == null ?
                mStopTime : mCropLabels.cropEndLabel.getTimeStamp();
    }

    public long getOriginalFirstTimestamp() {
        return mStartTime;
    }

    public long getOriginalLastTimestamp() {
        return mStopTime;
    }

    public CropHelper.CropLabels getCropLabels() {
        return mCropLabels;
    }

    public void setCropLabels(CropHelper.CropLabels cropLabels) {
        mCropLabels = cropLabels;
    }

    public List<String> getSensorTags() {
        return mRun.getSensorIds();
    }

    public long elapsedSeconds() {
        return Math.round((getLastTimestamp() - getFirstTimestamp()) / 1000.0);
    }

    public String getRunId() {
        return mRun.getId();
    }

    public boolean isValidRun() {
        return mStartTime > 0 && mStopTime > mStartTime;
    }

    public String getRunTitle(Context context) {
        if (TextUtils.isEmpty(mRun.getTitle())) {
            if (mRun.getRunIndex() != -1) {
                return context.getString(R.string.run_label,
                        Integer.toString(mRun.getRunIndex() + 1));
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
