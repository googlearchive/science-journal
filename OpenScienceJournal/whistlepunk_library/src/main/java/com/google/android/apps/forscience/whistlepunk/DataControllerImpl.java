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
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentOverviewPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileSyncCollection;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData.ScalarSensorDataDump;
import com.google.android.apps.forscience.whistlepunk.metadata.MetaDataManager;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensorDumpReader;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.SensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Range;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public class DataControllerImpl implements DataController, RecordingDataController {
  private static final String TAG = "DataControllerImpl";
  private final Context context;
  private final AppAccount appAccount;
  private final SensorDatabase sensorDatabase;
  private final Executor uiThread;
  private final Executor metaDataThread;
  private final Executor sensorDataThread;
  private MetaDataManager metaDataManager;
  private Clock clock;
  private Map<String, FailureListener> sensorFailureListeners = new HashMap<>();
  private final Map<String, SensorProvider> providerMap;
  private long prevLabelTimestamp = 0;
  private Map<String, WeakReference<Experiment>> cachedExperiments = new HashMap<>();
  private ConnectableSensor.Connector connector;

  public DataControllerImpl(
      Context context,
      AppAccount appAccount,
      SensorDatabase sensorDatabase,
      Executor uiThread,
      Executor metaDataThread,
      Executor sensorDataThread,
      MetaDataManager metaDataManager,
      Clock clock,
      Map<String, SensorProvider> providerMap,
      ConnectableSensor.Connector connector) {
    this.context = context;
    this.appAccount = appAccount;
    this.sensorDatabase = sensorDatabase;
    this.uiThread = uiThread;
    this.metaDataThread = metaDataThread;
    this.sensorDataThread = sensorDataThread;
    this.metaDataManager = metaDataManager;
    this.clock = clock;
    this.providerMap = providerMap;
    this.connector = connector;
  }

  public void replaceSensorInExperiment(
      final String experimentId,
      final String oldSensorId,
      final String newSensorId,
      final MaybeConsumer<Success> onSuccess) {
    getExperimentById(
        experimentId,
        MaybeConsumers.chainFailure(
            onSuccess,
            new Consumer<Experiment>() {
              @Override
              public void take(final Experiment experiment) {
                replaceIdInLayouts(experiment, oldSensorId, newSensorId);
                background(
                    metaDataThread,
                    onSuccess,
                    new Callable<Success>() {
                      @Override
                      public Success call() throws Exception {
                        metaDataManager.eraseSensorFromExperiment(oldSensorId, experimentId);
                        metaDataManager.addSensorToExperiment(newSensorId, experimentId);
                        // No need to mark the experiment as dirty, as sensors do not sync.
                        // True would also be ok, but would just add an extra sync.
                        metaDataManager.updateExperiment(experiment, false);
                        return Success.SUCCESS;
                      }
                    });
              }
            }));
  }

  private void replaceIdInLayouts(Experiment experiment, String oldSensorId, String newSensorId) {
    for (SensorLayoutPojo layout : experiment.getSensorLayouts()) {
      if (layout.getSensorId().equals(oldSensorId)) {
        layout.setSensorId(newSensorId);
      }
    }
  }

  private void removeTrialSensorData(final Trial trial) {
    sensorDataThread.execute(
        () -> {
          long firstTimestamp = trial.getOriginalFirstTimestamp();
          long lastTimestamp = trial.getOriginalLastTimestamp();
          if (firstTimestamp > lastTimestamp) {
            // TODO: Need a way to clean up invalid old data properly. For now, just
            // continue to ignore it because we cannot be sure where to stop deleting.
            return;
          }
          TimeRange times = TimeRange.oldest(Range.closed(firstTimestamp, lastTimestamp));
          for (String tag : trial.getSensorIds()) {
            sensorDatabase.deleteScalarReadings(trial.getTrialId(), tag, times);
          }
        });
  }

  @Override
  public void addScalarReadings(List<BatchInsertScalarReading> readings) {
    sensorDataThread.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              sensorDatabase.addScalarReadings(readings);
            } catch (final Exception e) {
              uiThread.execute(
                  new Runnable() {
                    @Override
                    public void run() {
                      notifyFailureListener("batchImport", e);
                    }
                  });
            }
          }
        });
  }

  @Override
  public void addScalarReading(
      final String trialId,
      final String sensorId,
      final int resolutionTier,
      final long timestampMillis,
      final double value) {
    sensorDataThread.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              sensorDatabase.addScalarReading(
                  trialId, sensorId, resolutionTier, timestampMillis, value);
            } catch (final Exception e) {
              uiThread.execute(
                  new Runnable() {
                    @Override
                    public void run() {
                      notifyFailureListener(sensorId, e);
                    }
                  });
            }
          }
        });
  }

  private void notifyFailureListener(String sensorId, Exception e) {
    FailureListener listener = sensorFailureListeners.get(sensorId);
    if (listener != null) {
      listener.fail(e);
    }
  }

  @Override
  public void getScalarReadings(
      final String trialId,
      final String databaseTag,
      final int resolutionTier,
      final TimeRange timeRange,
      final int maxRecords,
      final MaybeConsumer<ScalarReadingList> onSuccess) {
    Preconditions.checkNotNull(databaseTag);
    background(
        sensorDataThread,
        onSuccess,
        new Callable<ScalarReadingList>() {
          @Override
          public ScalarReadingList call() throws Exception {
            return sensorDatabase.getScalarReadings(
                trialId, databaseTag, timeRange, resolutionTier, maxRecords);
          }
        });
  }

  @Override
  public void getScalarReadingProtosInBackground(
      GoosciExperiment.Experiment experiment,
      final MaybeConsumer<GoosciScalarSensorData.ScalarSensorData> onSuccess) {
    Preconditions.checkNotNull(experiment);
    sensorDataThread.execute(
        () -> {
          onSuccess.success(sensorDatabase.getScalarReadingProtos(experiment));
        });
  }

  @Override
  public Observable<ScalarReading> createScalarObservable(
      final String trialId,
      final String[] sensorIds,
      final TimeRange timeRange,
      final int resolutionTier) {
    return sensorDatabase
        .createScalarObservable(trialId, sensorIds, timeRange, resolutionTier)
        .observeOn(Schedulers.from(sensorDataThread));
  }

  @Override
  public void deleteTrialData(final Trial trial, MaybeConsumer<Success> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<Success>() {
          @Override
          public Success call() throws Exception {
            removeTrialSensorData(trial);
            return Success.SUCCESS;
          }
        });
  }

  @Override
  public void createExperiment(final MaybeConsumer<Experiment> onSuccess) {
    MaybeConsumer<Experiment> onSuccessWrapper =
        MaybeConsumers.chainFailure(
            onSuccess,
            new Consumer<Experiment>() {
              @Override
              public void take(Experiment experiment) {
                cacheExperiment(experiment);
                onSuccess.success(experiment);
              }
            });
    background(
        metaDataThread,
        onSuccessWrapper,
        new Callable<Experiment>() {
          @Override
          public Experiment call() throws Exception {
            Experiment experiment = metaDataManager.newExperiment();
            return experiment;
          }
        });
  }

  @Override
  public void deleteExperiment(final String experimentId, final MaybeConsumer<Success> onSuccess) {
    if (cachedExperiments.containsKey(experimentId)) {
      cachedExperiments.remove(experimentId);
    }
    background(
        metaDataThread,
        onSuccess,
        new Callable<Success>() {

          @Override
          public Success call() throws Exception {
            deleteExperimentOnDataThread(experimentId);
            return Success.SUCCESS;
          }
        });
  }

  @Override
  public void deleteExperiment(
      final Experiment experiment, final MaybeConsumer<Success> onSuccess) {
    if (cachedExperiments.containsKey(experiment.getExperimentId())) {
      cachedExperiments.remove(experiment.getExperimentId());
    }
    background(
        metaDataThread,
        onSuccess,
        new Callable<Success>() {

          @Override
          public Success call() throws Exception {
            deleteExperimentOnDataThread(experiment);
            return Success.SUCCESS;
          }
        });
  }

  private void deleteExperimentOnDataThread(Experiment experiment) {
    // TODO: delete invalid run data, as well (b/35794788)
    metaDataManager.deleteExperiment(experiment);
  }

  private void deleteExperimentOnDataThread(String experimentId) {
    // TODO: delete invalid run data, as well (b/35794788)
    metaDataManager.deleteExperiment(experimentId);
  }

  @Override
  public void getExperimentById(
      final String experimentId, final MaybeConsumer<Experiment> onSuccess) {
    if (cachedExperiments.containsKey(experimentId)) {
      Experiment experiment = cachedExperiments.get(experimentId).get();
      if (experiment != null) {
        // We are already caching this one
        onSuccess.success(experiment);
        return;
      }
    }
    MaybeConsumer<Experiment> onSuccessWrapper =
        MaybeConsumers.chainFailure(
            onSuccess,
            new Consumer<Experiment>() {
              @Override
              public void take(Experiment experiment) {
                cachedExperiments.put(experimentId, new WeakReference<>(experiment));
                onSuccess.success(experiment);
              }
            });
    background(
        metaDataThread,
        onSuccessWrapper,
        new Callable<Experiment>() {
          @Override
          public Experiment call() throws Exception {
            Experiment result = metaDataManager.getExperimentById(experimentId);
            if (result == null) {
              throw new IllegalArgumentException(
                  "Could not find experiment with id " + experimentId);
            }
            return result;
          }
        });
  }

  @Override
  public void experimentExists(
      final String experimentId, final MaybeConsumer<Boolean> onSuccess) {
    if (cachedExperiments.containsKey(experimentId)) {
      Experiment experiment = cachedExperiments.get(experimentId).get();
      if (experiment != null) {
        // We are already caching this one
        onSuccess.success(true);
        return;
      }
    }
    background(
        metaDataThread,
        onSuccess,
        new Callable<Boolean>() {
          @Override
          public Boolean call() {
            Experiment result = metaDataManager.getExperimentById(experimentId);
            if (result == null) {
              return false;
            }
            return true;
          }
        });
  }

  @Override
  public void updateExperiment(
      final String experimentId,
      long lastUpdateTime,
      boolean setDirty,
      MaybeConsumer<Success> onSuccess) {
    if (!cachedExperiments.containsKey(experimentId)) {
      onSuccess.fail(new Exception("Experiment not loaded"));
      return;
    }
    final Experiment experiment = cachedExperiments.get(experimentId).get();
    if (experiment == null) {
      onSuccess.fail(new Exception("Experiment not loaded"));
      return;
    }

    updateExperiment(experiment, lastUpdateTime, setDirty, onSuccess);
  }

  @Override
  public void updateExperiment(final String experimentId, MaybeConsumer<Success> onSuccess) {
    updateExperiment(experimentId, clock.getNow(), true, onSuccess);
  }

  @Override
  public void updateExperiment(
      final String experimentId, boolean setDirty, MaybeConsumer<Success> onSuccess) {
    updateExperiment(experimentId, clock.getNow(), setDirty, onSuccess);
  }

  @Override
  public void updateExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {
    updateExperiment(experiment, clock.getNow(), false, onSuccess);
  }

  @Override
  public void updateExperiment(
      Experiment experiment,
      long lastUsedTime,
      boolean setDirty,
      MaybeConsumer<Success> onSuccess) {
    if (!cachedExperiments.containsKey(experiment.getExperimentId())) {
      throw new IllegalArgumentException(
          "Updating experiment not returned by DataController: " + experiment);
    }

    if (cachedExperiments.get(experiment.getExperimentId()).get() != experiment) {
      throw new IllegalArgumentException(
          "Updating different instance of experiment than is managed by DataController: "
              + experiment);
    }

    // Every time we update the experiment, we can update its last used time.
    experiment.setLastUsedTime(lastUsedTime);
    background(
        metaDataThread,
        onSuccess,
        () -> {
          metaDataManager.updateExperiment(experiment, setDirty);
          return Success.SUCCESS;
        });
  }

  @Override
  public void updateExperimentEvenIfNotActive(
      Experiment experiment,
      long lastUsedTime,
      boolean setDirty,
      MaybeConsumer<Success> onSuccess) {
    if (!cachedExperiments.containsKey(experiment.getExperimentId())) {
      Log.e(TAG, "Updating Non Active: " + experiment);
    }

    // Every time we update the experiment, we can update its last used time.
    experiment.setLastUsedTime(lastUsedTime);
    background(
        metaDataThread,
        onSuccess,
        () -> {
          metaDataManager.updateExperiment(experiment, setDirty);
          return Success.SUCCESS;
        });
  }

  @Override
  public void saveImmediately(MaybeConsumer<Success> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        () -> {
          metaDataManager.saveImmediately();
          return Success.SUCCESS;
        });
  }

  @Override
  public void addExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {
    if (cachedExperiments.containsKey(experiment.getExperimentId())) {
      throw new IllegalArgumentException(
          "Adding experiment already returned by DataController: " + experiment);
    }

    background(
        metaDataThread,
        onSuccess,
        () -> {
          metaDataManager.addExperiment(experiment);
          cacheExperiment(experiment);
          return Success.SUCCESS;
        });
  }

  @Override
  public void mergeExperiment(
      String experimentId,
      Experiment toMerge,
      boolean overwrite,
      MaybeConsumer<FileSyncCollection> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<FileSyncCollection>() {
          @Override
          public FileSyncCollection call() throws Exception {
            Experiment result = metaDataManager.getExperimentById(experimentId);
            if (result == null) {
              throw new IllegalArgumentException(
                  "Could not find experiment with id " + experimentId);
            }
            FileSyncCollection sync = result.mergeFrom(toMerge, context, appAccount, overwrite);
            if (Strings.isNullOrEmpty(result.getTitle())) {
              result.setTitle(toMerge.getTitle());
            }
            metaDataManager.updateExperiment(result, false);
            metaDataManager.saveImmediately();
            cachedExperiments.put(experimentId, new WeakReference<>(result));
            return sync;
          }
        });
  }

  @Override
  public String generateNewLabelId() {
    long nextLabelTimestamp = clock.getNow();
    if (nextLabelTimestamp <= prevLabelTimestamp) {
      // Make sure we never use the same label ID twice.
      nextLabelTimestamp = prevLabelTimestamp + 1;
    }
    prevLabelTimestamp = nextLabelTimestamp;
    return "label_" + nextLabelTimestamp;
  }

  @Override
  public void getExperimentOverviews(
      final boolean includeArchived, final MaybeConsumer<List<ExperimentOverviewPojo>> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<List<ExperimentOverviewPojo>>() {
          @Override
          public List<ExperimentOverviewPojo> call() throws Exception {
            return metaDataManager.getExperimentOverviews(includeArchived);
          }
        });
  }

  @Override
  public List<ExperimentOverviewPojo> blockingGetExperimentOverviews(boolean includeArchived) {
    return metaDataManager.getExperimentOverviews(includeArchived);
  }

  @Override
  public void getLastUsedUnarchivedExperiment(final MaybeConsumer<Experiment> onSuccess) {
    MaybeConsumer<Experiment> onSuccessWrapper =
        new MaybeConsumer<Experiment>() {
          @Override
          public void success(Experiment lastUsed) {
            if (lastUsed == null) {
              onSuccess.success(null);
              return;
            }
            if (cachedExperiments.containsKey(lastUsed.getExperimentId())) {
              // Use the same object if it's already in the cache.
              Experiment cached = cachedExperiments.get(lastUsed.getExperimentId()).get();
              if (cached != null) {
                onSuccess.success(cached);
                return;
              }
            }
            cacheExperiment(lastUsed);
            onSuccess.success(lastUsed);
          }

          @Override
          public void fail(Exception e) {
            onSuccess.fail(e);
          }
        };
    background(
        metaDataThread,
        onSuccessWrapper,
        new Callable<Experiment>() {
          @Override
          public Experiment call() throws Exception {
            return metaDataManager.getLastUsedUnarchivedExperiment();
          }
        });
  }

  @Override
  public void importExperimentFromZip(
      final Uri zipUri, ContentResolver resolver, final MaybeConsumer<String> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<String>() {
          @Override
          public String call() throws Exception {
            Experiment experiment = metaDataManager.importExperimentFromZip(zipUri, resolver);
            cacheExperiment(experiment);
            return experiment.getExperimentId();
          }
        });
  }

  private void cacheExperiment(Experiment experiment) {
    cachedExperiments.put(experiment.getExperimentId(), new WeakReference<>(experiment));
  }

  @Override
  public void getExternalSensors(final MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<Map<String, ExternalSensorSpec>>() {
          @Override
          public Map<String, ExternalSensorSpec> call() throws Exception {
            return metaDataManager.getExternalSensors(providerMap);
          }
        });
  }

  @Override
  public void getExternalSensorsByExperiment(
      final String experimentId, final MaybeConsumer<ExperimentSensors> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<ExperimentSensors>() {
          @Override
          public ExperimentSensors call() throws Exception {
            return metaDataManager.getExperimentSensors(experimentId, providerMap, connector);
          }
        });
  }

  @Override
  public void getExternalSensorById(
      final String id, final MaybeConsumer<ExternalSensorSpec> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<ExternalSensorSpec>() {
          @Override
          public ExternalSensorSpec call() throws Exception {
            return metaDataManager.getExternalSensorById(id, providerMap);
          }
        });
  }

  @Override
  public void addSensorToExperiment(
      final String experimentId, final String sensorId, final MaybeConsumer<Success> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<Success>() {
          @Override
          public Success call() throws Exception {
            metaDataManager.addSensorToExperiment(sensorId, experimentId);
            return Success.SUCCESS;
          }
        });
  }

  @Override
  public void removeSensorFromExperiment(
      final String experimentId, final String sensorId, final MaybeConsumer<Success> onSuccess) {
    getExperimentById(
        experimentId,
        MaybeConsumers.chainFailure(
            onSuccess,
            new Consumer<Experiment>() {
              @Override
              public void take(final Experiment experiment) {
                replaceIdInLayouts(experiment, sensorId, "");
                background(
                    metaDataThread,
                    onSuccess,
                    new Callable<Success>() {
                      @Override
                      public Success call() throws Exception {
                        metaDataManager.removeSensorFromExperiment(sensorId, experimentId);
                        metaDataManager.updateExperiment(experiment, false);
                        return Success.SUCCESS;
                      }
                    });
              }
            }));
  }

  @Override
  public void eraseSensorFromExperiment(
      final String experimentId, final String sensorId, final MaybeConsumer<Success> onSuccess) {
    getExperimentById(
        experimentId,
        MaybeConsumers.chainFailure(
            onSuccess,
            new Consumer<Experiment>() {
              @Override
              public void take(final Experiment experiment) {
                replaceIdInLayouts(experiment, sensorId, "");
                background(
                    metaDataThread,
                    onSuccess,
                    new Callable<Success>() {
                      @Override
                      public Success call() throws Exception {
                        metaDataManager.eraseSensorFromExperiment(sensorId, experimentId);
                        metaDataManager.updateExperiment(experiment, false);
                        return Success.SUCCESS;
                      }
                    });
              }
            }));
  }

  @Override
  public void setDataErrorListenerForSensor(String sensorId, FailureListener listener) {
    sensorFailureListeners.put(sensorId, listener);
  }

  @Override
  public void clearDataErrorListenerForSensor(String sensorId) {
    sensorFailureListeners.remove(sensorId);
  }

  @Override
  public void addOrGetExternalSensor(
      final ExternalSensorSpec sensor, final MaybeConsumer<String> onSensorId) {
    background(
        metaDataThread,
        onSensorId,
        new Callable<String>() {
          @Override
          public String call() throws Exception {
            return metaDataManager.addOrGetExternalSensor(sensor, providerMap);
          }
        });
  }

  private <T> void background(
      Executor dataThread, final MaybeConsumer<T> onSuccess, final Callable<T> job) {
    RuntimeException runtimeExceptionWithOriginalStackTrace =
        new RuntimeException(
            "This is the stack trace for thread "
                + Thread.currentThread()
                + ", which called DataControllerImpl.background().");
    dataThread.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              final T result = job.call();
              uiThread.execute(
                  new Runnable() {
                    @Override
                    public void run() {
                      onSuccess.success(result);
                    }
                  });
            } catch (final Exception e) {
              Log.e(
                  TAG,
                  "Caught exception (" + e + ") while executing background job.",
                  runtimeExceptionWithOriginalStackTrace);
              uiThread.execute(
                  new Runnable() {
                    @Override
                    public void run() {
                      onSuccess.fail(e);
                    }
                  });
            }
          }
        });
  }

  @Override
  public void getMyDevices(MaybeConsumer<List<InputDeviceSpec>> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<List<InputDeviceSpec>>() {
          @Override
          public List<InputDeviceSpec> call() throws Exception {
            return metaDataManager.getMyDevices();
          }
        });
  }

  @Override
  public void addMyDevice(final InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<Success>() {
          @Override
          public Success call() throws Exception {
            metaDataManager.addMyDevice(spec);
            return Success.SUCCESS;
          }
        });
  }

  @Override
  public void forgetMyDevice(final InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<Success>() {
          @Override
          public Success call() throws Exception {
            metaDataManager.removeMyDevice(spec);
            return Success.SUCCESS;
          }
        });
  }

  @Override
  public AppAccount getAppAccount() {
    return appAccount;
  }

  @Override
  public void moveAllExperimentsToAnotherAccount(
      AppAccount targetAccount, final MaybeConsumer<Success> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<Success>() {
          @Override
          public Success call() throws Exception {
            moveAllExperimentsToAnotherAccountOnDataThread(targetAccount);
            cachedExperiments.clear();
            return Success.SUCCESS;
          }
        });
  }

  private void moveAllExperimentsToAnotherAccountOnDataThread(AppAccount targetAccount)
      throws IOException {
    metaDataManager.saveImmediately();
    // Move each experiment, one at a time.
    List<ExperimentOverviewPojo> experiments =
        blockingGetExperimentOverviews(true /* includeArchived */);
    for (ExperimentOverviewPojo overview : experiments) {
      Experiment experiment = getExperimentFromId(overview.getExperimentId());
      moveExperimentToAnotherAccountOnDataThread(experiment, targetAccount);
    }
  }

  @Override
  public void deleteAllExperiments(final MaybeConsumer<Success> onSuccess) {
    background(
        metaDataThread,
        onSuccess,
        new Callable<Success>() {
          @Override
          public Success call() throws Exception {
            deleteAllExperimentsOnDataThread();
            cachedExperiments.clear();
            return Success.SUCCESS;
          }
        });
  }

  private void deleteAllExperimentsOnDataThread() {
    List<ExperimentOverviewPojo> experiments =
        blockingGetExperimentOverviews(true /* includeArchived */);
    for (ExperimentOverviewPojo overview : experiments) {
      Experiment experiment = getExperimentFromId(overview.getExperimentId());
      deleteExperimentOnDataThread(experiment);
    }
  }

  private Experiment getExperimentFromId(String experimentId) {
    Experiment experiment = null;
    if (cachedExperiments.containsKey(experimentId)) {
      experiment = cachedExperiments.get(experimentId).get();
    }
    if (experiment == null) {
      // Even if the experiment id is in the cache, the experiment might still be null
      // if it has been garbage collected.
      experiment = metaDataManager.getExperimentById(experimentId);
    }
    return experiment;
  }

  @Override
  public void moveExperimentToAnotherAccount(
      String experimentId, AppAccount targetAccount, MaybeConsumer<Success> onSuccess) {
    if (cachedExperiments.containsKey(experimentId)) {
      cachedExperiments.remove(experimentId);
    }
    getExperimentById(
        experimentId,
        MaybeConsumers.chainFailure(
            onSuccess,
            new Consumer<Experiment>() {
              @Override
              public void take(final Experiment experiment) {
                background(
                    metaDataThread,
                    onSuccess,
                    new Callable<Success>() {
                      @Override
                      public Success call() throws Exception {
                        moveExperimentToAnotherAccountOnDataThread(experiment, targetAccount);
                        return Success.SUCCESS;
                      }
                    });
              }
            }));
  }

  private void moveExperimentToAnotherAccountOnDataThread(
      Experiment experiment, AppAccount targetAccount) throws IOException {
    DataControllerImpl targetDataController =
        (DataControllerImpl) AppSingleton.getInstance(context).getDataController(targetAccount);

    metaDataManager.saveImmediately();

    metaDataManager.beforeMovingExperimentToAnotherAccount(experiment);

    //TODO(b/129534983): Write test that covers an Exception when claiming experiments
    try {
      // Move files.
      metaDataManager.moveExperimentToAnotherAccount(experiment, targetAccount);

      // Move scalar sensor data.
      List<ScalarSensorDataDump> scalarSensorData =
          sensorDatabase.getScalarReadingProtosAsList(experiment.getExperimentProto());
      ScalarSensorDumpReader scalarSensorDumpReader =
          new ScalarSensorDumpReader(targetDataController);
      scalarSensorDumpReader.readData(scalarSensorData);
      for (Trial trial : experiment.getTrials()) {
        removeTrialSensorData(trial);
      }

      targetDataController.metaDataManager.afterMovingExperimentFromAnotherAccount(experiment);
    } catch (Exception e) {
      // Re-add it to the original MetaDataManager
      metaDataManager.afterMovingExperimentFromAnotherAccount(experiment);
      throw e;
    }
  }

  @Override
  public void writeTrialProtoToFile(
      String experimentId, String trialId, final MaybeConsumer<File> onSuccess) throws IOException {
    getExperimentById(
        experimentId,
        MaybeConsumers.chainFailure(
            onSuccess,
            new Consumer<Experiment>() {
              @Override
              public void take(final Experiment experiment) {
                background(
                    metaDataThread,
                    onSuccess,
                    new Callable<File>() {
                      @Override
                      public File call() throws Exception {
                        GoosciScalarSensorData.ScalarSensorData proto =
                            sensorDatabase.getScalarReadingProtosForTrial(
                                experiment.getExperimentProto(), trialId);
                        File sensorProtoFile =
                            new File(
                                FileMetadataUtil.getInstance()
                                    .getExperimentDirectory(
                                        appAccount, experiment.getExperimentId()),
                                FileMetadataUtil.getInstance().getTrialProtoFileName(trialId));
                        try (FileOutputStream sensorStream =
                            new FileOutputStream(sensorProtoFile)) {
                          proto.writeTo(sensorStream);
                          return sensorProtoFile;
                        } catch (IOException ioException) {
                          return null;
                        }
                      }
                    });
              }
            }));
  }
}
