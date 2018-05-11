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
  private final String prefFile;
  private Context context;
  private ReadableSensorOptions readOnly =
      new AbstractReadableSensorOptions() {
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
    this.prefFile = prefFile;
    this.context = context;
  }

  @Override
  public WriteableSensorOptions load(FailureListener onFailures) {
    return new WriteableSensorOptions() {
      @Override
      public ReadableSensorOptions getReadOnly() {
        return readOnly;
      }

      @Override
      public void put(String key, String value) {
        getPrefs().edit().putString(key, value).apply();
      }
    };
  }

  private SharedPreferences getPrefs() {
    return context.getSharedPreferences(prefFile, Context.MODE_PRIVATE);
  }
}
