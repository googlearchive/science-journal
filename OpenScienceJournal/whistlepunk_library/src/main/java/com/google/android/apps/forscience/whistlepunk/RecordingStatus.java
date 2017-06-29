/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

public class RecordingStatus {
    public static final RecordingStatus UNCONNECTED =
            new RecordingStatus(RecordingState.UNCONNECTED, null);
    public final RecordingState state;
    public final RecordingMetadata currentRecording;

    private RecordingStatus(RecordingState state, RecordingMetadata metadata) {
        this.state = state;
        this.currentRecording = metadata;
    }

    public boolean isRecording() {
        return currentRecording != null;
    }

    public String getCurrentRunId() {
        return isRecording() ? currentRecording.getRunId() : RecorderController
                .NOT_RECORDING_RUN_ID;
    }

    public RecordingStatus withRecording(RecordingMetadata newRecording) {
        return new RecordingStatus(state, newRecording);
    }

    public RecordingStatus withState(RecordingState newState) {
        return new RecordingStatus(newState, currentRecording);
    }

    public RecordingStatus inStableRecordingState() {
        return isRecording() ? withState(RecordingState.ACTIVE) : withState(
                RecordingState.INACTIVE);
    }

    public long getRecordingStartTime() {
        return RecordingMetadata.getStartTime(currentRecording);
    }

    @Override
    public String toString() {
        return "RecordingStatus{" +
               "state=" + state +
               ", currentRecording=" + currentRecording +
               '}';
    }
}
