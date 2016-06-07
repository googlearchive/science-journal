package com.google.android.apps.forscience.whistlepunk;

import android.os.RemoteException;

import com.google.android.apps.forscience.whistlepunk.wireapi.IRecordingStateListener;

import java.util.List;

class RecordingStateListener extends IRecordingStateListener.Stub {
    public Boolean recentIsRecording = null;
    public List<String> recentObservedSensorIds;

    @Override
    public void onRecordingStateChanged(boolean isRecording) throws RemoteException {
        recentIsRecording = isRecording;
    }

    @Override
    public void onObservedIdsChanged(List<String> observedSensorIds)
            throws RemoteException {
        recentObservedSensorIds = observedSensorIds;
    }
}
