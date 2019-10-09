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
 * A DataController with empty implementations of all the methods, for tests to extend and override
 * only what they need.
 */
public class StubDataController implements DataController {
  @Override
  public void getScalarReadings(
      String trialId,
      String databaseTag,
      int resolutionTier,
      TimeRange timeRange,
      int maxRecords,
      MaybeConsumer<ScalarReadingList> onSuccess) {}

  @Override
  public void getScalarReadingProtosInBackground(
      GoosciExperiment.Experiment experiment,
      MaybeConsumer<GoosciScalarSensorData.ScalarSensorData> onSuccess) {}

  @Override
  public Observable<ScalarReading> createScalarObservable(
      String trialId, String[] sensorIds, TimeRange timeRange, int resolutionTier) {
    return null;
  }

  @Override
  public void deleteTrialData(Trial trial, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void createExperiment(MaybeConsumer<Experiment> onSuccess) {}

  @Override
  public void deleteExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public void deleteExperiment(String experimentId, MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public void getExperimentById(String experimentId, MaybeConsumer<Experiment> onSuccess) {
    Experiment e = Experiment.newExperiment(0, experimentId, 0);
    e.setTitle("Experiment Title");
    onSuccess.success(e);
  }

  @Override
  public void experimentExists(String experimentId, MaybeConsumer<Boolean> onSuccess) {
    onSuccess.success(true);
  }

  @Override
  public void updateExperiment(
      String experimentId,
      long lastUpdateTime,
      boolean setDirty,
      MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public void updateExperiment(String experimentId, MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public void updateExperiment(
      String experimentId, boolean setDirty, MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public void updateExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public void updateExperiment(
      Experiment experiment,
      long lastUsedTime,
      boolean setDirty,
      MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public void mergeExperiment(
      String experimentId,
      Experiment toMerge,
      boolean overwrite,
      MaybeConsumer<FileSyncCollection> onSuccess) {
    onSuccess.success(new FileSyncCollection());
  }

  @Override
  public void updateExperimentEvenIfNotActive(Experiment experiment, long lastUsedTime,
      boolean setDirty, MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public void importExperimentFromZip(
      Uri zipUri, ContentResolver resolver, MaybeConsumer<String> onSuccess) {}

  @Override
  public void saveImmediately(MaybeConsumer<Success> onSuccess) {}

  @Override
  public void addExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {
    onSuccess.success(Success.SUCCESS);
  }

  @Override
  public String generateNewLabelId() {
    return null;
  }

  @Override
  public void getExperimentOverviews(
      boolean includeArchived, MaybeConsumer<List<ExperimentOverviewPojo>> onSuccess) {}

  @Override
  public List<ExperimentOverviewPojo> blockingGetExperimentOverviews(boolean includeArchived) {
    return null;
  }

  @Override
  public void getLastUsedUnarchivedExperiment(MaybeConsumer<Experiment> onSuccess) {}

  @Override
  public void getExternalSensors(MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess) {}

  @Override
  public void getExternalSensorsByExperiment(
      String experimentId, MaybeConsumer<ExperimentSensors> onSuccess) {}

  @Override
  public void getExternalSensorById(String id, MaybeConsumer<ExternalSensorSpec> onSuccess) {}

  @Override
  public void addSensorToExperiment(
      String experimentId, String sensorId, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void removeSensorFromExperiment(
      String experimentId, String sensorId, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void eraseSensorFromExperiment(
      String experimentId, String sensorId, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void addOrGetExternalSensor(ExternalSensorSpec sensor, MaybeConsumer<String> onSensorId) {}

  @Override
  public void replaceSensorInExperiment(
      String experimentId,
      String oldSensorId,
      String newSensorId,
      MaybeConsumer<Success> onSuccess) {}

  @Override
  public void getMyDevices(MaybeConsumer<List<InputDeviceSpec>> onSuccess) {}

  @Override
  public void forgetMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void addMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {}

  @Override
  public AppAccount getAppAccount() {
    return null;
  }

  @Override
  public void moveAllExperimentsToAnotherAccount(
      AppAccount targetAccount, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void deleteAllExperiments(MaybeConsumer<Success> onSuccess) {}

  @Override
  public void moveExperimentToAnotherAccount(
      String experimentId, AppAccount targetAccount, MaybeConsumer<Success> onSuccess) {}

  public void writeTrialProtoToFile(
      String experimentId, String trialId, final MaybeConsumer<File> onSuccess)
      throws IOException {}
}
