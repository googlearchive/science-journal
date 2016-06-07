package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;

import java.util.Collection;
import java.util.Map;

/**
 * Extends TransportableSensorOptions
 */
public class ReadableTransportableSensorOptions extends AbstractReadableSensorOptions {
    private final TransportableSensorOptions mTransportable;

    public ReadableTransportableSensorOptions(Map<String, String> values) {
        this(new TransportableSensorOptions(values));
    }

    public ReadableTransportableSensorOptions(TransportableSensorOptions transportable) {
        mTransportable = transportable;
    }

    @Override
    public String getString(String key, String defaultValue) {
        return mTransportable.getString(key, defaultValue);
    }

    @Override
    public Collection<String> getWrittenKeys() {
        return mTransportable.getWrittenKeys();
    }
}
