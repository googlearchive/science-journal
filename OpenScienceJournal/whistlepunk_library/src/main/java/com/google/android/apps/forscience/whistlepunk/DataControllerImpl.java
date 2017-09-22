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

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.MetaDataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.SensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

public class DataControllerImpl implements DataController, RecordingDataController {
    private static final String TAG = "DataControllerImpl";
    private final SensorDatabase mSensorDatabase;
    private final Executor mUiThread;
    private final Executor mMetaDataThread;
    private final Executor mSensorDataThread;
    private MetaDataManager mMetaDataManager;
    private Clock mClock;
    private Map<String, FailureListener> mSensorFailureListeners = new HashMap<>();
    private final Map<String, SensorProvider> mProviderMap;
    private long mPrevLabelTimestamp = 0;
    private Map<String, WeakReference<Experiment>> mCachedExperiments = new HashMap<>();
    private ConnectableSensor.Connector mConnector;

    public DataControllerImpl(SensorDatabase sensorDatabase, Executor uiThread,
            Executor metaDataThread, Executor sensorDataThread, MetaDataManager metaDataManager,
            Clock clock, Map<String, SensorProvider> providerMap,
            ConnectableSensor.Connector connector) {
        mSensorDatabase = sensorDatabase;
        mUiThread = uiThread;
        mMetaDataThread = metaDataThread;
        mSensorDataThread = sensorDataThread;
        mMetaDataManager = metaDataManager;
        mClock = clock;
        mProviderMap = providerMap;
        mConnector = connector;
    }

    public void replaceSensorInExperiment(final String experimentId, final String oldSensorId,
            final String newSensorId, final MaybeConsumer<Success> onSuccess) {
        getExperimentById(experimentId, MaybeConsumers.chainFailure(onSuccess,
                new Consumer<Experiment>() {
                    @Override
                    public void take(final Experiment experiment) {
                        replaceIdInLayouts(experiment, oldSensorId, newSensorId);
                        background(mMetaDataThread, onSuccess,
                                new Callable<Success>() {
                                    @Override
                                    public Success call() throws Exception {
                                        mMetaDataManager.removeSensorFromExperiment(oldSensorId,
                                                experimentId);
                                        mMetaDataManager.addSensorToExperiment(newSensorId,
                                                experimentId);
                                        mMetaDataManager.updateExperiment(experiment);
                                        return Success.SUCCESS;
                                    }
                                });
                    }
                }));
    }

    private void replaceIdInLayouts(Experiment experiment,
            String oldSensorId, String newSensorId) {
        for (GoosciSensorLayout.SensorLayout layout : experiment.getSensorLayouts()) {
            if (layout.sensorId.equals(oldSensorId)) {
                layout.sensorId = newSensorId;
            }
        }
    }

    private void removeTrialSensorData(final Trial trial) {
        mSensorDataThread.execute(() -> {
            long firstTimestamp = trial.getOriginalFirstTimestamp();
            long lastTimestamp = trial.getOriginalLastTimestamp();
            if (firstTimestamp > lastTimestamp) {
                // TODO: Need a way to clean up invalid old data properly. For now, just
                // continue to ignore it because we cannot be sure where to stop deleting.
                return;
            }
            TimeRange times = TimeRange.oldest(Range.closed(firstTimestamp,
                    lastTimestamp));
            for (String tag : trial.getSensorIds()) {
                mSensorDatabase.deleteScalarReadings(tag, times);
            }
        });
    }

