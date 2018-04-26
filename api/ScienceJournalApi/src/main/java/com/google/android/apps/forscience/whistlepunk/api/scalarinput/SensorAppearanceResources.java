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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.os.Parcel;
import android.os.Parcelable;

public class SensorAppearanceResources implements Parcelable {
  public int iconId = -1;
  public String units = "";
  public String shortDescription = "";

  public SensorAppearanceResources() {}

  // TODO(saff): test round-trip parcel!
  protected SensorAppearanceResources(Parcel in) {
    iconId = in.readInt();
    units = in.readString();
    shortDescription = in.readString();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(iconId);
    dest.writeString(units);
    dest.writeString(shortDescription);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<SensorAppearanceResources> CREATOR =
      new Creator<SensorAppearanceResources>() {
        @Override
        public SensorAppearanceResources createFromParcel(Parcel in) {
          return new SensorAppearanceResources(in);
        }

        @Override
        public SensorAppearanceResources[] newArray(int size) {
          return new SensorAppearanceResources[size];
        }
      };
}
