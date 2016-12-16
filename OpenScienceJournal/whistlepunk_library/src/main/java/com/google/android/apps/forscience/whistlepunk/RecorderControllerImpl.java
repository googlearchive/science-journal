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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.FallibleConsumer;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation
        .TriggerInformation;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTriggerLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.TriggerHelper;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensors.SystemScheduler;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps track of:
 * <ul>
 *     <li>All the recorders that are currently observing or recording</li>
 *     <li>The recording state (when did the current run start, if any?)</li>
 *     <li>The currently-selected experiment</li>
 * </ul>
 *
 */
public class RecorderControllerImpl implements RecorderController {
    private static final String TAG = "RecorderController";

    /**
     * Default delay to wait after the last observer stops before asking the sensor to stop
     * collecting.
     */
    //private static final Delay DEFAULT_STOP_DELAY = Delay.seconds(5);

    // TODO: remove this comment when we're sure about the delay.
    // To disable delayed stop, comment out the above line, and uncomment this one.
    private static final Delay DEFAULT_STOP_DELAY = Delay.ZERO;

    private DataController mDataController;
    private final Scheduler mScheduler;
    private final Delay mStopDelay;
    private Map<String, StatefulRecorder> mRecorders = new LinkedHashMap<>();
    private final Context mContext;
    private SensorRegistry mSensors;
    private final RecorderListenerRegistry mRegistry;
    private Map<String, String> mServiceObservers = new HashMap<>();
    private RecorderServiceConnection mServiceConnection = null;
    private int mPauseCount = 0;
    private final SensorEnvironment mSensorEnvironment;
    private Experiment mSelectedExperiment;
    private Map<String, TriggerFiredListener> mTriggerListeners = new HashMap<>();
    private List<GoosciSensorLayout.SensorLayout> mSensorLayouts = Collections.emptyList();
    private boolean mRecordingStateChangeInProgress;
    private TriggerHelper mTriggerHelper;
    private boolean mActivityInForeground = false;
    private int mStatsSaved = 0;
    private final Supplier<RecorderServiceConnection> mConnectionSupplier;

    private Map<String, RecordingStateListener> mRecordingStateListeners = new ArrayMap<>();
    private Map<String, ObservedIdsListener> mObservedIdListeners = new ArrayMap<>();

    /**
     * Label for the start of the current recording session, or null if not recording
     */
    private RecordingMetadata mRecording = null;

    public RecorderControllerImpl(Context context) {
        this(context, AppSingleton.getInstance(context).getDataController());
    }

    @VisibleForTesting
    public RecorderControllerImpl(Context context, DataController dataController) {
        this(context, SensorRegistry.createWithBuiltinSensors(context),
                AppSingleton.getInstance(context).getSensorEnvironment(),
                new RecorderListenerRegistry(), productionConnectionSupplier(context),
                dataController, new SystemScheduler(), DEFAULT_STOP_DELAY);
    }

    /**
     * @param scheduler for scheduling delayed stops if desired (to prevent sensor stop/start churn)
     * @param stopDelay how long to wait before stopping sensors.
     */
    @VisibleForTesting
    public RecorderControllerImpl(final Context context, SensorRegistry registry,
            SensorEnvironment sensorEnvironment, RecorderListenerRegistry listenerRegistry,
            Supplier<RecorderServiceConnection> connectionSupplier, DataController dataController,
            Scheduler scheduler, Delay stopDelay) {
        mContext = context;
        mSensors = registry;
        mSensorEnvironment = sensorEnvironment;
        mRegistry = listenerRegistry;
        mConnectionSupplier = connectionSupplier;
        mDataController = dataController;
        mScheduler = scheduler;
        mStopDelay = stopDelay;
    }

    @NonNull
    private static Supplier<RecorderServiceConnection> productionConnectionSupplier(
            final Context context) {
        return new Supplier<RecorderServiceConnection>() {
            @Override
            public RecorderServiceConnection get() {
                return new RecorderServiceConnectionImpl(context,
                        new FailureListener() {
                            @Override
                            public void fail(Exception e) {
                                if (Log.isLoggable(TAG, Log.ERROR)) {
                                    Log.e(TAG, "exception with remote service");
                                }
                            }
                        });
            }
        };
    }

