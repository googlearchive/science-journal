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

@Deprecated
public class ExperimentRun {
    private Trial mTrial;
    private String mExperimentId;

    private ExperimentRun(Trial trial, String experimentId, CropHelper.CropLabels unused) {
        mTrial = trial;
        mExperimentId = experimentId;
    }

    public String getExperimentId() {
        return mExperimentId;
    }

    public long getFirstTimestamp() {
        return mTrial.getFirstTimestamp();
    }

    public List<String> getSensorIds() {
        return mTrial.getSensorIds();
    }

    public String getTrialId() {
        return mTrial.getTrialId();
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
}
