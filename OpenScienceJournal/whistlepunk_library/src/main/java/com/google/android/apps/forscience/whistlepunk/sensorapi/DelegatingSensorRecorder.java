package com.google.android.apps.forscience.whistlepunk.sensorapi;

public class DelegatingSensorRecorder implements SensorRecorder {
    private final SensorRecorder mDelegate;

    public DelegatingSensorRecorder(SensorRecorder delegate) {
        mDelegate = delegate;
    }

    public void startObserving() {
        mDelegate.startObserving();
    }

    public void startRecording(String runId) {
        mDelegate.startRecording(runId);
    }

    public void applyOptions(ReadableSensorOptions settings) {
        mDelegate.applyOptions(settings);
    }

    public void stopRecording() {
        mDelegate.stopRecording();
    }

    public void stopObserving() {
        mDelegate.stopObserving();
    }
}