    @Override
    public String startObserving(final String sensorId, final List<SensorTrigger> activeTriggers,
            SensorObserver observer, SensorStatusListener listener,
            final TransportableSensorOptions initialOptions) {
        // Put the observer and listener in the registry by sensorId.
        String observerId = mRegistry.putListeners(sensorId, observer, listener);
        StatefulRecorder sr = mRecorders.get(sensorId);
        if (sr != null) {
            RecorderControllerImpl.this.startObserving(sr);
            addServiceObserverIfNeeded(sensorId, activeTriggers);
        } else {
            mSensors.withSensorChoice(TAG, sensorId, new Consumer<SensorChoice>() {
                @Override
                public void take(SensorChoice sensor) {
                    final SensorRecorder recorder = sensor.createRecorder(mContext,
                            mRegistry.makeObserverForRecorder(sensorId), mRegistry,
                            mSensorEnvironment);
                    recorder.applyOptions(new ReadableTransportableSensorOptions(initialOptions));
                    StatefulRecorder newStatefulRecorder = new StatefulRecorder(recorder,
                            mScheduler, mStopDelay);
                    mRecorders.put(sensorId, newStatefulRecorder);
                    addServiceObserverIfNeeded(sensorId, activeTriggers);
                    RecorderControllerImpl.this.startObserving(newStatefulRecorder);
                }
            });
        }
        return observerId;
    }

    @Override
    public void reboot(String sensorId) {
        StatefulRecorder recorder = mRecorders.get(sensorId);
        if (recorder != null) {
            recorder.reboot();
        }
    }

    private void addServiceObserverIfNeeded(String sensorId,
            final List<SensorTrigger> activeTriggers) {
        if (!mServiceObservers.containsKey(sensorId)) {
            String serviceObserverId = mRegistry.putListeners(sensorId,
                    new SensorObserver() {
                        @Override
                        public void onNewData(long timestamp, Bundle data) {
                            if (!ScalarSensor.hasValue(data)) {
                                return;
                            }
                            double value = ScalarSensor.getValue(data);
                            // Fire triggers.
                            for (SensorTrigger trigger : activeTriggers) {
                                if (!isRecording() && trigger.shouldTriggerOnlyWhenRecording()) {
                                    continue;
                                }
                                if (trigger.isTriggered(value)) {
                                    fireSensorTrigger(trigger, timestamp);
                                }
                            }
                        }
                    }, null);
            mServiceObservers.put(sensorId, serviceObserverId);
        }
    }

    private void fireSensorTrigger(SensorTrigger trigger, long timestamp) {
        // TODO: Think about behavior for triggers firing near the same time, especially
        // regarding start/stop recording and notes. Right now behavior may not seem repeatable
        // depending on timing of callbacks and order of triggers. b/
        boolean triggerWasFired = false;
        if (trigger.getActionType() == TriggerInformation.TRIGGER_ACTION_START_RECORDING &&
                !isRecording() && mSelectedExperiment != null) {
            if (!mRecordingStateChangeInProgress) {
                triggerWasFired = true;
                for (TriggerFiredListener listener : mTriggerListeners.values()) {
                    listener.onRequestStartRecording();
                }
                mDataController.getProjectById(mSelectedExperiment.getProjectId(),
                        new LoggingConsumer<Project>(TAG, "get project to record into") {
                            @Override
                            public void success(Project project) {
                                startRecording(new Intent(mContext, MainActivity.class), project);
                            }
                        });
                WhistlePunkApplication.getUsageTracker(mContext).trackEvent(
                        TrackerConstants.CATEGORY_RUNS,
                        TrackerConstants.ACTION_TRY_RECORDING_FROM_TRIGGER, null, 0);
            }
        } else if (trigger.getActionType() == TriggerInformation.TRIGGER_ACTION_STOP_RECORDING &&
                isRecording()) {
            if (!mRecordingStateChangeInProgress) {
                triggerWasFired = true;
                for (TriggerFiredListener listener : mTriggerListeners.values()) {
                    listener.onRequestStopRecording(this);
                }
                stopRecording();
                WhistlePunkApplication.getUsageTracker(mContext).trackEvent(
                        TrackerConstants.CATEGORY_RUNS,
                        TrackerConstants.ACTION_TRY_STOP_RECORDING_FROM_TRIGGER, null, 0);
            }
        } else if (trigger.getActionType() == TriggerInformation.TRIGGER_ACTION_NOTE) {
            triggerWasFired = true;
            addTriggerLabel(timestamp, trigger, mContext);
        } else if (trigger.getActionType() == TriggerInformation.TRIGGER_ACTION_ALERT) {
            if (trigger.getAlertTypes().length > 0) {
                triggerWasFired = true;
            }
            if (trigger.hasAlertType(TriggerInformation.TRIGGER_ALERT_PHYSICAL)) {
                getTriggerHelper().doVibrateAlert(mContext);
            }
            if (trigger.hasAlertType(TriggerInformation.TRIGGER_ALERT_AUDIO)) {
                getTriggerHelper().doAudioAlert(mContext);
            }
            // Visual alerts are not covered in RecorderControllerImpl.
        }
        // Not all triggers should actually be fired -- for example, we cannot stop recording if
        // we are not yet recording. Only call the trigger fired listeners when the event actually
        // takes place.
        if (triggerWasFired) {
            for (TriggerFiredListener listener : mTriggerListeners.values()) {
                listener.onTriggerFired(trigger);
            }
        }
    }

