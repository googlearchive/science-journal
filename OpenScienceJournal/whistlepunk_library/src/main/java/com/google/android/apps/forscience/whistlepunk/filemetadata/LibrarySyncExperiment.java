/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.SyncExperiment;

/**
 * Corresponds with an experiment that is tracked by the Experiment Library manager. Produces
 * the proto used to write to the Experiment Library proto.
 */
class LibrarySyncExperiment {
  private String fileId;
  private String experimentId;
  private long lastOpened;
  private long lastModified;
  private boolean deleted;
  private boolean archived;

  public LibrarySyncExperiment(String experimentId) {
    this.experimentId = experimentId;
  }

  public LibrarySyncExperiment(
      String experimentId,
      String fileId,
      long lastOpened,
      long lastModified,
      boolean deleted,
      boolean archived) {
    this.fileId = fileId;
    this.experimentId = experimentId;
    this.lastOpened = lastOpened;
    this.lastModified = lastModified;
    this.deleted = deleted;
    this.archived = archived;
  }

  public String getFileId() {
    return fileId;
  }

  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  public String getExperimentId() {
    return experimentId;
  }

  public void setExperimentId(String experimentId) {
    this.experimentId = experimentId;
  }

  public long getLastOpened() {
    return lastOpened;
  }

  public void setLastOpened(long lastOpened) {
    this.lastOpened = lastOpened;
  }

  public long getLastModified() {
    return lastModified;
  }

  public void setLastModified(long lastModified) {
    this.lastModified = lastModified;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  SyncExperiment generateProto() {
    SyncExperiment.Builder builder = SyncExperiment.newBuilder();
    if (fileId != null) {
      builder.setFileId(fileId);
    }
    if (experimentId != null) {
      builder.setExperimentId(experimentId);
    }
    return builder
        .setLastOpened(lastOpened)
        .setLastModified(lastModified)
        .setDeleted(deleted)
        .setArchived(archived)
        .build();
  }
}
