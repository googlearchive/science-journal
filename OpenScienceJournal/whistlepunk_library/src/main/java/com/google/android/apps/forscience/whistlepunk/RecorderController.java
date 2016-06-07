package com.google.android.apps.forscience.whistlepunk;

import android.content.Intent;

import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;

import java.util.List;

public interface RecorderController extends ExternalSensorListener {
    /**
     * @return observerId: should be passed to stopObserving, so that this client only kills
     * observers that it creates.
     */
    String startObserving(String sensorId, SensorObserver observer,
            SensorStatusListener listener, TransportableSensorOptions initialOptions);

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


    void startRecording(Intent resumeIntent, RecordingMetadata recording);

    void stopRecording();

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
    }

    void addRecordingStateListener(String listenerId, RecordingStateListener listener);

    void removeRecordingStateListener(String listenerId);

    interface ObservedIdsListener {
        void onObservedIdsChanged(List<String> observedSensorIds);
    }

    void addObservedIdsListener(String listenerId, ObservedIdsListener listener);

    void removeObservedIdsListener(String listenerId);
}
