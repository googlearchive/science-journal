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

import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;

/** Represents a project, which is a collection of experiments. */
public class Project {

  private long id;
  private String projectId;
  private String title;
  private String description;
  private String coverPhoto;
  private boolean archived;
  private long lastUsedTime;

  @VisibleForTesting
  public Project(long id) {
    this.id = id;
  }

  /* package */ long getId() {
    return id;
  }

  /* package */ void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setCoverPhoto(String coverPhoto) {
    this.coverPhoto = coverPhoto;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getCoverPhoto() {
    return coverPhoto;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setLastUsedTime(long lastUsedTime) {
    this.lastUsedTime = lastUsedTime;
  }

  public long getLastUsedTime() {
    return lastUsedTime;
  }

  @Override
  public boolean equals(Object o) {
    Project other = (Project) o;
    if (other == null) {
      return false;
    }
    boolean returnValue =
        id == other.id
            && TextUtils.equals(title, other.title)
            && archived == other.archived
            && TextUtils.equals(description, other.description)
            && TextUtils.equals(coverPhoto, other.coverPhoto)
            && lastUsedTime == other.lastUsedTime;
    return returnValue;
  }
}
