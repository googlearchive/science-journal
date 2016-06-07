package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.os.Bundle;

/**
 * Observes changes to a sensor, potentially serialized between processes.
 *
 * Assume all calls are on the main thread
 *
 * @param <BG_DATA> Parcelable type containing the sensor data.
 */
public interface SensorObserver {
    /**
     * Called when new data arrives.  Extender must copy or extract any values from {@code data}
     * that it wishes to use after returning; caller can re-use the same reference to reduce
     * allocations.
     */
    void onNewData(long timestamp, Bundle data);
}
