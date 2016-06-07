package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;

import java.util.Collection;
import java.util.Collections;

public class BlankReadableSensorOptions implements ReadableSensorOptions {
    @Override
    public float getFloat(String key, float defaultValue) {
        return defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return defaultValue;
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
        return null;
    }

    @Override
    public Collection<String> getWrittenKeys() {
        return Collections.emptyList();
    }
}
