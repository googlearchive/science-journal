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

import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.common.annotations.VisibleForTesting;

import java.util.List;

/**
 * An Experiment is a grouping of data sources and labels.
 */
public class Experiment {
    private com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment mExperiment;
    private String mExperimentId;

    @VisibleForTesting
    public Experiment(long timestamp) {
        mExperiment = com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment
                .newExperiment(timestamp);
    }

    public Experiment(
            com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment experiment,
            String experimentId) {
        mExperiment = experiment;
        mExperimentId = experimentId;
    }

    @VisibleForTesting
    public void setExperimentId(String experimentId) {
        mExperimentId = experimentId;
    }

    public String getExperimentId() {
        return mExperimentId;
    }

    public com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment getExperiment() {
        return mExperiment;
    }

    // For testing purposes only
    @Override
    public String toString() {
        return "Experiment{mTitle='" + mExperiment.getTitle() + '\'' + '}';
    }

    public static String getExperimentId(Experiment experiment) {
        return experiment == null ? null : experiment.getExperimentId();
    }
}