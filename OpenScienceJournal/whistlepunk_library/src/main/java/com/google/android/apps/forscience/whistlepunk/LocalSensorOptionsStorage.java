package com.google.android.apps.forscience.whistlepunk;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Stores sensor options in a local map
 */
public class LocalSensorOptionsStorage implements NewOptionsStorage {
    public static final String TAG = "LocalSensorOptionsStrg";
    Map<String, String> mValues = new HashMap<>();
    private AbstractReadableSensorOptions mReadable = new ReadableTransportableSensorOptions(
            mValues);

    @Override
    public WriteableSensorOptions load(FailureListener onFailures) {
        // can't fail to read from local HashMap
        return load();
    }

    @NonNull
    public WriteableSensorOptions load() {
        return new WriteableSensorOptions() {
            @Override
            public ReadableSensorOptions getReadOnly() {
                return mReadable;
            }

            @Override
            public void put(String key, String value) {
                mValues.put(key, value);
            }
        };
    }

    public static WriteableSensorOptions loadFromLayoutExtras(
            GoosciSensorLayout.SensorLayout sensorLayout) {
        LocalSensorOptionsStorage options = new LocalSensorOptionsStorage();
        options.putAllExtras(sensorLayout.extras);
        return options.load(LoggingConsumer.expectSuccess(TAG, "loading sensor options"));
    }

    @NonNull
    GoosciSensorLayout.SensorLayout.ExtrasEntry[] exportAsLayoutExtras() {
        ReadableSensorOptions extras = load(null).getReadOnly();
        Collection<String> keys = extras.getWrittenKeys();
        GoosciSensorLayout.SensorLayout.ExtrasEntry[] entries = new GoosciSensorLayout
                .SensorLayout.ExtrasEntry[keys.size()];
        Iterator<String> keysIter = keys.iterator();
        for (int i = 0; i < keys.size(); i++) {
            entries[i] = new GoosciSensorLayout.SensorLayout.ExtrasEntry();
            String key = keysIter.next();
            entries[i].key = key;
            entries[i].value = extras.getString(key, null);
        }
        return entries;
    }

    public void putAllExtras(GoosciSensorLayout.SensorLayout.ExtrasEntry[] extras) {
        WriteableSensorOptions options = load();
        for (GoosciSensorLayout.SensorLayout.ExtrasEntry extra : extras) {
            options.put(extra.key, extra.value);
        }
    }
}
