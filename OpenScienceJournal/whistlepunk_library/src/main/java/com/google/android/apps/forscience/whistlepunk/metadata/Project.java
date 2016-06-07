package com.google.android.apps.forscience.whistlepunk.metadata;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.R;
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

    public String getDisplayTitle(Context context) {
        return !TextUtils.isEmpty(mTitle) ? mTitle : context.getString(R.string.default_project_name);
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
