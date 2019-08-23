/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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

import androidx.annotation.VisibleForTesting;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus.ExperimentStatus;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus.LocalSyncStatus;
import java.io.IOException;
import java.util.HashMap;

/**
 * Manages the Sync Status for Science Journal All changes should be made using the getters and
 * setters provided, rather than by getting the underlying protocol buffer and making changes to
 * that directly. Changes to the underlying proto outside this class may be overwritten and may not
 * be saved.
 */
public class LocalSyncManager {
  private static final String TAG = "localSyncManager";
  private final HashMap<String, ExperimentSyncStatus> statusMap = new HashMap<>();
  private long lastSyncedLibraryVersion = -1L;
  private final AppAccount account;

  /** Constructor for an LocalSyncManager that creates a new LocalSyncStatus proto. */
  public LocalSyncManager(AppAccount account) {
    this(null, account);
  }

  /** Constructor for an LocalSyncManager using an existing LocalSyncStatus. Useful for testing. */
  @VisibleForTesting
  public LocalSyncManager(LocalSyncStatus localSyncStatus, AppAccount account) {
    this.account = account;
    if (localSyncStatus != null) {
      setLocalSyncStatus(localSyncStatus);
    }
  }

  /**
   * Sets the LocalSyncStatus being managed.
   *
   * @param localSyncStatus The KicalSyncStatus to manage.
   */
  public void setLocalSyncStatus(LocalSyncStatus localSyncStatus) {
    lastSyncedLibraryVersion = localSyncStatus.getLastSyncedLibraryVersion();
    statusMap.clear();
    for (ExperimentStatus status : localSyncStatus.getExperimentStatusList()) {
      statusMap.put(status.getExperimentId(), new ExperimentSyncStatus(status));
    }
  }

  /**
   * Checks if an experiment is known.
   *
   * @param experimentId The id of the experiment to manage.
   */
  public boolean hasExperiment(String experimentId) {
    populateLocalSyncManager();
    return getExperimentStatus(experimentId) != null;
  }

  /**
   * Adds an Experiment to be managed.
   *
   * @param experimentId The id of the experiment to manage.
   */
  public void addExperiment(String experimentId) {
    populateLocalSyncManager();
    if (!statusMap.containsKey(experimentId)) {
      statusMap.put(experimentId, new ExperimentSyncStatus(experimentId));
      writeLocalSyncStatus();
    }
  }

  /**
   * Gets the Experiment status for a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return The ExperimentStatus of the experiment, or null if not found.
   */
  private ExperimentSyncStatus getExperimentStatus(String experimentId) {
    populateLocalSyncManager();
    return statusMap.get(experimentId);
  }

  /**
   * Sets the dirty bit of a given experiment id. Experiments where dirty is true have local changes
   * that must be synced to Drive.
   *
   * @param experimentId The id of the experiment to set status for.
   */
  public void setDirty(String experimentId, boolean dirty) {
    ExperimentSyncStatus status = getExperimentStatus(experimentId);
    if (status != null) {
      status.setDirty(dirty);
      if (dirty) {
        lastSyncedLibraryVersion = 0L;
      }
      writeLocalSyncStatus();
    }
  }

  /**
   * Gets the dirty bit of a given experiment id. Experiments where dirty is true have local changes
   * that must be synced to Drive.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return Whether or not the experiment has local changes.
   */
  public boolean getDirty(String experimentId) {
    ExperimentSyncStatus status = getExperimentStatus(experimentId);
    if (status == null) {
      return false;
    }
    return status.isDirty();
  }

  /**
   * Sets the last synced version of a given experiment id.
   *
   * @param experimentId The id of the experiment to set status for.
   * @param version The last version of the experiment synced to or from Drive.
   */
  public void setLastSyncedVersion(String experimentId, long version) {
    ExperimentSyncStatus status = getExperimentStatus(experimentId);
    if (status != null) {
      status.setLastSyncedVersion(version);
      writeLocalSyncStatus();
    }
  }

  /**
   * Gets the last synced version of a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return The last version of the experiment synced to or from Drive.
   */
  public long getLastSyncedVersion(String experimentId) {
    ExperimentSyncStatus status = getExperimentStatus(experimentId);
    if (status == null) {
      return -1L;
    }
    return status.getLastSyncedVersion();
  }

