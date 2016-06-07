package com.google.android.apps.forscience.whistlepunk.wireapi;

interface IRecordingStateListener {
    void onRecordingStateChanged(boolean isRecording) = 0;

    void onObservedIdsChanged(in List<String> observedSensorIds) = 1;
}
