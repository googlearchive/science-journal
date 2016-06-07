package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.os.Bundle;

/**
 * Writeable interface for sensor options.
 */
public interface WriteableSensorOptions {
    /**
     * @return an interface for reading the options from this bundle of options, without the
     * possibility of changing them
     */
    ReadableSensorOptions getReadOnly();

    /**
     *
     * @param key
     * @param value
     */
    void put(String key, String value);
}
