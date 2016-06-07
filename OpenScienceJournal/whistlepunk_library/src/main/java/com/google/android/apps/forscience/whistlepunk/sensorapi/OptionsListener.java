package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.os.Bundle;

/**
 * Interface for an object that can receive new options
 */
public interface OptionsListener {
    /**
     * Apply user-set options that affect data collection and storage (for example, may
     * include sample frequency, or a pin number for an Arduino-based remote sensor)
     * @param settings
     */
    void applyOptions(ReadableSensorOptions settings);
}