    @Override
    public void addScalarReading(final String sensorId, final int resolutionTier,
            final long timestampMillis, final double value) {
        mSensorDataThread.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mSensorDatabase.addScalarReading(sensorId, resolutionTier, timestampMillis,
                            value);
                } catch (final Exception e) {
                    mUiThread.execute(new Runnable() {
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
        FailureListener listener = mSensorFailureListeners.get(sensorId);
        if (listener != null) {
            listener.fail(e);
        }
    }

    @Override
    public void getScalarReadings(final String databaseTag, final int resolutionTier,
            final TimeRange timeRange, final int maxRecords,
            final MaybeConsumer<ScalarReadingList> onSuccess) {
        Preconditions.checkNotNull(databaseTag);
        background(mSensorDataThread, onSuccess, new Callable<ScalarReadingList>() {
            @Override
            public ScalarReadingList call() throws Exception {
                return mSensorDatabase.getScalarReadings(databaseTag, timeRange, resolutionTier,
                        maxRecords);
            }
        });
    }

    @Override
    public Observable<ScalarReading> createScalarObservable(final String[] sensorIds,
            final TimeRange timeRange, final int resolutionTier) {
        return mSensorDatabase.createScalarObservable(sensorIds, timeRange, resolutionTier)
                .observeOn(Schedulers.from(mSensorDataThread));
    }

    @Override
    public void deleteTrialData(final Trial trial, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                removeTrialSensorData(trial);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void createExperiment(final MaybeConsumer<Experiment> onSuccess) {
        MaybeConsumer<Experiment> onSuccessWrapper = MaybeConsumers.chainFailure(onSuccess,
                new Consumer<Experiment>() {
                    @Override
                    public void take(Experiment experiment) {
                        mCachedExperiments.put(experiment.getExperimentId(),
                                new WeakReference<>(experiment));
                        onSuccess.success(experiment);
                    }
                });
        background(mMetaDataThread, onSuccessWrapper, new Callable<Experiment>() {
            @Override
            public Experiment call() throws Exception {
                Experiment experiment = mMetaDataManager.newExperiment();
                return experiment;
            }
        });
    }

    @Override
    public void deleteExperiment(final Experiment experiment,
                                 final MaybeConsumer<Success> onSuccess) {
        if (mCachedExperiments.containsKey(experiment.getExperimentId())) {
            mCachedExperiments.remove(experiment.getExperimentId());
        }
        background(mMetaDataThread, onSuccess, new Callable<Success>() {

            @Override
            public Success call() throws Exception {
                deleteExperimentOnDataThread(experiment);
                return Success.SUCCESS;
            }
        });
    }

    private void deleteExperimentOnDataThread(Experiment experiment) {
        // TODO: delete invalid run data, as well (b/35794788)
        mMetaDataManager.deleteExperiment(experiment);
    }

    @Override
    public void getExperimentById(final String experimentId,
            final MaybeConsumer<Experiment> onSuccess) {
        if (mCachedExperiments.containsKey(experimentId)) {
            Experiment experiment = mCachedExperiments.get(experimentId).get();
            if (experiment != null) {
                // We are already caching this one
                onSuccess.success(experiment);
                return;
            }
        }
        MaybeConsumer<Experiment> onSuccessWrapper = MaybeConsumers.chainFailure(onSuccess,
                new Consumer<Experiment>() {
                    @Override
                    public void take(Experiment experiment) {
                        mCachedExperiments.put(experimentId, new WeakReference<>(experiment));
                        onSuccess.success(experiment);
                    }
                });
        background(mMetaDataThread, onSuccessWrapper, new Callable<Experiment>() {
            @Override
            public Experiment call() throws Exception {
                Experiment result = mMetaDataManager.getExperimentById(experimentId);
                if (result == null) {
                    throw new IllegalArgumentException(
                            "Could not find experiment with id " + experimentId);
                }
                return result;
            }
        });
    }

    @Override
    public void updateExperiment(final String experimentId, MaybeConsumer<Success> onSuccess) {
        if (!mCachedExperiments.containsKey(experimentId)) {
            onSuccess.fail(new Exception("Experiment not loaded"));
            return;
        }
        final Experiment experiment = mCachedExperiments.get(experimentId).get();
        if (experiment == null) {
            onSuccess.fail(new Exception("Experiment not loaded"));
            return;
        }

        updateExperiment(experiment, onSuccess);
    }

    @Override
    public void saveImmediately(MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, () -> {
            mMetaDataManager.saveImmediately();
            return Success.SUCCESS;
        });
    }

    @Override
    public void updateExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {
        if (!mCachedExperiments.containsKey(experiment.getExperimentId())) {
            throw new IllegalArgumentException(
                    "Updating experiment not returned by DataController: " + experiment);
        }

        if (mCachedExperiments.get(experiment.getExperimentId()).get() != experiment) {
            throw new IllegalArgumentException(
                    "Updating different instance of experiment than is managed by DataController: "
                    + experiment);
        }

        // Every time we update the experiment, we can update its last used time.
        experiment.setLastUsedTime(mClock.getNow());
        background(mMetaDataThread, onSuccess, () -> {
            mMetaDataManager.updateExperiment(experiment);
            return Success.SUCCESS;
        });
    }

    @Override
    public String generateNewLabelId() {
        long nextLabelTimestamp = mClock.getNow();
        if (nextLabelTimestamp <= mPrevLabelTimestamp) {
            // Make sure we never use the same label ID twice.
            nextLabelTimestamp = mPrevLabelTimestamp + 1;
        }
        mPrevLabelTimestamp = nextLabelTimestamp;
        return "label_" + nextLabelTimestamp;
    }

    @Override
    public void getExperimentOverviews(final boolean includeArchived,
            final MaybeConsumer<List<GoosciUserMetadata.ExperimentOverview>> onSuccess) {
        background(mMetaDataThread, onSuccess,
                new Callable<List<GoosciUserMetadata.ExperimentOverview>>() {
                    @Override
                    public List<GoosciUserMetadata.ExperimentOverview> call() throws Exception {
                        return mMetaDataManager.getExperimentOverviews(includeArchived);
                    }
                });
    }

    @Override
    public List<GoosciUserMetadata.ExperimentOverview> blockingGetExperimentOverviews(
            boolean includeArchived) {
        return mMetaDataManager.getExperimentOverviews(includeArchived);
    }

    @Override
    public void getLastUsedUnarchivedExperiment(final MaybeConsumer<Experiment> onSuccess) {
        MaybeConsumer<Experiment> onSuccessWrapper = new MaybeConsumer<Experiment>() {
            @Override
            public void success(Experiment lastUsed) {
                if (lastUsed == null) {
                    onSuccess.success(null);
                    return;
                }
                if (mCachedExperiments.containsKey(lastUsed.getExperimentId())) {
                    // Use the same object if it's already in the cache.
                    Experiment cached = mCachedExperiments.get(lastUsed.getExperimentId()).get();
                    if (cached != null) {
                        onSuccess.success(cached);
                        return;
                    }
                }
                mCachedExperiments.put(lastUsed.getExperimentId(), new WeakReference<>(lastUsed));
                onSuccess.success(lastUsed);
            }

            @Override
            public void fail(Exception e) {
                onSuccess.fail(e);
            }
        };
        background(mMetaDataThread, onSuccessWrapper, new Callable<Experiment>() {
            @Override
            public Experiment call() throws Exception {
                return mMetaDataManager.getLastUsedUnarchivedExperiment();
            }
        });
    }

    @Override
    public void getExternalSensors(final MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Map<String, ExternalSensorSpec>>() {
            @Override
            public Map<String, ExternalSensorSpec> call() throws Exception {
                return mMetaDataManager.getExternalSensors(mProviderMap);
            }
        });
    }

    @Override
    public void getExternalSensorsByExperiment(final String experimentId,
            final MaybeConsumer<ExperimentSensors> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<ExperimentSensors>() {
            @Override
            public ExperimentSensors call() throws Exception {
                return mMetaDataManager.getExperimentSensors(experimentId, mProviderMap,
                        mConnector);
            }
        });
    }

