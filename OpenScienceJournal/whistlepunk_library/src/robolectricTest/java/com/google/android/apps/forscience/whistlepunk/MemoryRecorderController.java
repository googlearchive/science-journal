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

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSnapshotValue;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;

class MemoryRecorderController implements RecorderController {
    private Map<String, String> mCurrentObserverIds = new HashMap<>();
    private int mObserverCount = 0;

    @Override
    public String startObserving(String sensorId, List<SensorTrigger> activeTriggers,
            SensorObserver observer, SensorStatusListener listener,
            TransportableSensorOptions initialOptions, SensorRegistry sensorRegistry) {
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
    public Completable startRecording(Intent resumeIntent, boolean userInitiated) {
        return Completable.complete();
    }

    @Override
    public Completable stopRecording(final SensorRegistry sensorRegistry) {
        return Completable.complete();
    }

    @Override
    public void reboot(String sensorId) {

    }

    @Override
    public Single<GoosciSnapshotValue.SnapshotLabelValue> generateSnapshotLabelValue(
            List<String> sensorIds, SensorRegistry sensorRegistry) {
        return null;
    }

    @Override
    public void stopRecordingWithoutSaving() {

    }

    @Override
    public List<String> getMostRecentObservedSensorIds() {
        return null;
    }

    @Override
    public Observable<RecordingStatus> watchRecordingStatus() {
        return null;
    }

    @Override
    public int addTriggerFiredListener(TriggerFiredListener listener) {
        return 0;
    }

    @Override
    public void removeTriggerFiredListener(int listenerId) {

    }

    @Override
    public void addObservedIdsListener(String listenerId, ObservedIdsListener listener) {
    }

    @Override
    public void removeObservedIdsListener(String listenerId) {

    }

    @Override
    public void setSelectedExperiment(Experiment experiment) {

    }

    @Override
    public void setLayoutSupplier(Supplier<List<GoosciSensorLayout.SensorLayout>> supplier) {

    }

    @Override
    public Experiment getSelectedExperiment() {
        return null;
    }

    @Override
    public long getNow() {
        return 0;
    }

    @Override
    public void setRecordActivityInForeground(boolean isInForeground) {

    }

    @Override
    public void clearSensorTriggers(String sensorId, SensorRegistry sensorRegistry) {

    }

    public List<String> getCurrentObservedIds() {
        return Lists.newArrayList(mCurrentObserverIds.keySet());
    }
}
