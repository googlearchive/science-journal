package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.preference.Preference;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

/**
 * One way of discovering additional sensors that can be added to an experiment
 */
public interface ExternalSensorDiscoverer {
    @NonNull
    ExternalSensorSpec extractSensorSpec(Preference preference);

    void onDestroy();

    boolean canScan();

    interface SensorPrefCallbacks {
        boolean isSensorAlreadyKnown(String key);

        void addAvailableSensorPreference(Preference newPref);
    }

    void startScanning(final SensorPrefCallbacks sensorPrefCallbacks);

    void stopScanning();
}