    private TriggerHelper getTriggerHelper() {
        if (mTriggerHelper == null) {
            mTriggerHelper = new TriggerHelper();
        }
        return mTriggerHelper;
    }

    private void addTriggerLabel(long timestamp, SensorTrigger trigger, Context context) {
        if (mSelectedExperiment == null) {
            return;
        }
        Label triggerLabel = new SensorTriggerLabel(mDataController.generateNewLabelId(),
                getCurrentRunId(), timestamp, trigger, context);
        triggerLabel.setExperimentId(mSelectedExperiment.getExperimentId());
        mDataController.addLabel(triggerLabel,
                new LoggingConsumer<Label>(TAG, "add trigger label") {
                    @Override
                    public void success(Label label) {
                        String trackerLabel = isRecording() ? TrackerConstants.LABEL_RECORD :
                                TrackerConstants.LABEL_OBSERVE;
                        WhistlePunkApplication.getUsageTracker(mContext)
                                .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                        TrackerConstants.ACTION_CREATE,
                                        trackerLabel,
                                        TrackerConstants.getLabelValueType(label));
                        for (TriggerFiredListener listener : mTriggerListeners.values()) {
                            listener.onLabelAdded(label);
                        }
                    }
                });
    }

    private String getCurrentRunId() {
        return mRecording == null? RecordFragment.NOT_RECORDING_RUN_ID : mRecording.getRunId();
    }

    private void startObserving(StatefulRecorder sr) {
        sr.startObserving();
        updateObservedIdListeners();
    }

    @Override
    public void clearSensorTriggers(String sensorId) {
        String observerId = mServiceObservers.get(sensorId);
        if (!TextUtils.isEmpty(observerId)) {
            // Remove the old serviceObserver and add a new one with no triggers.
            mServiceObservers.remove(sensorId);
            mRegistry.remove(sensorId, observerId);
            addServiceObserverIfNeeded(sensorId, Collections.<SensorTrigger>emptyList());
        }
    }

    @Override
    public void stopObserving(String sensorId, String observerId) {
        mRegistry.remove(sensorId, observerId);
        // If there are no listeners left except for our serviceobserver, remove it.
        if (mRegistry.countListeners(sensorId) == 1) {
            stopObservingServiceObserver(sensorId);
        }
        cleanUpUnusedRecorders();
        updateObservedIdListeners();
        if (mRecorders.isEmpty()) {
            SensorHistoryStorage storage = mSensorEnvironment.getSensorHistoryStorage();
            storage.setMostRecentSensorIds(Lists.newArrayList(sensorId));
        }
    }

    private void stopObservingServiceObserver(String sensorId) {
        final StatefulRecorder r = mRecorders.get(sensorId);
        if (r != null) {
            r.stopObserving();
            if (!r.isRecording()) {
                // If it was not recording, we can also remove our service-level observers.
                if (mServiceObservers.containsKey(sensorId)) {
                    String serviceObserverId = mServiceObservers.get(sensorId);
                    mRegistry.remove(sensorId, serviceObserverId);
                    mServiceObservers.remove(sensorId);
                }
            }
        }
    }

    private void updateObservedIdListeners() {
        List<String> currentObservedSensorIds = getCurrentObservedSensorIds();
        for (ObservedIdsListener observedIdsListener : mObservedIdListeners.values()) {
            observedIdsListener.onObservedIdsChanged(currentObservedSensorIds);
        }
    }

    @Override
    public List<String> getMostRecentObservedSensorIds() {
        List<String> keys = getCurrentObservedSensorIds();
        if (keys.isEmpty()) {
            return mSensorEnvironment.getSensorHistoryStorage().getMostRecentSensorIds();
        } else {
            return keys;
        }
    }

    private List<String> getCurrentObservedSensorIds() {
        return Lists.newArrayList(mRecorders.keySet());
    }

    // TODO: test this logic
    @Override
    public String pauseObservingAll() {
        ++mPauseCount;
        if (!isRecording()) {
            for (StatefulRecorder recorder : mRecorders.values()) {
                recorder.stopObserving();
            }
        }
        return String.valueOf(mPauseCount);
    }

    // TODO: test this logic
    @Override
    public boolean resumeObservingAll(String pauseId) {
        if (!Objects.equals(pauseId, String.valueOf(mPauseCount))) {
            return false;
        }
        if (!isRecording()) {
            for (StatefulRecorder recorder : mRecorders.values()) {
                startObserving(recorder);
            }
        }
        return true;
    }

    @Override
    public void applyOptions(String sensorId, TransportableSensorOptions settings) {
        final StatefulRecorder r = mRecorders.get(sensorId);
        if (r != null) {
            r.applyOptions(new ReadableTransportableSensorOptions(settings));
        }
    }

    private void cleanUpUnusedRecorders() {
        final Iterator<Map.Entry<String, StatefulRecorder>> iter = mRecorders.entrySet().iterator();
        while (iter.hasNext()) {
            StatefulRecorder r = iter.next().getValue();
            if (!r.isStillRunning()) {
                iter.remove();
            }
        }
    }

    @Override
    public void startRecording(final Intent resumeIntent, final Project project) {
        if (mRecording != null || mRecordingStateChangeInProgress) {
            return;
        }
        if (mRecorders.size() == 0) {
            // If the recorders are empty, then we stopped observing a sensor before we tried
            // to start recording it. This may happen if we failed to connect to an external sensor
            // and it was disconnected before recording started.
            callRecordingStartFailedListeners(RecorderController.ERROR_START_FAILED, null);
            return;
        }
        // Check that all sensors are connected before starting a recording.
        for (String sensorId : mRecorders.keySet()) {
            if (!mRegistry.isSourceConnectedWithoutError(sensorId)) {
                callRecordingStartFailedListeners(
                        RecorderController.ERROR_START_FAILED_DISCONNECTED, null);
                return;
            }
        }
        mRecordingStateChangeInProgress = true;
        withBoundRecorderService(new FallibleConsumer<RecorderService>() {
            @Override
            public void take(final RecorderService recorderService) throws RemoteException {
                final DataController dataController = mDataController;
                dataController.startRun(mSelectedExperiment,
                        new LoggingConsumer<ApplicationLabel>(TAG, "store label") {
                            @Override
                            public void success(ApplicationLabel label) {
                                mRecording = new RecordingMetadata(label.getTimeStamp(),
                                        label.getRunId(),
                                        mSelectedExperiment.getDisplayTitle(mContext));

                                ensureUnarchived(mSelectedExperiment, project, dataController);
                                recorderService.beginServiceRecording(
                                        mRecording.getExperimentName(), resumeIntent);

                                for (StatefulRecorder recorder : mRecorders.values()) {
                                    recorder.startRecording(mRecording.getRunId());
                                }
                                updateRecordingListeners();
                                mRecordingStateChangeInProgress = false;
                            }

                            @Override
                            public void fail(Exception e) {
                                super.fail(e);
                                callRecordingStartFailedListeners(
                                        RecorderController.ERROR_START_FAILED, e);
                                mRecordingStateChangeInProgress = false;
                            }
                        });
            }
        });
    }

    @Override
    public void stopRecording() {
        if (mRecording == null || mSelectedExperiment == null || mRecordingStateChangeInProgress) {
            return;
        }

        // TODO: What happens when recording stop fails and the app is in the background?

        // First check that all sensors have at least one data point. A recording with no
        // data is invalid.
        for (String sensorId : mRecorders.keySet()) {
            // If we try to stop recording when a sensor is not connected, recording stop fails.
            if (!mRegistry.isSourceConnectedWithoutError(sensorId)) {
                callRecordingStopFailedListeners(RecorderController.ERROR_STOP_FAILED_DISCONNECTED);
                return;
            }
            // Check to see if it has no data recorded, as this also fails stop recording.
            if (!mRecorders.get(sensorId).hasRecordedData()) {
                callRecordingStopFailedListeners(RecorderController.ERROR_STOP_FAILED_NO_DATA);
                return;
            }
        }
        mRecordingStateChangeInProgress = true;
        final boolean activityInForground = mActivityInForeground;
        withBoundRecorderService(new FallibleConsumer<RecorderService>() {
            @Override
            public void take(final RecorderService recorderService) throws RemoteException {
                final List<GoosciSensorLayout.SensorLayout> sensorLayoutsAtStop =
                        new ArrayList<>(mSensorLayouts);
                mDataController
                        .stopRun(mSelectedExperiment, mRecording.getRunId(), mSensorLayouts,
                        new LoggingConsumer<ApplicationLabel>(TAG, "store label") {
                            @Override
                            public void success(ApplicationLabel value) {
                                trackStopRecording(recorderService.getApplicationContext(), value,
                                        sensorLayoutsAtStop);
                                final String runId = mRecording.getRunId();

                                // Now actually stop the recording.
                                mRecording = null;
                                mStatsSaved = 0;
                                final int statsToSave = mRecorders.values().size();
                                LoggingConsumer<Success> onSuccess =
                                        new LoggingConsumer<Success>(TAG, "save stats") {
                                    @Override
                                    public void success(Success value) {
                                        if (++mStatsSaved == statsToSave) {
                                            // Close the service. When the service is closed, if the
                                            // app is in the background, all processes will stop --
                                            // so this needs to be the last thing to happen!
                                            recorderService.endServiceRecording(
                                                    !activityInForground, runId,
                                                    mSelectedExperiment.getDisplayTitle(mContext));
                                        }
                                    }
                                };
                                for (StatefulRecorder recorder : mRecorders.values()) {
                                    recorder.stopRecording(onSuccess);
                                }

                                // Now stop observing in the service, because after recording
                                // completes we don't want to keep observing and firing triggers in
                                // the foreground or background.
                                // NOTE: We assume that after recording stops we do *not* want
                                // to keep observing.
                                List<String> sensorIds = new ArrayList();
                                sensorIds.addAll(mServiceObservers.keySet());
                                for (String sensorId : sensorIds) {
                                    stopObservingServiceObserver(sensorId);
                                }
                                mServiceObservers.clear();

                                cleanUpUnusedRecorders();
                                updateRecordingListeners();
                                mRecordingStateChangeInProgress = false;
                            }

                            @Override
                            public void fail(Exception e) {
                                super.fail(e);
                                callRecordingStopFailedListeners(
                                        RecorderController.ERROR_FAILED_SAVE_RECORDING);
                                mRecordingStateChangeInProgress = false;
                            }
                        });
            }
        });
    }

    @Override
    public void stopRecordingWithoutSaving() {
        if (mRecording == null || mRecordingStateChangeInProgress) {
            return;
        }
        mRecording = null;
        for (StatefulRecorder recorder : mRecorders.values()) {
            recorder.stopRecording(LoggingConsumer.<Success>expectSuccess(TAG, "stop recording"));
        }
        mRecordingStateChangeInProgress = true;
        withBoundRecorderService(new FallibleConsumer<RecorderService>() {
            @Override
            public void take(RecorderService recorderService) throws RemoteException {
                recorderService.endServiceRecording(false, "", "");
                mRecordingStateChangeInProgress = false;
            }
        });
        cleanUpUnusedRecorders();
        updateRecordingListeners();
    }

    @VisibleForTesting
    void trackStopRecording(Context context, ApplicationLabel stopRecordingLabel,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        SensorRegistry registry = AppSingleton.getInstance(context).getSensorRegistry();
        // Record how long this session was.
        WhistlePunkApplication.getUsageTracker(context)
                .trackEvent(TrackerConstants.CATEGORY_RUNS,
                        TrackerConstants.ACTION_CREATE, null,
                        stopRecordingLabel.getTimeStamp() - mRecording.getStartTime());

        // Record which sensors were recorded and information about their layouts.
        List<String> sensorLogs = new ArrayList<>();
        for (GoosciSensorLayout.SensorLayout layout : sensorLayouts) {
            sensorLogs.add(getLayoutLoggingString(registry.getLoggingId(layout.sensorId), layout));
        }
        WhistlePunkApplication.getUsageTracker(context)
                .trackEvent(TrackerConstants.CATEGORY_RUNS,
                        TrackerConstants.ACTION_RECORDED,
                        Joiner.on(",").join(sensorLogs),
                        0);
    }

    String getLayoutLoggingString(String loggingId, GoosciSensorLayout.SensorLayout layout) {
        StringBuilder builder = new StringBuilder();
        builder.append(loggingId);
        builder.append("|");
        builder.append(layout.cardView == GoosciSensorLayout.SensorLayout.METER ? "meter" :
                "graph");
        builder.append("|");
        builder.append(layout.audioEnabled ? "audioOn" : "audioOff");
        return builder.toString();
    }

    private void updateRecordingListeners() {
        for (RecordingStateListener listener : mRecordingStateListeners.values()) {
            listener.onRecordingStateChanged(mRecording);
        }
    }

    private void callRecordingStopFailedListeners(@RecordingStopErrorType int errorType) {
        for (RecordingStateListener listener : mRecordingStateListeners.values()) {
            listener.onRecordingStopFailed(errorType);
        }
    }

    private void callRecordingStartFailedListeners(@RecordingStartErrorType int errorType,
            Exception e) {
        for (RecordingStateListener listener : mRecordingStateListeners.values()) {
            listener.onRecordingStartFailed(errorType, e);
        }
    }

    /**
     * Convenience function for asynchronously binding to the service and doing something with it.
     *
     * @param c what to do
     */
    protected void withBoundRecorderService(final FallibleConsumer<RecorderService> c) {
        if (mServiceConnection == null) {
            mServiceConnection = mConnectionSupplier.get();
        }
        mServiceConnection.runWithService(c);
    }

    @Override
    public void addRecordingStateListener(String listenerId, RecordingStateListener listener) {
        if (mRecordingStateListeners.containsKey(listenerId)) {
            throw new IllegalStateException("Already listening on: " + listenerId);
        }
        mRecordingStateListeners.put(listenerId, listener);
        listener.onRecordingStateChanged(mRecording);
    }

    @Override
    public void removeRecordingStateListener(String listenerId) {
        mRecordingStateListeners.remove(listenerId);
    }

    @Override
    public void addTriggerFiredListener(String listenerId, TriggerFiredListener listener) {
        if (mTriggerListeners.containsKey(listenerId)) {
            throw new IllegalStateException("Already listening to triggers on: " + listenerId);
        }
        mTriggerListeners.put(listenerId, listener);
    }

    @Override
    public void removeTriggerFiredListener(String listenerId) {
        mTriggerListeners.remove(listenerId);
    }

    @Override
    public void addObservedIdsListener(String listenerId, ObservedIdsListener listener) {
        mObservedIdListeners.put(listenerId, listener);
        listener.onObservedIdsChanged(getMostRecentObservedSensorIds());
    }

    @Override
    public void removeObservedIdsListener(String listenerId) {
        mObservedIdListeners.remove(listenerId);
    }

    @Override
    public void setSelectedExperiment(Experiment experiment) {
        mSelectedExperiment = experiment;
    }

    @Override
    public void setCurrentSensorLayouts(List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        mSensorLayouts = sensorLayouts;
    }

    private boolean isRecording() {
        return mRecording != null;
    }

    @Override
    public void updateExternalSensors(List<ConnectableSensor> sensors) {
        mSensors.updateExternalSensors(sensors,
                AppSingleton.getInstance(mContext).getExternalSensorProviders());
    }

    @Override
    public void refreshBuiltinSensors() {
        mSensors.refreshBuiltinSensors(mContext);
    }

    @VisibleForTesting
    public Map<String, StatefulRecorder> getRecorders() {
        return mRecorders;
    }

    @VisibleForTesting
    void ensureUnarchived(Experiment experiment, Project project, DataController dc) {
        RecordFragment.ensureUnarchived(experiment, project, dc);
    }

    @Override
    public void setRecordActivityInForeground(boolean isInForeground) {
        mActivityInForeground = isInForeground;
    }
}
