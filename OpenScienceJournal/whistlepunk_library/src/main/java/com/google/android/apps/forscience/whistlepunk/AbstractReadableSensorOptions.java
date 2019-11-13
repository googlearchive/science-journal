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

import android.util.ArrayMap;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;

/**
 * Implements general behavior of abstracting primitive types from strings. Subclasses need only
 * define #getString and #getWrittenKeys
 */
public abstract class AbstractReadableSensorOptions implements ReadableSensorOptions {
  public static TransportableSensorOptions makeTransportable(ReadableSensorOptions fromThis) {
    ArrayMap<String, String> values = new ArrayMap<>();
    for (String key : fromThis.getWrittenKeys()) {
      values.put(key, fromThis.getString(key, null));
    }
    return new TransportableSensorOptions(values);
  }

  @Override
  public float getFloat(String key, float defaultValue) {
    String string = getString(key, null);
    if (string == null) {
      return defaultValue;
    }
    try {
      return Float.valueOf(string);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public int getInt(String key, int defaultValue) {
    String string = getString(key, null);
    if (string == null) {
      return defaultValue;
    }
    try {
      return Integer.valueOf(string);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public long getLong(String key, long defaultValue) {
    String string = getString(key, null);
    if (string == null) {
      return defaultValue;
    }
    try {
      return Long.valueOf(string);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public boolean getBoolean(String key, boolean defaultValue) {
    String string = getString(key, null);
    if (string == null) {
      return defaultValue;
    }
    return Boolean.valueOf(string);
  }
}
