package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;

import java.util.HashMap;
import java.util.Map;

public class RecordingStatusListener implements SensorStatusListener {
    public Map<String, Integer> mostRecentStatuses = new HashMap<>();

    @Override
    public void onSourceStatus(String id, int status) {
        mostRecentStatuses.put(id, status);
    }

    @Override
    public void onSourceError(String id, int error, String errorMessage) {

    }
}
