package com.google.android.apps.forscience.whistlepunk;

import android.content.Intent;

import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;
import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MemoryRecorderController implements RecorderController {
    private Map<String, String> mCurrentObserverIds = new HashMap<>();
    private int mObserverCount = 0;

    @Override
    public String startObserving(String sensorId, SensorObserver observer,
            SensorStatusListener listener, TransportableSensorOptions initialOptions) {
        String observerId = String.valueOf(++mObserverCount);
        mCurrentObserverIds.put(sensorId, observerId);
        return observerId;
    }

    @Override
    public void stopObserving(String sensorId, String observerId) {
        if (mCurrentObserverIds.get(sensorId).equals(observerId)) {
            mCurrentObserverIds.remove(sensorId);
        }
    }

    @Override
    public String pauseObservingAll() {
        return null;
    }

    @Override
    public boolean resumeObservingAll(String pauseId) {
        return false;
    }

    @Override
    public void applyOptions(String sensorId, TransportableSensorOptions settings) {

    }

    @Override
    public void startRecording(Intent resumeIntent, RecordingMetadata recording) {

    }

    @Override
    public void stopRecording() {

    }

    @Override
    public List<String> getMostRecentObservedSensorIds() {
        return null;
    }

    @Override
    public void addRecordingStateListener(String listenerId,
            RecordingStateListener listener) {

    }

    @Override
    public void removeRecordingStateListener(String listenerId) {

    }

    @Override
    public void addObservedIdsListener(String listenerId, ObservedIdsListener listener) {
    }

    @Override
    public void removeObservedIdsListener(String listenerId) {

    }

    @Override
    public void replaceExternalSensors(Map<String, ExternalSensorSpec> sensors) {

    }

    public List<String> getCurrentObservedIds() {
        return Lists.newArrayList(mCurrentObserverIds.keySet());
    }
}
