package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;
import com.google.common.collect.Sets;

import java.util.List;

public class PrefsNewOptionsStorageTest extends AndroidTestCase {
    protected static final String PREF_FILE = "testPrefs";

    @Override
    protected void tearDown() throws Exception {
        final SharedPreferences prefs = getSharedPreferences();
        prefs.edit().clear().apply();
        super.tearDown();
    }

    public void testRoundTripString() {
        final String key = Arbitrary.string();
        final String value = Arbitrary.string();
        load().put(key, value);
        assertEquals(value, load().getReadOnly().getString(key, null));
    }

    public void testRoundTripFloat() {
        final String key = Arbitrary.string();
        final float value = Arbitrary.singleFloat();
        load().put(key, String.valueOf(value));
        assertEquals(value, load().getReadOnly().getFloat(key, 0));
    }

    public void testRoundTripLong() {
        final String key = Arbitrary.string();
        final long value = Arbitrary.longInteger();
        load().put(key, String.valueOf(value));
        assertEquals(value, load().getReadOnly().getLong(key, 0));
    }

    public void testRoundTripBoolean() {
        final String key = Arbitrary.string();
        final boolean value = Arbitrary.bool();
        load().put(key, String.valueOf(value));
        assertEquals(value, load().getReadOnly().getBoolean(key, !value));
    }

    public void testRoundTripInt() {
        final String key = Arbitrary.string();
        final int value = Arbitrary.integer();
        load().put(key, String.valueOf(value));
        assertEquals(value, load().getReadOnly().getInt(key, 0));
    }

    public void testNonDefaultKeys() {
        List<String> keys = Arbitrary.distinctStrings(2);
        load().put(keys.get(0), "1");
        load().put(keys.get(1), "2");
        assertEquals(Sets.newHashSet(keys),
                Sets.<String>newHashSet(load().getReadOnly().getWrittenKeys()));
    }

    private WriteableSensorOptions load() {
        return makeNewStorage().load(new StoringConsumer<>());
    }

    @NonNull
    private NewOptionsStorage makeNewStorage() {
        return new PrefsNewOptionsStorage(PREF_FILE, getContext());
    }

    private SharedPreferences getSharedPreferences() {
        return getContext().getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }
}
