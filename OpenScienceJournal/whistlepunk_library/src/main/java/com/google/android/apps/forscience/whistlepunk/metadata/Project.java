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

import android.text.TextUtils;

import com.google.common.annotations.VisibleForTesting;

/**
 * Represents a project, which is a collection of experiments.
 */
public class Project {

    private long mId;
    private String mProjectId;
    private String mTitle;
    private String mDescription;
    private String mCoverPhoto;
    private boolean mArchived;
    private long mLastUsedTime;

    @VisibleForTesting
    public Project(long id) {
        mId = id;
    }

    /* package */ long getId() {
        return mId;
    }

    /* package */ void setProjectId(String projectId) {
        mProjectId = projectId;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setCoverPhoto(String coverPhoto) {
        this.mCoverPhoto = coverPhoto;
    }

    public void setArchived(boolean archived) {
        this.mArchived = archived;
    }

    public String getProjectId() {
        return mProjectId;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getCoverPhoto() {
        return mCoverPhoto;
    }

    public boolean isArchived() {
        return mArchived;
    }

    public void setLastUsedTime(long lastUsedTime) {
        mLastUsedTime = lastUsedTime;
    }

    public long getLastUsedTime() {
        return mLastUsedTime;
    }

    @Override
    public boolean equals(Object o) {
        Project other = (Project) o;
        if (other == null) {
            return false;
        }
        boolean returnValue = mId == other.mId
                && TextUtils.equals(mTitle, other.mTitle)
                && mArchived == other.mArchived
                && TextUtils.equals(mDescription, other.mDescription)
                && TextUtils.equals(mCoverPhoto, other.mCoverPhoto)
                && mLastUsedTime == other.mLastUsedTime;
        return returnValue;
    }
}
