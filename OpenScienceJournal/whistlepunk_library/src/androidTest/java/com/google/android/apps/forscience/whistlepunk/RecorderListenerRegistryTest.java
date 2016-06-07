package com.google.android.apps.forscience.whistlepunk;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;

public class RecorderListenerRegistryTest extends AndroidTestCase {
    public void testImmediatelyUpdateStatus() {
        RecorderListenerRegistry r = new RecorderListenerRegistry();
        RecordingStatusListener beforeListener = new RecordingStatusListener();
        r.putListeners("sensorId", new RecordingSensorObserver(), beforeListener);
        assertFalse(beforeListener.mostRecentStatuses.containsKey("sensorId"));

        int status = Arbitrary.integer();
        r.onSourceStatus("sensorId", status);
        RecordingStatusListener afterListener = new RecordingStatusListener();
        r.putListeners("sensorId", new RecordingSensorObserver(), afterListener);
        assertEquals((Integer) status, afterListener.mostRecentStatuses.get("sensorId"));
    }

}