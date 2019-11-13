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

import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;

public class SensorBehavior implements Parcelable {
  /**
   * A PendingIntent that the Science Journal app can send to open an activity that will allow the
   * user to change settings for this particular sensor. Be sure that different sensors issue
   * different settingsIntents (simply changing the extras is not enough, see the javadoc for
   * PendingIntent.)
   */
  public PendingIntent settingsIntent = null;

  /**
   * If your sensor can be connected to meaningfully without user input, set this to false, even if
   * you have a settings screen. For example, a thermometer can default to Celsius, and users can
   * choose to open the settings screen to switch to Fahrenheit.
   *
   * <p>However, if there is no sensible default behavior (for example, this is a pH meter that
   * emits nonsense until calibrated), leave this true.
   */
  public boolean shouldShowSettingsOnConnect = true;

  /**
   * An id that will be used to log usage and problem reports. All reports will already contain your
   * service's package name. The loggingId might contain additional information about the _type_ of
   * sensor being used ("thermometer"), but should not contain anything that would allow individual
   * users to be identified (no user names or physical or virtual addresses of any kind)
   */
  public String loggingId = "";

  /**
   * This is a hint to Science Journal of how many values per second the sensor plans to publish.
   * This can help in rendering either very low- or high-throughput sensors.
   */
  public float expectedSamplesPerSecond = 10.0f;

  public SensorBehavior() {}

  protected SensorBehavior(Parcel in) {
    settingsIntent = in.readParcelable(PendingIntent.class.getClassLoader());
    byte b = in.readByte();
    shouldShowSettingsOnConnect = (b != 0);
    loggingId = in.readString();
    expectedSamplesPerSecond = in.readFloat();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(settingsIntent, 0);
    dest.writeByte((byte) (shouldShowSettingsOnConnect ? 1 : 0));
    dest.writeString(loggingId);
    dest.writeFloat(expectedSamplesPerSecond);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<SensorBehavior> CREATOR =
      new Creator<SensorBehavior>() {
        @Override
        public SensorBehavior createFromParcel(Parcel in) {
          return new SensorBehavior(in);
        }

        @Override
        public SensorBehavior[] newArray(int size) {
          return new SensorBehavior[size];
        }
      };
}
