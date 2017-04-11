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

package com.google.android.apps.forscience.whistlepunk.wireapi;

import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.apps.forscience.whistlepunk.wireapi.IRecordingStateListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.wireapi.ISensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;

// Next method id = 12
interface IRecorderController {
    void startObserving(String sensorId, ISensorObserver observer,
            ISensorStatusListener failureListener, in TransportableSensorOptions initialOptions) = 0;

    void stopObserving(String sensorId) = 1;

    void pauseObservingAll() = 2;

    void resumeObservingAll() = 3;

    List<String> getObservedSensorIds() = 4;

    List<String> getMostRecentObservedSensorIds() = 11;

    void applyOptions(String sensorId, in TransportableSensorOptions settings) = 5;


    void startRecording(in Intent resumeIntent, in RecordingMetadata recording) = 6;

    void stopRecording() = 7;

    RecordingMetadata getCurrentRecording() = 8;

    void addRecordingStateListener(String listenerId, IRecordingStateListener failureListener) = 9;

    // listenerId must match a previous listenerId supplied to addRecordingStateListener, or this
    // call has no effect.
    void removeRecordingStateListener(String listenerId) = 10;
}
