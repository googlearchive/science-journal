package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;

import java.util.Collection;

/**
 * Simple implementation of OptionsStorage, which stores Strings and primitives in a
 * SharedPreferences file.
 */
public class PrefsNewOptionsStorage implements NewOptionsStorage {
    private final String mPrefFile;
    private Context mContext;
    private ReadableSensorOptions mReadOnly = new AbstractReadableSensorOptions() {
        @Override
        public String getString(String key, String defaultValue) {
            return getPrefs().getString(key, defaultValue);
        }

        @Override
        public Collection<String> getWrittenKeys() {
            return getPrefs().getAll().keySet();
        }
    };

    public PrefsNewOptionsStorage(String prefFile, Context context) {
        mPrefFile = prefFile;
        mContext = context;
    }

    @Override
    public WriteableSensorOptions load(FailureListener onFailures) {
        return new WriteableSensorOptions() {
            @Override
            public ReadableSensorOptions getReadOnly() {
                return mReadOnly;
            }

            @Override
            public void put(String key, String value) {
                getPrefs().edit().putString(key, value).apply();
            }
        };
    }

    private SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(mPrefFile, Context.MODE_PRIVATE);
    }
}
