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

package com.google.android.apps.forscience.whistlepunk.wireapi;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;
import java.util.Collection;
import java.util.Map;

public class TransportableSensorOptions implements Parcelable {
  private Map<String, String> values;

  public TransportableSensorOptions(Map<String, String> values) {
    this.values = values;
  }

  protected TransportableSensorOptions(Parcel in) {
    values = new ArrayMap<>();
    in.readMap(values, getClass().getClassLoader());
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeMap(values);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<TransportableSensorOptions> CREATOR =
      new Creator<TransportableSensorOptions>() {
        @Override
        public TransportableSensorOptions createFromParcel(Parcel in) {
          return new TransportableSensorOptions(in);
        }

        @Override
        public TransportableSensorOptions[] newArray(int size) {
          return new TransportableSensorOptions[size];
        }
      };

  public String getString(String key, String defaultValue) {
    if (values.containsKey(key)) {
      return values.get(key);
    } else {
      return defaultValue;
    }
  }

  public Collection<String> getWrittenKeys() {
    return values.keySet();
  }
}
