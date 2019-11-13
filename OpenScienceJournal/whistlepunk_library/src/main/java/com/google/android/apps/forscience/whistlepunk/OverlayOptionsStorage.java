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

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashSet;

/** Combines two different options storage: the one provided first overrides the second one. */
public class OverlayOptionsStorage implements NewOptionsStorage {
  private final NewOptionsStorage topStorage;
  private final NewOptionsStorage backingStorage;

  public OverlayOptionsStorage(NewOptionsStorage topStorage, NewOptionsStorage backingStorage) {
    this.topStorage = topStorage;
    this.backingStorage = backingStorage;
  }

  @Override
  public WriteableSensorOptions load(FailureListener onFailures) {
    final WriteableSensorOptions top = topStorage.load(onFailures);
    final WriteableSensorOptions backing = backingStorage.load(onFailures);

    return new WriteableSensorOptions() {
      @Override
      public ReadableSensorOptions getReadOnly() {
        final ReadableSensorOptions topRead = top.getReadOnly();
        final ReadableSensorOptions backingRead = backing.getReadOnly();

        return new AbstractReadableSensorOptions() {
          @Override
          public String getString(String key, String defaultValue) {
            return topRead.getString(key, backingRead.getString(key, defaultValue));
          }

          @Override
          public Collection<String> getWrittenKeys() {
            HashSet<String> keys = Sets.newHashSet(topRead.getWrittenKeys());
            keys.addAll(backingRead.getWrittenKeys());
            return keys;
          }
        };
      }

      @Override
      public void put(String key, String value) {
        top.put(key, value);
      }
    };
  }
}
