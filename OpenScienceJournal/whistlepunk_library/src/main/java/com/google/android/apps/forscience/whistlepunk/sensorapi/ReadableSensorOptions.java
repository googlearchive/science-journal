package com.google.android.apps.forscience.whistlepunk.sensorapi;

import java.util.Collection;

/**
 * Sensor options, read-only.
 */
public interface ReadableSensorOptions {
    float getFloat(String key, float defaultValue);
    int getInt(String key, int defaultValue);
    long getLong(String key, long defaultValue);
    boolean getBoolean(String key, boolean defaultValue);
    String getString(String key, String defaultValue);

    /**
     * @return all the keys in this options bundle that may have non-default values
     */
    Collection<String> getWrittenKeys();
}
