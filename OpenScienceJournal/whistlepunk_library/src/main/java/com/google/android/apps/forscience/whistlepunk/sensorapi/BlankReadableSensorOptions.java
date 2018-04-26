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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import java.util.Collection;
import java.util.Collections;

public class BlankReadableSensorOptions implements ReadableSensorOptions {
  public static BlankReadableSensorOptions INSTANCE = new BlankReadableSensorOptions();

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

  public static ReadableSensorOptions blankIfNull(ReadableSensorOptions options) {
    return options != null ? options : INSTANCE;
  }
}
