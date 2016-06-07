package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;

public interface SensorAppearanceProvider {
    /**
     * Loads appearances from storage.
     */
    void loadAppearances(MaybeConsumer<Success> onSuccess);
    SensorAppearance getAppearance(String sensorId);
}
