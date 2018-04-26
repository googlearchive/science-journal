/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;
import com.google.common.collect.Sets;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class PrefsNewOptionsStorageTest {
  protected static final String PREF_FILE = "testPrefs";

  @After
  public void tearDown() throws Exception {
    final SharedPreferences prefs = getSharedPreferences();
    prefs.edit().clear().apply();
  }

  @Test
  public void testRoundTripString() {
    final String key = Arbitrary.string();
    final String value = Arbitrary.string();
    load().put(key, value);
    assertEquals(value, load().getReadOnly().getString(key, null));
  }

  @Test
  public void testRoundTripFloat() {
    final String key = Arbitrary.string();
    final float value = Arbitrary.singleFloat();
    load().put(key, String.valueOf(value));
    assertEquals(value, load().getReadOnly().getFloat(key, 0), 0.01);
  }

  @Test
  public void testRoundTripLong() {
    final String key = Arbitrary.string();
    final long value = Arbitrary.longInteger();
    load().put(key, String.valueOf(value));
    assertEquals(value, load().getReadOnly().getLong(key, 0));
  }

  @Test
  public void testRoundTripBoolean() {
    final String key = Arbitrary.string();
    final boolean value = Arbitrary.bool();
    load().put(key, String.valueOf(value));
    assertEquals(value, load().getReadOnly().getBoolean(key, !value));
  }

  @Test
  public void testRoundTripInt() {
    final String key = Arbitrary.string();
    final int value = Arbitrary.integer();
    load().put(key, String.valueOf(value));
    assertEquals(value, load().getReadOnly().getInt(key, 0));
  }

  @Test
  public void testNonDefaultKeys() {
    List<String> keys = Arbitrary.distinctStrings(2);
    load().put(keys.get(0), "1");
    load().put(keys.get(1), "2");
    assertEquals(
        Sets.newHashSet(keys), Sets.<String>newHashSet(load().getReadOnly().getWrittenKeys()));
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

  private Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }
}
