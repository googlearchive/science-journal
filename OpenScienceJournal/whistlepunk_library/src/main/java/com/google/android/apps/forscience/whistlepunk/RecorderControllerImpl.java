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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.VisibleForTesting;
import android.util.ArrayMap;
import android.util.Log;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.FallibleConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

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

    @VisibleForTesting
    static class StatefulRecorder {
        private boolean mObserving = false;
        private boolean mRecording = false;
        private final SensorRecorder mRecorder;
        private boolean mForceHasDataForTesting = false;

        private StatefulRecorder(SensorRecorder mRecorder) {
            this.mRecorder = mRecorder;
        }

        public void startObserving() {
            if (!isStillRunning()) {
                mRecorder.startObserving();
            }
            mObserving = true;
        }

        // TODO: Note that this doesn't necessarily call mRecorder.stopObserving, because
        // the current SensorRecorder spec assumes stopObserving also stops recording.  Should that
        // change?
        public void stopObserving() {
            mObserving = false;
            maybeStopObserving();
        }

        /**
         * @param runId id for the run that is starting, to allow out-of-band data saving
         */
        public void startRecording(String runId) {
            mRecorder.startRecording(runId);
            mRecording = true;
        }


        public void stopRecording() {
            mRecorder.stopRecording();
            mRecording = false;
        }

        // The spec for SensorRecorder says that once you stopObserving, the recorder should
        // shut down.  Unless we decide to change that (and retrofit all current sensors to that
        // new design), we should only _actually_ call stopObserving once we're no longer
        // observing OR recording.
        private void maybeStopObserving() {
            if (!isStillRunning()) {
                mRecorder.stopObserving();
            }
        }

        public boolean isStillRunning() {
            return mObserving || mRecording;
        }

        public void applyOptions(ReadableSensorOptions settings) {
            mRecorder.applyOptions(settings);
        }

        public boolean hasRecordedData() {
            return mRecorder.hasRecordedData() || mForceHasDataForTesting;
        }

        // TODO: Find a better way to fake force add data than this method.
        @VisibleForTesting
        void forceHasDataForTesting(boolean hasDataForTesting) {
            mForceHasDataForTesting = hasDataForTesting;
        }
    }

    private static class RecorderServiceConnection implements ServiceConnection {
        private final FailureListener mOnFailure;
        private Queue<FallibleConsumer<RecorderService>> mOperations = new LinkedList<>();
        private RecorderService mService;

        public RecorderServiceConnection(Context context, FailureListener onFailure) {
            mOnFailure = onFailure;
            context.bindService(new Intent(context, RecorderService.class), this,
                    Context.BIND_AUTO_CREATE);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RecorderService.Binder binder = (RecorderService.Binder) service;
            mService = binder.getService();
            while (! mOperations.isEmpty()) {
                runOperation(mOperations.remove());
            }
        }

        private void runOperation(FallibleConsumer<RecorderService> op) {
            try {
                op.take(mService);
            } catch (Exception e) {
                mOnFailure.fail(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        public void runWithService(FallibleConsumer<RecorderService> c) {
            if (mService != null) {
                runOperation(c);
            } else {
                mOperations.add(c);
            }
        }
    }

    private Map<String, StatefulRecorder> mRecorders = new LinkedHashMap<>();
    private final Context mContext;
    private SensorRegistry mSensors;
    private final RecorderListenerRegistry mRegistry;
    private Map<String, String> mServiceObservers = new HashMap<>();
    private RecorderServiceConnection mServiceConnection = null;
    private int mPauseCount = 0;
    private final SensorEnvironment mSensorEnvironment;

    private FailureListener mRemoteFailureListener = new FailureListener() {
        @Override
        public void fail(Exception e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "exception with remote service");
            }
        }
    };

    private Map<String, RecordingStateListener> mRecordingStateListeners = new ArrayMap<>();
    private Map<String, ObservedIdsListener> mObservedIdListeners = new ArrayMap<>();

    /**
     * Label for the start of the current recording session, or null if not recording
     */
    private RecordingMetadata mRecording = null;

    public RecorderControllerImpl(Context context) {
        this(context, SensorRegistry.createWithBuiltinSensors(context),
                AppSingleton.getInstance(context).getSensorEnvironment(),
                new RecorderListenerRegistry());
    }

    @VisibleForTesting
    public RecorderControllerImpl(Context context, SensorRegistry registry,
            SensorEnvironment sensorEnvironment, RecorderListenerRegistry listenerRegistry) {
        mContext = context;
        mSensors = registry;
        mSensorEnvironment = sensorEnvironment;
        mRegistry = listenerRegistry;
    }

    @Override
    public String startObserving(final String sensorId, SensorObserver observer,
            SensorStatusListener listener, final TransportableSensorOptions initialOptions) {
        String observerId = mRegistry.putListeners(sensorId, observer, listener);
        StatefulRecorder sr = mRecorders.get(sensorId);
        if (sr != null) {
            RecorderControllerImpl.this.startObserving(sr);
        } else {
            mSensors.withSensorChoice(sensorId, new Consumer<SensorChoice>() {
                @Override
                public void take(SensorChoice sensor) {
                    final SensorRecorder recorder = sensor.createRecorder(mContext,
                            mRegistry.makeObserverForRecorder(sensorId), mRegistry,
                            mSensorEnvironment);
                    recorder.applyOptions(new ReadableTransportableSensorOptions(initialOptions));
                    StatefulRecorder newStatefulRecorder = new StatefulRecorder(recorder);
                    mRecorders.put(sensorId, newStatefulRecorder);

                    if (!mServiceObservers.containsKey(sensorId)) {
                        String serviceObserverId = mRegistry.putListeners(sensorId,
                                new SensorObserver() {
                                    @Override
                                    public void onNewData(long timestamp, Bundle data) {
                                        // TODO: Add code to fire triggers here.
                                    }
                                }, null);
                        mServiceObservers.put(sensorId, serviceObserverId);
                    }
                    RecorderControllerImpl.this.startObserving(newStatefulRecorder);
                }
            });
        }
        return observerId;
    }

    private void startObserving(StatefulRecorder sr) {
        sr.startObserving();
        updateObservedIdListeners();
    }

    @Override
    public void stopObserving(String sensorId, String observerId) {
        mRegistry.remove(sensorId, observerId);
        // If there are no listeners left except for our serviceobserver, remove it.
        if (mRegistry.countListeners(sensorId) == 1) {
            final StatefulRecorder r = mRecorders.get(sensorId);
            if (r != null) {
                r.stopObserving();
                if (!r.isStillRunning()) {
                    // Then it was not recording, so we can also remove our service-level observers.
                    if (mServiceObservers.containsKey(sensorId)) {
                        String serviceObserverId = mServiceObservers.get(sensorId);
                        mRegistry.remove(sensorId, serviceObserverId);
                        mServiceObservers.remove(sensorId);
                    }
                }
            }
        }
        cleanUpUnusedRecorders();
        updateObservedIdListeners();
        if (mRecorders.isEmpty()) {
            SensorHistoryStorage storage = mSensorEnvironment.getSensorHistoryStorage();
            storage.setMostRecentSensorIds(Lists.newArrayList(sensorId));
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
                RecorderControllerImpl.this.startObserving(recorder);
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
            StatefulRecorder value = iter.next().getValue();
            if (!value.isStillRunning()) {
                iter.remove();
            }
        }
    }

    @Override
    public void startRecording(final Intent resumeIntent, final Experiment experiment, final
            Project project) {
        if (mRecording != null) {
            return;
        }
        if (mRecorders.size() == 0) {
            // If the recorders are empty, then we stopped observing a sensor before we tried
            // to start recording it. This may happen if we failed to connect to an external sensor
            // and it was disconnected before recording started.
            callRecordingStartFailedListeners(RecorderController.ERROR_START_FAILED);
            return;
        }

        // Check that all sensors are connected before starting a recording.
        for (String sensorId : mRecorders.keySet()) {
            if (!mRegistry.isSourceConnectedWithoutError(sensorId)) {
                callRecordingStartFailedListeners(
                        RecorderController.ERROR_START_FAILED_DISCONNECTED);
                return;
            }
        }
        withBoundRecorderService(new FallibleConsumer<RecorderService>() {
            @Override
            public void take(final RecorderService recorderService) throws RemoteException {
                final DataController dataController = getDataController(
                        recorderService.getApplicationContext());
                dataController.startRun(experiment,
                        new LoggingConsumer<ApplicationLabel>(TAG, "store label") {
                            @Override
                            public void success(ApplicationLabel label) {
                                mRecording = new RecordingMetadata(label.getTimeStamp(),
                                        label.getRunId(),
                                        experiment.getDisplayTitle(
                                                recorderService.getApplicationContext()));
                                ensureUnarchived(experiment, project, dataController);
                                recorderService.beginServiceRecording(
                                        mRecording.getExperimentName(), resumeIntent);

                                for (StatefulRecorder recorder : mRecorders.values()) {
                                    recorder.startRecording(mRecording.getRunId());
                                }
                                updateRecordingListeners();
                            }

                            @Override
                            public void fail(Exception e) {
                                super.fail(e);
                                callRecordingStartFailedListeners(
                                        RecorderController.ERROR_START_FAILED);
                            }
                        });
            }
        });
    }

    @VisibleForTesting
    protected DataController getDataController(Context context) {
        return AppSingleton.getInstance(context).getDataController();
    }

    @Override
    public void stopRecording(final Experiment experiment,
            final List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        if (mRecording == null) {
            return;
        }

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
        withBoundRecorderService(new FallibleConsumer<RecorderService>() {
            @Override
            public void take(final RecorderService recorderService) throws RemoteException {
                getDataController(recorderService.getApplicationContext())
                        .stopRun(experiment, mRecording.getRunId(), sensorLayouts,
                        new LoggingConsumer<ApplicationLabel>(TAG, "store label") {
                            @Override
                            public void success(ApplicationLabel value) {
                                trackStopRecording(recorderService.getApplicationContext(), value);

                                // Now actually stop the recording.
                                mRecording = null;
                                for (StatefulRecorder recorder : mRecorders.values()) {
                                    recorder.stopRecording();
                                }
                                recorderService.endServiceRecording();
                                cleanUpUnusedRecorders();
                                updateRecordingListeners();
                            }

                            @Override
                            public void fail(Exception e) {
                                super.fail(e);
                                callRecordingStopFailedListeners(
                                        RecorderController.ERROR_FAILED_SAVE_RECORDING);
                            }
                        });
            }
        });
    }

    @Override
    public void stopRecordingWithoutSaving() {
        if (mRecording == null) {
            return;
        }
        mRecording = null;
        for (StatefulRecorder recorder : mRecorders.values()) {
            recorder.stopRecording();
        }
        withBoundRecorderService(new FallibleConsumer<RecorderService>() {
            @Override
            public void take(RecorderService recorderService) throws RemoteException {
                recorderService.endServiceRecording();
            }
        });
        cleanUpUnusedRecorders();
        updateRecordingListeners();
    }

    @VisibleForTesting
    void trackStopRecording(Context context, ApplicationLabel stopRecordingLabel) {
        // Record how long this session was.
        WhistlePunkApplication.getUsageTracker(context)
                .trackEvent(TrackerConstants.CATEGORY_RUNS,
                        TrackerConstants.ACTION_CREATE, null,
                        stopRecordingLabel.getTimeStamp() - mRecording.getStartTime());

        // Record which sensors were recorded.
        Set<String> sensors = mRecorders.keySet();
        String[] sensorArray = sensors.toArray(new String[sensors.size()]);
        Arrays.sort(sensorArray);
        WhistlePunkApplication.getUsageTracker(context)
                .trackEvent(TrackerConstants.CATEGORY_RUNS,
                        TrackerConstants.ACTION_RECORDED,
                        Joiner.on(",").join(sensorArray),
                        0);
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

    private void callRecordingStartFailedListeners(@RecordingStartErrorType int errorType) {
        for (RecordingStateListener listener : mRecordingStateListeners.values()) {
            listener.onRecordingStartFailed(errorType);
        }
    }

    /**
     * Convenience function for asynchronously binding to the service and doing something with it.
     *
     * @param c what to do
     */
    protected void withBoundRecorderService(final FallibleConsumer<RecorderService> c) {
        if (mServiceConnection == null) {
            mServiceConnection = new RecorderServiceConnection(mContext, mRemoteFailureListener);
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
    public void addObservedIdsListener(String listenerId, ObservedIdsListener listener) {
        mObservedIdListeners.put(listenerId, listener);
        listener.onObservedIdsChanged(getMostRecentObservedSensorIds());
    }

    @Override
    public void removeObservedIdsListener(String listenerId) {
        mObservedIdListeners.remove(listenerId);
    }

    private boolean isRecording() {
        return mRecording != null;
    }

    @Override
    public void updateExternalSensors(Map<String, ExternalSensorSpec> sensors) {
        mSensors.updateExternalSensors(sensors);
    }

    @VisibleForTesting
    public Map<String, StatefulRecorder> getRecorders() {
        return mRecorders;
    }

    @VisibleForTesting
    void ensureUnarchived(Experiment experiment, Project project, DataController dc) {
        RecordFragment.ensureUnarchived(experiment, project, dc);
    }
}
