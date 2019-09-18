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

import android.content.ContentResolver;
import android.net.Uri;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentOverviewPojo;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Loads and saves meta data. */
public interface MetaDataManager {

  Experiment getExperimentById(String experimentId);

  /** Creates a new experiment. */
  Experiment newExperiment();

  /** Adds the specified experiment. */
  void addExperiment(Experiment experiment);

  /** Deletes the experiment and any associated trials and labels and their assets. */
  void deleteExperiment(Experiment experiment);

  void deleteExperiment(String experimentId);

  /** Updates experiment details, including the experiment's labels. */
  void updateExperiment(Experiment experiment, boolean setDirty);

  /** Add an existing experiment. */
  Experiment importExperimentFromZip(Uri zipUri, ContentResolver resolver) throws IOException;

  /** @return the list of all experiments. */
  List<ExperimentOverviewPojo> getExperimentOverviews(boolean includeArchived);

  /** Gets all the external sensors previously saved. */
  Map<String, ExternalSensorSpec> getExternalSensors(Map<String, SensorProvider> providerMap);

  /** Gets the external sensor or {@null} if no sensor with that ID was added. */
  ExternalSensorSpec getExternalSensorById(String id, Map<String, SensorProvider> providerMap);

  /**
   * @param sensor
   * @param providerMap
   * @return
   */
  String addOrGetExternalSensor(ExternalSensorSpec sensor, Map<String, SensorProvider> providerMap);

  /**
   * Removes an external sensor from the database.
   *
   * <p>Note that this will also delete it from any experiments linked to that sensor which could
   * affect display of saved experiments.
   */
  void removeExternalSensor(String databaseTag);

  /** Adds a linkage between an experiment and a sensor, which could be external or internal. */
  void addSensorToExperiment(String databaseTag, String experimentId);

  /** Removes a linkage between an experiment and a sensor, which could be external or internal. */
  void removeSensorFromExperiment(String databaseTag, String experimentId);

  /** Completely erase references to a sensor from an experiment. */
  void eraseSensorFromExperiment(String databaseTag, String experimentId);

  /** Gets all the external sensors which are linked to an experiment, in insertion order */
  ExperimentSensors getExperimentSensors(
      String experimentId,
      Map<String, SensorProvider> providerMap,
      ConnectableSensor.Connector connector);

  /**
   * Adds this device as one to be remembered as "my device" in the manage devices screen from here
   * on out.
   */
  void addMyDevice(InputDeviceSpec deviceSpec);

  /** Removes this device from "my devices". */
  void removeMyDevice(InputDeviceSpec deviceSpec);

  /** @return all of "my devices", in insertion order */
  List<InputDeviceSpec> getMyDevices();

  /** @returns Last used experiment. */
  Experiment getLastUsedUnarchivedExperiment();

  /**
   * Updates which experiment was last used. Does not save other parts of this experiment.
   *
   * <p>This is deprecated -- any changes to the experiment should be done using updateExperiment on
   * the main thread, and not here on the data thread.
   */
  @Deprecated
  void setLastUsedExperiment(Experiment experiment);

  void close();

  /** Any unsaved or cached data should be saved immediately. */
  void saveImmediately();

  /**
   * Returns true if moveAllExperimentsToAnotherAccount can be used to move all experiments to the
   * target account. If this method removes false, experiments should be removed one at a time.
   */
  boolean canMoveAllExperimentsToAnotherAccount(AppAccount targetAccount);

  /**
   * Moves experiment root directory, user_metadata.proto, and experiment and sensor databases, from
   * this account to the targetAccount.
   *
   * @throws IllegalStateException if {@link #canMoveAllExperimentsToAnotherAccount} is false.
   */
  void moveAllExperimentsToAnotherAccount(AppAccount targetAccount) throws IOException;

  void beforeMovingExperimentToAnotherAccount(Experiment experiment);

  void moveExperimentToAnotherAccount(Experiment experiment, AppAccount targetAccount)
      throws IOException;

  void afterMovingExperimentFromAnotherAccount(Experiment experiment);
}
