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

package com.google.android.apps.forscience.whistlepunk;

import android.content.ContentResolver;
import android.net.Uri;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentOverviewPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileSyncCollection;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import io.reactivex.Observable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Provides access to any data outside of the UI. All methods should be called from the UI thread;
 * callers can expect that methods will return quickly, and the provided callback will be called
 * later on the UI thread.
 */
public interface DataController {
  void getScalarReadings(
      String trialId,
      String databaseTag,
      final int resolutionTier,
      TimeRange timeRange,
      int maxRecords,
      MaybeConsumer<ScalarReadingList> onSuccess);

  // TODO: refactor to remove the interface inconsistency here.

  /** Unlike all other DataController methods, this one calls onSuccess on the background thread. */
  void getScalarReadingProtosInBackground(
      GoosciExperiment.Experiment experiment,
      final MaybeConsumer<GoosciScalarSensorData.ScalarSensorData> onSuccess);

  Observable<ScalarReading> createScalarObservable(
      String trialId, String[] sensorIds, TimeRange timeRange, final int resolutionTier);

  void deleteTrialData(Trial trial, MaybeConsumer<Success> onSuccess);

  void createExperiment(MaybeConsumer<Experiment> onSuccess);

  void deleteExperiment(String experimentId, MaybeConsumer<Success> onSuccess);

  void deleteExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess);

  void getExperimentById(String experimentId, MaybeConsumer<Experiment> onSuccess);

  void experimentExists(
      String experimentId, MaybeConsumer<Boolean> onSuccess);

  void updateExperiment(
      String experimentId, long lastUpdateTime, boolean setDirty, MaybeConsumer<Success> onSuccess);

  void updateExperiment(String experimentId, MaybeConsumer<Success> onSuccess);

  void updateExperiment(String experimentId, boolean setDirty, MaybeConsumer<Success> onSuccess);

  void updateExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess);

  void updateExperiment(
      Experiment experiment, long lastUsedTime, boolean setDirty, MaybeConsumer<Success> onSuccess);

  void mergeExperiment(
      String experimentId,
      Experiment toMerge,
      boolean overwrite,
      MaybeConsumer<FileSyncCollection> onSuccess);

  /* Updates an experiment even if it is not the currently cached/active experiment. Only used for
   * syncing experiments. */
  void updateExperimentEvenIfNotActive(
      Experiment experiment, long lastUsedTime, boolean setDirty, MaybeConsumer<Success> onSuccess);

  void saveImmediately(MaybeConsumer<Success> onSuccess);

  void addExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess);

  String generateNewLabelId();

  void importExperimentFromZip(
      final Uri zipUri, ContentResolver resolver, final MaybeConsumer<String> onSuccess);

  /** Gets all experiment overviews. */
  void getExperimentOverviews(
      boolean includeArchived, MaybeConsumer<List<ExperimentOverviewPojo>> onSuccess);

  /** Gets all experiment overviews on the same thread as the caller. */
  List<ExperimentOverviewPojo> blockingGetExperimentOverviews(boolean includeArchived);

  /** Gets the most recently used, unarchived experiment. */
  void getLastUsedUnarchivedExperiment(MaybeConsumer<Experiment> onSuccess);

  void getExternalSensors(MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess);

  // TODO: fix docs, rename?

  /** Passes to onSuccess a map from sensor ids to external sensor specs */
  void getExternalSensorsByExperiment(
      final String experimentId, MaybeConsumer<ExperimentSensors> onSuccess);

  /** Passes to onSuccess a map from sensor ids to external sensor specs */
  void getExternalSensorById(String id, MaybeConsumer<ExternalSensorSpec> onSuccess);

  /**
   * Adds the external sensor to the experiment.
   *
   * @return {@code true} to the consumer if the sensor was added, {@code false} if the sensor was
   *     already present.
   */
  void addSensorToExperiment(
      final String experimentId, String sensorId, MaybeConsumer<Success> onSuccess);

  /**
   * Removes the external sensor from the experiment.
   *
   * @return {@code true} to the consumer if the sensor needed to be removed, {@code false} if the
   *     sensor was not already present.
   */
  void removeSensorFromExperiment(
      final String experimentId, final String sensorId, MaybeConsumer<Success> onSuccess);

  /** Completely removes a sensor reference from an experiment. */
  void eraseSensorFromExperiment(
      final String experimentId, final String sensorId, final MaybeConsumer<Success> onSuccess);

  /**
   * Makes sure there is an external sensor already registered in the database with the given spec,
   * and returns its id to {@code onSensorId}
   */
  void addOrGetExternalSensor(ExternalSensorSpec sensor, MaybeConsumer<String> onSensorId);

  // TODO: layout and external sensor storage is redundant

  /**
   * Replace oldSensorId with newSensorId in experiment. All cards that were showing oldSensorId
   * will now show newSensorId
   */
  void replaceSensorInExperiment(
      String experimentId,
      String oldSensorId,
      String newSensorId,
      final MaybeConsumer<Success> onSuccess);

  void getMyDevices(MaybeConsumer<List<InputDeviceSpec>> onSuccess);

  void forgetMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess);

  void addMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess);

  AppAccount getAppAccount();

  void moveAllExperimentsToAnotherAccount(
      AppAccount targetAccount, MaybeConsumer<Success> onSuccess);

  void deleteAllExperiments(MaybeConsumer<Success> onSuccess);

  void moveExperimentToAnotherAccount(
      String experimentId, AppAccount targetAccount, MaybeConsumer<Success> onSuccess);

  void writeTrialProtoToFile(
      String experimentId, String trialId, final MaybeConsumer<File> onSuccess) throws IOException;
}
