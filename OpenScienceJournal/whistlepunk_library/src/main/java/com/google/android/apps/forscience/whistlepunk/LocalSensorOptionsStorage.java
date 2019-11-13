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

import androidx.annotation.NonNull;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;
import java.util.HashMap;
import java.util.Map;

/** Stores sensor options in a local map */
public class LocalSensorOptionsStorage implements NewOptionsStorage {
  public static final String TAG = "LocalSensorOptionsStrg";
  Map<String, String> values = new HashMap<>();
  private AbstractReadableSensorOptions readable = new ReadableTransportableSensorOptions(values);

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
        return readable;
      }

      @Override
      public void put(String key, String value) {
        values.put(key, value);
      }
    };
  }

  public static WriteableSensorOptions loadFromLayoutExtras(SensorLayoutPojo sensorLayout) {
    LocalSensorOptionsStorage options = new LocalSensorOptionsStorage();
    options.putAllExtras(sensorLayout.getExtras());
    return options.load(LoggingConsumer.expectSuccess(TAG, "loading sensor options"));
  }

  @NonNull
  public Map<String, String> exportAsLayoutExtras() {
    ReadableSensorOptions extras = load(null).getReadOnly();
    Map<String, String> map = new HashMap<>();
    for (String key : extras.getWrittenKeys()) {
      map.put(key, extras.getString(key, null));
    }
    return map;
  }

  public void putAllExtras(Map<String, String> extras) {
    WriteableSensorOptions options = load();
    for (Map.Entry<String, String> extra : extras.entrySet()) {
      options.put(extra.getKey(), extra.getValue());
    }
  }
}
