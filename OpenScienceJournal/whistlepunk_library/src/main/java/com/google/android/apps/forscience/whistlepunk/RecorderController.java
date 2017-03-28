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

import android.content.Intent;
import android.support.annotation.IntDef;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;
import com.google.common.base.Supplier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

public interface RecorderController extends SensorRegistryListener {

    // Errors when a recording state fails to change.
    int ERROR_START_FAILED = 0;
    int ERROR_START_FAILED_DISCONNECTED = 1;

    @IntDef({ERROR_START_FAILED, ERROR_START_FAILED_DISCONNECTED})
    @Retention(RetentionPolicy.SOURCE)
    @interface RecordingStartErrorType {}

    int ERROR_STOP_FAILED_DISCONNECTED = 0;
    int ERROR_STOP_FAILED_NO_DATA = 1;
    int ERROR_FAILED_SAVE_RECORDING = 2;

    @IntDef({ERROR_STOP_FAILED_DISCONNECTED, ERROR_STOP_FAILED_NO_DATA,
            ERROR_FAILED_SAVE_RECORDING})
    @Retention(RetentionPolicy.SOURCE)
    @interface RecordingStopErrorType {}

    /**
     * @return observerId: should be passed to stopObserving, so that this client only kills
     * observers that it creates.
     */
    String startObserving(String sensorId, List<SensorTrigger> activeTriggers,
            SensorObserver observer, SensorStatusListener listener,
            TransportableSensorOptions initialOptions);

    /**
     * @param observerId the observerId returned from the corresponding call to startObserving
     */
    void stopObserving(String sensorId, String observerId);

    /**
     * @return A different unique string id each time this is called
     */
    String pauseObservingAll();

    /**
     * @param pauseId the pauseId returned by a previous call to pauseObservingAll
     * @return true iff we were able to resume streaming to the observers that were paused
     *         when that pauseId was returned, otherwise false (which means the observers will
     *         need to be manually reconnected)
     */
    boolean resumeObservingAll(String pauseId);

    void applyOptions(String sensorId, TransportableSensorOptions settings);

    /**
     * @param resumeIntent this must be distinct from any other active Intent, as defined by
     *                     {@link Intent#filterEquals(Intent)}
     */
    void startRecording(Intent resumeIntent, Project project);

    void stopRecording();

    /**
     * Retry connecting to the given sensor, which is currently in an error state.
     */
    void reboot(String sensorId);

    void stopRecordingWithoutSaving();

    /**
     * @return the most recently-observed ids when observation was happening.  If observation is
     * currently happening, the currently-observed ids, in the order that they began observation.
     *
     * Otherwise, the ids that were active the last time observation was active, or an empty set
     * if observation has never occurred.
     */
    List<String> getMostRecentObservedSensorIds();

    interface RecordingStateListener {
        void onRecordingStateChanged(RecordingMetadata currentRecording);

        void onRecordingStartFailed(@RecordingStartErrorType int errorType, Exception e);

        void onRecordingStopFailed(@RecordingStopErrorType int errorType);
    }

    public static int NO_LISTENER_ID = -1;

    /**
     * @return an integer id that should be passed to {@link #removeRecordingStateListener(int)} to
     *         stop listening.  Guaranteed to be >= 0.
     */
    int addRecordingStateListener(RecordingStateListener listener);

    void removeRecordingStateListener(int listenerId);

    interface TriggerFiredListener {
        /**
         * Called any time a trigger fires successfully.
         * Note that this is not called for a trigger to stop recording when recording is not
         * yet started, or for a trigger to start recording when a recording going on, or for
         * an alert type trigger that has no alert types selected.
         */
        void onTriggerFired(SensorTrigger trigger);

        /**
         * Called before the RecorderController tries to start the recording from a trigger.
         */
        void onRequestStartRecording();

        /**
         * Called when the RecorderController has added a trigger label successfully.
         */
        void onLabelAdded(Label label);

        /**
         * Called before the RecorderController tries to stop the recording from a trigger.
         */
        void onRequestStopRecording(RecorderController rc);
    }


    /**
     * @return an integer id that should be passed to {@link #removeTriggerFiredListener(int)} to
     *         stop listening.  Guaranteed to be >= 0.
     */
    int addTriggerFiredListener(TriggerFiredListener listener);

    void removeTriggerFiredListener(int listenerId);

    interface ObservedIdsListener {
        void onObservedIdsChanged(List<String> observedSensorIds);
    }

    void addObservedIdsListener(String listenerId, ObservedIdsListener listener);

    void removeObservedIdsListener(String listenerId);

    void setSelectedExperiment(Experiment experiment);

    void setRecordActivityInForeground(boolean isInForeground);

    void clearSensorTriggers(String sensorId);

    /**
     * @param supplier can be called to get the current sensor layouts, for
     *                 saving or storing with a trial.
     */
    void setLayoutSupplier(Supplier<List<GoosciSensorLayout.SensorLayout>> supplier);
}
