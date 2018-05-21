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

import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciLocalSyncStatus;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Manages the Sync Status for Science Journal All changes should be made using the getters and
 * setters provided, rather than by getting the underlying protocol buffer and making changes to
 * that directly. Changes to the underlying proto outside this class may be overwritten and may not
 * be saved.
 */
public class LocalSyncManager {
  private GoosciLocalSyncStatus.LocalSyncStatus proto;

  /** Constructor for an LocalSyncManager that creates a new LocalSyncStatus proto. */
  public LocalSyncManager() {
    proto = new GoosciLocalSyncStatus.LocalSyncStatus();
  }

  /** Constructor for an LocalSyncManager using an existing LocalSyncStatus. Useful for testing. */
  public LocalSyncManager(GoosciLocalSyncStatus.LocalSyncStatus localSyncStatus) {
    proto = localSyncStatus;
  }

  /**
   * Sets the LocalSyncStatus being managed.
   *
   * @param localSyncStatus The KicalSyncStatus to manage.
   */
  public void setLocalSyncStatus(GoosciLocalSyncStatus.LocalSyncStatus localSyncStatus) {
    proto = localSyncStatus;
  }

  /**
   * Checks if an experiment is known.
   *
   * @param experimentId The id of the experiment to manage.
   */
  public boolean hasExperiment(String experimentId) {
    return getExperimentStatus(experimentId) != null;
  }

  /**
   * Adds an Experiment to be managed.
   *
   * @param experimentId The id of the experiment to manage.
   */
  public void addExperiment(String experimentId) {
    ArrayList<GoosciLocalSyncStatus.ExperimentStatus> list =
        new ArrayList<>(Arrays.asList(proto.experimentStatus));
    GoosciLocalSyncStatus.ExperimentStatus status = new GoosciLocalSyncStatus.ExperimentStatus();
    status.experimentId = experimentId;
    list.add(status);
    proto.experimentStatus = list.toArray(new GoosciLocalSyncStatus.ExperimentStatus[0]);
  }

  /**
   * Gets the Experiment status for a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return The ExperimentStatus of the experiment, or null if not found.
   */
  private GoosciLocalSyncStatus.ExperimentStatus getExperimentStatus(String experimentId) {
    if (experimentId.isEmpty()) {
      return null;
    }

    for (GoosciLocalSyncStatus.ExperimentStatus experimentStatus : proto.experimentStatus) {
      if (experimentId.equals(experimentStatus.experimentId)) {
        return experimentStatus;
      }
    }

    return null;
  }

  /**
   * Sets the dirty bit of a given experiment id. Experiments where dirty is true have local changes
   * that must be synced to Drive.
   *
   * @param experimentId The id of the experiment to set status for.
   */
  public void setDirty(String experimentId, boolean dirty) {
    GoosciLocalSyncStatus.ExperimentStatus status = getExperimentStatus(experimentId);
    status.dirty = dirty;
  }

  /**
   * Gets the dirty bit of a given experiment id. Experiments where dirty is true have local changes
   * that must be synced to Drive.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return Whether or not the experiment has local changes.
   */
  public boolean getDirty(String experimentId) {
    GoosciLocalSyncStatus.ExperimentStatus status = getExperimentStatus(experimentId);
    return status.dirty;
  }

  /**
   * Sets the last synced version of a given experiment id.
   *
   * @param experimentId The id of the experiment to set status for.
   * @param version The last version of the experiment synced to or from Drive.
   */
  public void setLastSyncedVersion(String experimentId, long version) {
    GoosciLocalSyncStatus.ExperimentStatus status = getExperimentStatus(experimentId);
    status.lastSyncedVersion = version;
  }

  /**
   * Gets the last synced version of a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return The last version of the experiment synced to or from Drive.
   */
  public long getLastSyncedVersion(String experimentId) {
    GoosciLocalSyncStatus.ExperimentStatus status = getExperimentStatus(experimentId);
    return status.lastSyncedVersion;
  }

  /**
   * Sets the last archived status that we synced from the server of a given experiment id.
   *
   * @param experimentId The id of the experiment to set status for.
   * @param archived Whether the server says the experiment is archived.
   */
  public void setServerArchived(String experimentId, boolean archived) {
    GoosciLocalSyncStatus.ExperimentStatus status = getExperimentStatus(experimentId);
    status.serverArchived = archived;
  }

  /**
   * Gets the last archived status that we synced from the server of a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return Whether the server says the experiment is archived.
   */
  public boolean getServerArchived(String experimentId) {
    GoosciLocalSyncStatus.ExperimentStatus status = getExperimentStatus(experimentId);
    return status.serverArchived;
  }

  /**
   * Sets whether the experiment has been downloaded to the device for a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @param downloaded Whether the experiment is downloaded.
   */
  public void setDownloaded(String experimentId, boolean downloaded) {
    GoosciLocalSyncStatus.ExperimentStatus status = getExperimentStatus(experimentId);
    status.downloaded = downloaded;
  }

  /**
   * Gets whether the experiment has been downloaded to the device for a given experiment id.
   *
   * @param experimentId The id of the experiment to get status for.
   * @return Whether the experiment is downloaded.
   */
  public boolean getDownloaded(String experimentId) {
    GoosciLocalSyncStatus.ExperimentStatus status = getExperimentStatus(experimentId);
    return status.downloaded;
  }
}
