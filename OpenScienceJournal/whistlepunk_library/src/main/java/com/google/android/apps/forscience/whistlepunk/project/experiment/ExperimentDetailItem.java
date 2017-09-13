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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;

/**
 * Represents a detail item: either a run or an experiment level label or a special card.
 * <p>
 * TODO: might be able to rework this when Run objects exist.
 */
public class ExperimentDetailItem {
    private final int mViewType;
    private Trial mTrial;
    private int mSensorTagIndex = -1;
    private Label mLabel;
    private long mTimestamp;
    private ChartController mChartController;

    ExperimentDetailItem(Trial trial, ScalarDisplayOptions scalarDisplayOptions,
            boolean isRecording) {
        mTrial = trial;
        mTimestamp = mTrial.getFirstTimestamp();
        if (isRecording) {
            mViewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_RECORDING;
        } else {
            mViewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_RUN_CARD;
        }
        mSensorTagIndex = mTrial.getSensorIds().size() > 0 ? 0 : -1;
        if (trial.isValid()) {
            mChartController = new ChartController(
                    ChartOptions.ChartPlacementType.TYPE_PREVIEW_REVIEW,
                    scalarDisplayOptions);
        }
    }

    ExperimentDetailItem(Label label) {
        mLabel = label;
        if (label.getType() == GoosciLabel.Label.TEXT) {
            mViewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_EXPERIMENT_TEXT_LABEL;
        } else if (label.getType() == GoosciLabel.Label.PICTURE) {
            mViewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_EXPERIMENT_PICTURE_LABEL;
        } else if (label.getType() == GoosciLabel.Label.SENSOR_TRIGGER) {
            mViewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
        } else if (label.getType() == GoosciLabel.Label.SNAPSHOT) {
            mViewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_SNAPSHOT_LABEL;
        } else {
            mViewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_UNKNOWN_LABEL;
        }
        mTimestamp = label.getTimeStamp();
    }

    ExperimentDetailItem(int viewType) {
        mViewType = viewType;
    }

    int getViewType() {
        return mViewType;
    }

    long getTimestamp() {
        return mTimestamp;
    }

    Trial getTrial() {
        return mTrial;
    }

    int getSensorTagIndex() {
        return mSensorTagIndex;
    }

    GoosciSensorLayout.SensorLayout getSelectedSensorLayout() {
        return mTrial.getSensorLayouts().get(mSensorTagIndex);
    }

    String getNextSensorId() {
        return mTrial.getSensorIds().get(mSensorTagIndex + 1);
    }

    String getPrevSensorId() {
        return mTrial.getSensorIds().get(mSensorTagIndex - 1);
    }

    void setSensorTagIndex(int index) {
        mSensorTagIndex = index;
    }

    ChartController getChartController() {
        return mChartController;
    }

    public Label getLabel() {
        return mLabel;
    }

    public void setLabel(Label label) {
        mLabel = label;
    }

    public void setTrial(Trial trial) {
        mTrial = trial;
    }
}
