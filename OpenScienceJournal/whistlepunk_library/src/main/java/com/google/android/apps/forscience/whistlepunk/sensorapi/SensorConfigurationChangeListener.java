package com.google.android.apps.forscience.whistlepunk.sensorapi;

/**
 * An object listening for sensor configuration change events.
 */
public interface SensorConfigurationChangeListener {


    /**
     * Called when the configuration of the sensor changes.
     */
    void onConfigurationChange();
}
