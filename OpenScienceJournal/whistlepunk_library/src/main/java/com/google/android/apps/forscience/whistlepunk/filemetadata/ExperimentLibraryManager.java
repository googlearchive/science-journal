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

import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciExperimentLibrary;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Manages a Science Journal experiment library. All changes should be made using the getters and
 * setters provided, rather than by getting the underlying protocol buffer and making changes to
 * that directly. Changes to the underlying proto outside this class may be overwritten and may not
 * be saved.
 */
public class ExperimentLibraryManager {
  private GoosciExperimentLibrary.ExperimentLibrary proto;

  /** Constructor for an ExperimentLibraryManager that creates a new ExperimentLibrary. */
  public ExperimentLibraryManager() {
    proto = new GoosciExperimentLibrary.ExperimentLibrary();
  }

  /**
   * Constructor for an ExperimentLibraryManager using an existing ExperimentLibrary. Useful for
   * testing.
   */
  public ExperimentLibraryManager(GoosciExperimentLibrary.ExperimentLibrary library) {
    proto = library;
  }

  /**
   * Sets the ExperimentLibrary being managed.
   *
   * @param library The library to manage.
   */
  public void setLibrary(GoosciExperimentLibrary.ExperimentLibrary library) {
    proto = library;
  }

  /**
   * Gets the SyncExperiment proto from the ExperimentLibrary that matches the experiment id
   *
   * @param experimentId The experiment to find.
   * @return The SyncExperiment if found, or null.
   */
  private GoosciExperimentLibrary.SyncExperiment getExperiment(String experimentId) {
    for (GoosciExperimentLibrary.SyncExperiment experiment : proto.syncExperiment) {
      if (experimentId.equals(experiment.experimentId)) {
        return experiment;
      }
    }

    return null;
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

    ArrayList<GoosciExperimentLibrary.SyncExperiment> experiments =
        new ArrayList<>(Arrays.asList(proto.syncExperiment));
    GoosciExperimentLibrary.SyncExperiment newExperiment =
        new GoosciExperimentLibrary.SyncExperiment();
    newExperiment.experimentId = experimentId;
    experiments.add(newExperiment);

    proto.syncExperiment =
        experiments.toArray(new GoosciExperimentLibrary.SyncExperiment[experiments.size()]);
  }

  /**
   * Sets the specified experiment's archived state on the local device.
   *
   * @param experimentId The experiment to update.
   * @param archived Whether or not the experiment is locally archived.
   */
  public void setArchived(String experimentId, boolean archived) {
    GoosciExperimentLibrary.SyncExperiment experiment = getExperiment(experimentId);
    // Remove these null checks and be sure to add every experiment to the Manager.
    // TODO(b/79205044)
    if (experiment != null) {
      getExperiment(experimentId).archived = archived;
    }
  }

  /**
   * Sets the specified experiment's deleted state on the local device.
   *
   * @param experimentId The experiment to update.
   * @param deleted Whether or not the experiment is locally deleted.
   */
  public void setDeleted(String experimentId, boolean deleted) {
    GoosciExperimentLibrary.SyncExperiment experiment = getExperiment(experimentId);
    // Remove these null checks and be sure to add every experiment to the Manager.
    // TODO(b/79205044)
    if (experiment != null) {
      experiment.deleted = deleted;
    }
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
    GoosciExperimentLibrary.SyncExperiment experiment = getExperiment(experimentId);
    // Remove these null checks and be sure to add every experiment to the Manager.
    // TODO(b/79205044)
    if (experiment != null) {
      experiment.lastOpened = timeInMillis;
    }
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
    GoosciExperimentLibrary.SyncExperiment experiment = getExperiment(experimentId);
    // Remove these null checks and be sure to add every experiment to the Manager.
    // TODO(b/79205044)
    if (experiment != null) {
      experiment.lastModified = timeInMillis;
    }
  }
}