  /**
   * Sets the last archived status that we synced from the server of a given experiment id.
   *
   * @param experimentId The id of the experiment to set status for.
   * @param archived Whether the server says the experiment is archived.
   */
  public void setServerArchived(String experimentId, boolean archived) {
    ExperimentSyncStatus status = getExperimentStatus(experimentId);
    if (status != null) {
      status.setServerArchived(archived);
      writeLocalSyncStatus();
    }
  }

  /**
   * Gets the last archived status that we synced from the server of a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return Whether the server says the experiment is archived.
   */
  public boolean getServerArchived(String experimentId) {
    ExperimentSyncStatus status = getExperimentStatus(experimentId);
    if (status == null) {
      return false;
    }
    return status.isServerArchived();
  }

  /**
   * Sets whether the experiment has been downloaded to the device for a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @param downloaded Whether the experiment is downloaded.
   */
  public void setDownloaded(String experimentId, boolean downloaded) {
    ExperimentSyncStatus status = getExperimentStatus(experimentId);
    if (status != null) {
      status.setDownloaded(downloaded);
      writeLocalSyncStatus();
    }
  }

  /**
   * Gets whether the experiment has been downloaded to the device for a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return Whether the experiment is downloaded.
   */
  public boolean getDownloaded(String experimentId) {
    ExperimentSyncStatus status = getExperimentStatus(experimentId);
    if (status == null) {
      return true;
    }
    return status.isDownloaded();
  }

  public long getLastSyncedLibraryVersion() {
    populateLocalSyncManager();
    return lastSyncedLibraryVersion;
  }

  public void setLastSyncedLibraryVersion(long version) {
    populateLocalSyncManager();
    this.lastSyncedLibraryVersion = version;
    writeLocalSyncStatus();
  }

  private void writeLocalSyncStatus() {
    try {
      FileMetadataUtil.getInstance().writeLocalSyncStatusFile(generateProto(), account);
    } catch (IOException ioe) {
      // Would like to do something else here, but not sure what else there really is to do.
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "LocalSyncStatus Write failed", ioe);
      }
    }
  }

  // Reads the saved local sync status file from disk, if the Library has not already
  // been set to a non-null value. This lets us move initialization of this object to the background
  // thread.
  // TODO(b/111649596) Test this
  private void populateLocalSyncManager() {
    if (statusMap.isEmpty()) {
      LocalSyncStatus proto = FileMetadataUtil.getInstance().readLocalSyncStatusFile(account);
      setLocalSyncStatus(proto);
    }
  }

  private LocalSyncStatus generateProto() {
    LocalSyncStatus.Builder proto =
        LocalSyncStatus.newBuilder().setLastSyncedLibraryVersion(lastSyncedLibraryVersion);

    for (ExperimentSyncStatus status : statusMap.values()) {
      proto.addExperimentStatus(status.generateProto());
    }

    return proto.build();
  }
}

class ExperimentSyncStatus {
  private String experimentId;
  private boolean dirty = true;
  private long lastSyncedVersion = -1L;
  private boolean serverArchived = false;
  private boolean downloaded = false;

  public ExperimentSyncStatus(String id) {
    experimentId = id;
  }

  public ExperimentSyncStatus(ExperimentStatus status) {
    experimentId = status.getExperimentId();
    dirty = status.getDirty();
    lastSyncedVersion = status.getLastSyncedVersion();
    serverArchived = status.getServerArchived();
    downloaded = status.getDownloaded();
  }

  public String getExperimentId() {
    return experimentId;
  }

  public void setExperimentId(String experimentId) {
    this.experimentId = experimentId;
  }

  public boolean isDirty() {
    return dirty;
  }

  public void setDirty(boolean dirty) {
    this.dirty = dirty;
  }

  public boolean isServerArchived() {
    return serverArchived;
  }

  public void setServerArchived(boolean serverArchived) {
    this.serverArchived = serverArchived;
  }

  public boolean isDownloaded() {
    return downloaded;
  }

  public void setDownloaded(boolean downloaded) {
    this.downloaded = downloaded;
  }

  public long getLastSyncedVersion() {
    return lastSyncedVersion;
  }

  public void setLastSyncedVersion(long lastSyncedVersion) {
    this.lastSyncedVersion = lastSyncedVersion;
  }

  public ExperimentStatus generateProto() {
    return ExperimentStatus.newBuilder()
        .setExperimentId(experimentId)
        .setDirty(dirty)
        .setLastSyncedVersion(lastSyncedVersion)
        .setServerArchived(serverArchived)
        .setDownloaded(downloaded)
        .build();
  }
}
