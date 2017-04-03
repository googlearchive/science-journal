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

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;

import java.util.List;

public class ExperimentRun {
    private final List<Label> mLabels;

    private Trial mTrial;
    private String mExperimentId;
    private CropHelper.CropLabels mCropLabels; // Need these around for writing to the DB

    public static ExperimentRun fromLabels(Trial trial, String experimentId,
            List<ApplicationLabel> applicationLabels, List<Label> labels) {
        CropHelper.CropLabels cropLabels = new CropHelper.CropLabels();
        for (ApplicationLabel label : applicationLabels) {
            if (label.getType() == ApplicationLabel.TYPE_CROP_START) {
                cropLabels.cropStartLabel = label;
            } else if (label.getType() == ApplicationLabel.TYPE_CROP_END) {
                cropLabels.cropEndLabel = label;
            }
        }
        return new ExperimentRun(trial, experimentId, labels, cropLabels);
    }

    private ExperimentRun(Trial trial, String experimentId,
            List<Label> labels, CropHelper.CropLabels cropLabels) {
        mTrial = trial;
        mExperimentId = experimentId;
        mLabels = labels;
        mCropLabels = cropLabels;
    }

    public int getNoteCount() {
        return mTrial.getLabelCount();
    }

    public List<Label> getPinnedNotes() {
        // TODO: Switch to List<filemetadata.Label when possible
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
        return mTrial.getFirstTimestamp();
    }

    public long getLastTimestamp() {
        return mTrial.getLastTimestamp();
    }

    public long getOriginalFirstTimestamp() {
        return mTrial.getOriginalFirstTimestamp();
    }

    public long getOriginalLastTimestamp() {
        return mTrial.getOriginalLastTimestamp();
    }

    public CropHelper.CropLabels getCropLabels() {
        // TODO: Delete crop labels and use trial crop range during DB to File upgrade.
        return mCropLabels;
    }

    public void setCropLabels(CropHelper.CropLabels cropLabels) {
        mCropLabels = cropLabels;
        GoosciTrial.Range cropRange = new GoosciTrial.Range();
        cropRange.startMs = cropLabels.cropStartLabel.getTimeStamp();
        cropRange.endMs = cropLabels.cropEndLabel.getTimeStamp();
        mTrial.setCropRange(cropRange);
    }

    public List<String> getSensorIds() {
        return mTrial.getSensorIds();
    }

    public long elapsedSeconds() {
        return mTrial.elapsedSeconds();
    }

    public String getTrialId() {
        return mTrial.getTrialId();
    }

    public boolean isValidRun() {
        return mTrial.isValid();
    }

    public String getRunTitle(Context context) {
        return mTrial.getTitle(context);
    }

    public void setRunTitle(String title) {
        mTrial.setTitle(title);
    }

    public boolean isArchived() {
        return mTrial.isArchived();
    }

    public void setArchived(boolean isArchived) {
        mTrial.setArchived(isArchived);
    }

    public Trial getTrial() {
        return mTrial;
    }

    public List<GoosciSensorLayout.SensorLayout> getSensorLayouts() {
        return mTrial.getSensorLayouts();
    }

    public boolean getAutoZoomEnabled() {
        return mTrial.getAutoZoomEnabled();
    }

    public void setAutoZoomEnabled(boolean enableAutoZoom) {
        mTrial.setAutoZoomEnabled(enableAutoZoom);
    }
}