    @Override
    public void getExternalSensorById(final String id,
                                      final MaybeConsumer<ExternalSensorSpec> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<ExternalSensorSpec>() {
            @Override
            public ExternalSensorSpec call() throws Exception {
                return mMetaDataManager.getExternalSensorById(id, mProviderMap);
            }
        });
    }

    @Override
    public void addSensorToExperiment(final String experimentId, final String sensorId,
            final MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.addSensorToExperiment(sensorId, experimentId);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void removeSensorFromExperiment(final String experimentId, final String sensorId,
            final MaybeConsumer<Success> onSuccess) {
        getExperimentById(experimentId, MaybeConsumers.chainFailure(onSuccess,
                new Consumer<Experiment>() {
                    @Override
                    public void take(final Experiment experiment) {
                        replaceIdInLayouts(experiment, sensorId, "");
                        background(mMetaDataThread, onSuccess,
                                new Callable<Success>() {
                                    @Override
                                    public Success call() throws Exception {
                                        mMetaDataManager.removeSensorFromExperiment(sensorId,
                                                experimentId);
                                        mMetaDataManager.updateExperiment(experiment);
                                        return Success.SUCCESS;
                                    }
                                });
                    }
                }));
    }

    @Override
    public void setDataErrorListenerForSensor(String sensorId, FailureListener listener) {
        mSensorFailureListeners.put(sensorId, listener);
    }

    @Override
    public void clearDataErrorListenerForSensor(String sensorId) {
        mSensorFailureListeners.remove(sensorId);
    }

    @Override
    public void addOrGetExternalSensor(final ExternalSensorSpec sensor,
            final MaybeConsumer<String> onSensorId) {
        background(mMetaDataThread, onSensorId, new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mMetaDataManager.addOrGetExternalSensor(sensor, mProviderMap);
            }
        });
    }

    private <T> void background(Executor dataThread, final MaybeConsumer<T> onSuccess,
            final Callable<T> job) {
        dataThread.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final T result = job.call();
                    mUiThread.execute(new Runnable() {
                        @Override
                        public void run() {
                            onSuccess.success(result);
                        }
                    });
                } catch (final Exception e) {
                    mUiThread.execute(new Runnable() {
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
        background(mMetaDataThread, onSuccess, new Callable<List<InputDeviceSpec>>() {
            @Override
            public List<InputDeviceSpec> call() throws Exception {
                return mMetaDataManager.getMyDevices();
            }
        });
    }

    @Override
    public void addMyDevice(final InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.addMyDevice(spec);
                return Success.SUCCESS;
            }
        });
    }

    @Override
    public void forgetMyDevice(final InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {
        background(mMetaDataThread, onSuccess, new Callable<Success>() {
            @Override
            public Success call() throws Exception {
                mMetaDataManager.removeMyDevice(spec);
                return Success.SUCCESS;
            }
        });
    }
}
