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
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.SyncExperiment;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manages a Science Journal experiment library.
 */
public class ExperimentLibraryManager {
  private static final String TAG = "experimentLibrary";
  private String folderId;
  private final Map<String, LibrarySyncExperiment> experiments = new HashMap<>();
  private final AppAccount account;

  /** Constructor for an ExperimentLibraryManager that creates a new ExperimentLibrary. */
  public ExperimentLibraryManager(AppAccount account) {
    this(null, account);
  }

  /**
   * Constructor for an ExperimentLibraryManager using an existing ExperimentLibrary. Useful for
   * testing.
   */
  @VisibleForTesting
  public ExperimentLibraryManager(ExperimentLibrary library, AppAccount account) {
    this.account = account;
    if (library != null) {
      setLibrary(library);
    }
  }

  /**
   * Sets the ExperimentLibrary being managed.
   *
   * @param library The library to manage.
   */
  public void setLibrary(ExperimentLibrary library) {
    experiments.clear();
    if (library == null) {
      this.folderId = null;
      return;
    }
    for (SyncExperiment experiment : library.getSyncExperimentList()) {
      LibrarySyncExperiment lse =
          new LibrarySyncExperiment(
              experiment.getExperimentId(),
              experiment.getFileId(),
              experiment.getLastOpened(),
              experiment.getLastModified(),
              experiment.getDeleted(),
              experiment.getArchived());
      experiments.put(experiment.getExperimentId(), lse);
    }
    this.folderId = library.getFolderId();
  }

  /**
   * Gets the LocalSyncExperiment from the ExperimentLibraryManager that matches the experiment id
   *
   * @param experimentId The experiment to find.
   * @return The SyncExperiment if found, or null.
   */
  LibrarySyncExperiment getExperiment(String experimentId) {
    populateExperimentLibraryManager();
    return experiments.get(experimentId);
  }

  /**
   * Adds a new SyncExperiment proto to the ExperimentLibrary that matches the experiment id, if one
   * does not already exist.
   *
   * @param experimentId The experiment to find.
   */
  public void addExperiment(String experimentId) {
    if (getExperiment(experimentId) != null) {
      return;
    }
    experiments.put(experimentId, new LibrarySyncExperiment(experimentId));
    writeExperimentLibrary();
  }

  /**
   * Adds an existing SyncExperiment proto to the ExperimentLibrary, if one does not already exist.
   *
   * @param experiment The SyncEcperiment to add.
   */
  void addExperiment(SyncExperiment experiment) {
    if (experiments.containsKey(experiment.getExperimentId())) {
      throw new IllegalArgumentException("Experiment already exists");
    }

    LibrarySyncExperiment lse =
        new LibrarySyncExperiment(
            experiment.getExperimentId(),
            experiment.getFileId(),
            experiment.getLastOpened(),
            experiment.getLastModified(),
            experiment.getDeleted(),
            experiment.getArchived());
    experiments.put(lse.getExperimentId(), lse);

    writeExperimentLibrary();
  }

  /**
   * Updates an existing SyncExperiment proto to the latest values between the library and another
   * version of the SyncExperiment.
   *
   * @param experiment The SyncExperiment to update
   * @param serverArchived The archive state seen when last synced to the server, from the
   *     LocalSyncManager.
   */
  private void updateExperiment(SyncExperiment experiment, boolean serverArchived) {
    LibrarySyncExperiment toMerge = experiments.get(experiment.getExperimentId());
    if (toMerge == null) {
      addExperiment(experiment);
    } else {
      if (experiment.getLastOpened() > toMerge.getLastOpened()) {
        toMerge.setLastOpened(experiment.getLastOpened());
      }
      if (experiment.getLastModified() > toMerge.getLastModified()) {
        toMerge.setLastModified(experiment.getLastModified());
      }
      if (experiment.getDeleted()) {
        toMerge.setDeleted(true);
      }

      // serverArchived is the state that we saw on the server during the last sync.
      // If we unarchived locally, and it was unarchived remotely since the last sync, both the
      // passed-in experiment and the local experiment will be false, and the serverArchived will
      // be true.
      // If only one of local and passed-in unarchived, serverArchived will be true, and one of the
      // experiments will be false, and we want to keep whichever one changed, and which is
      // is different from server archived.
      if (experiment.getArchived() != toMerge.isArchived()) {
        toMerge.setArchived(!serverArchived);
      }
    }
  }

  /**
   * Sets the specified experiment's archived state on the local device.
   *
   * @param experimentId The experiment to update.
   * @param archived Whether or not the experiment is locally archived.
   */
  public void setArchived(String experimentId, boolean archived) {
    LibrarySyncExperiment lse = experiments.get(experimentId);
    if (lse != null) {
      lse.setArchived(archived);
      writeExperimentLibrary();
    }
  }

  /**
   * Gets the specified experiment's archived state on the local device.
   *
   * @param experimentId The experiment to get state for.
   * @return Whether or not the experiment is locally archived.
   */
  public boolean isArchived(String experimentId) {
    return getExperiment(experimentId).isArchived();
  }

  public void setAllDeleted(boolean deleted) {
    populateExperimentLibraryManager();
    for (LibrarySyncExperiment experiment : experiments.values()) {
      experiment.setDeleted(deleted);
    }
    writeExperimentLibrary();
  }

  /**
   * Sets the specified experiment's deleted state on the local device.
   *
   * @param experimentId The experiment to update.
   * @param deleted Whether or not the experiment is locally deleted.
   */
  public void setDeleted(String experimentId, boolean deleted) {
    LibrarySyncExperiment lse = experiments.get(experimentId);
    if (lse != null) {
      lse.setDeleted(deleted);
      writeExperimentLibrary();
    }
  }

