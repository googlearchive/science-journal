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

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.common.annotations.VisibleForTesting;

/**
 * An Experiment is a grouping of data sources and labels.
 */
public class Experiment {

    // This ID is not the same as the experiment ID. It is an index or unique ID into the database.
    private long mId;

    // This is the experiment's unique ID.
    private String mExperimentId;

    private long mTimestamp;
    private String mTitle;
    private String mDescription;
    private boolean mArchived;
    private String mProjectId;
    private long mLastUsedTime;

    // TODO(justinkoh): add datasources when they get defined.

    @VisibleForTesting
    public Experiment(long id) {
        mId = id;
        mTimestamp = System.currentTimeMillis();
    }

    /* package*/ long getId() {
        return mId;
    }

    @VisibleForTesting
    public void setExperimentId(String experimentId) {
        mExperimentId = experimentId;
    }

    @VisibleForTesting
    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    public void setArchived(boolean archived) {
        this.mArchived = archived;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public void setDescription(String description) {
        this.mDescription = description;
    }

    public void setProjectId(String projectId) {
        this.mProjectId = projectId;
    }

    public String getExperimentId() {
        return mExperimentId;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDisplayTitle(Context context) {
        return !TextUtils.isEmpty(mTitle) ? mTitle : context.getString(
                R.string.default_experiment_name);
    }

    public String getDescription() {
        return mDescription;
    }

    public boolean isArchived() {
        return mArchived;
    }

    public String getProjectId() {
        return mProjectId;
    }

    public void setLastUsedTime(long lastUsedTime) {
        mLastUsedTime = lastUsedTime;
    }

    public long getLastUsedTime() {
        return mLastUsedTime;
    }

    // For testing purposes only
    @Override
    public String toString() {
        return "Experiment{" +
                "mId=" + mId +
                ", mTitle='" + mTitle + '\'' +
                '}';
    }

    public static String getExperimentId(Experiment experiment) {
        return experiment == null ? null : experiment.getExperimentId();
    }
}