  /**
   * Gets the specified experiment's deleted state on the local device.
   *
   * @param experimentId The experiment to get state for.
   * @return Whether or not the experiment is locally deleted.
   */
  public boolean isDeleted(String experimentId) {
    return getExperiment(experimentId).isDeleted();
  }

  /**
   * Sets the specified experiment's last opened time to now.
   *
   * @param experimentId The experiment to update.
   */
  public void setOpened(String experimentId) {
    setOpened(experimentId, System.currentTimeMillis());
  }

  /**
   * Sets the specified experiment's last opened time to a given timestamp.
   *
   * @param experimentId The experiment to update.
   * @param timeInMillis The time the experiment was last opened.
   */
  public void setOpened(String experimentId, long timeInMillis) {
    LibrarySyncExperiment lse = experiments.get(experimentId);
    if (lse != null) {
      lse.setLastOpened(timeInMillis);
      writeExperimentLibrary();
    }
  }

  /**
   * Gets the specified experiment's last opened time.
   *
   * @param experimentId The experiment get times from.
   * @return the last opened time for the experiment, in millis.
   */
  public long getOpened(String experimentId) {
    return getExperiment(experimentId).getLastOpened();
  }

  /**
   * Sets the specified experiment's last modified time to now.
   *
   * @param experimentId The experiment to update.
   */
  public void setModified(String experimentId) {
    setModified(experimentId, System.currentTimeMillis());
  }

  /**
   * Sets the specified experiment's last modified time to a given timestamp.
   *
   * @param experimentId The experiment to update.
   * @param timeInMillis The time the experiment was last modified.
   */
  public void setModified(String experimentId, long timeInMillis) {
    LibrarySyncExperiment lse = experiments.get(experimentId);
    if (lse != null) {
      lse.setLastModified(timeInMillis);
      writeExperimentLibrary();
    }
  }

  /**
   * Gets the specified experiment's last modified time.
   *
   * @param experimentId The experiment get times from.
   * @return the last modified time for the experiment, in millis.
   */
  public long getModified(String experimentId) {
    return getExperiment(experimentId).getLastModified();
  }

  /**
   * Sets the specified experiment's file id.
   *
   * @param experimentId The experiment to update.
   * @param fileId The file id for the experiment.
   */
  public void setFileId(String experimentId, String fileId) {
    LibrarySyncExperiment lse = experiments.get(experimentId);
    if (lse != null) {
      lse.setFileId(fileId);
      writeExperimentLibrary();
    }
  }

  /**
   * Gets the specified experiment's file id.
   *
   * @param experimentId The experiment get the file id from.
   * @return the file id for the experiment.
   */
  public String getFileId(String experimentId) {
    return getExperiment(experimentId).getFileId();
  }

  /**
   * Merges newer changes from one library into this one.
   *
   * @param library The experiment to merge from.
   */
  public void merge(ExperimentLibrary library, LocalSyncManager syncManager) {
    populateExperimentLibraryManager();
    if (!Strings.isNullOrEmpty(library.getFolderId())) {
      folderId = library.getFolderId();
    }
    for (SyncExperiment experiment : library.getSyncExperimentList()) {
      boolean serverArchived = false;
      if (syncManager.hasExperiment(experiment.getExperimentId())) {
        serverArchived = syncManager.getServerArchived(experiment.getExperimentId());
      } else {
        syncManager.addExperiment(experiment.getExperimentId());
        syncManager.setDirty(experiment.getExperimentId(), true);
      }
      updateExperiment(experiment, serverArchived);
    }
    writeExperimentLibrary();
  }

  public Set<String> getKnownExperiments() {
    populateExperimentLibraryManager();

    // Returning a local copy of this set will defend against concurrent modification.
    return new HashSet<>(experiments.keySet());
  }

  private void writeExperimentLibrary() {
    Executor thread = Executors.newSingleThreadExecutor();
    thread.execute(
        new Runnable() {
          @Override
          public void run() {
            synchronized (account.getLockForExperimentLibraryFile()) {
              try {
                FileMetadataUtil.getInstance().writeExperimentLibraryFile(generateProto(), account);
              } catch (IOException ioe) {
                // Would like to do something else here, but not sure what else there really is to
                // do.
                if (Log.isLoggable(TAG, Log.ERROR)) {
                  Log.e(TAG, "ExperimentLibrary Write failed", ioe);
                }
              }
            }
          }
        });
  }

  private ExperimentLibrary generateProto() {
    ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    if (folderId != null) {
      library.setFolderId(folderId);
    }
    List<LibrarySyncExperiment> valuesList = new ArrayList<>(experiments.values());
    for (LibrarySyncExperiment experiment : valuesList) {
      library.addSyncExperiment(experiment.generateProto());
    }
    return library.build();
  }

  // Reads the saved experiment library manager file from disk, if the Library has not already
  // been set to a non-null value. This lets us move initialization of this object to the background
  // TODO(b/111649596) Test this
  private void populateExperimentLibraryManager() {
    if (experiments.isEmpty()) {
      setLibrary(FileMetadataUtil.getInstance().readExperimentLibraryFile(account));
    }
  }

  public void setFolderId(String folderId) {
    populateExperimentLibraryManager();
    this.folderId = folderId;
    writeExperimentLibrary();
  }

  public String getFolderId() {
    populateExperimentLibraryManager();
    return folderId;
  }
}
