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

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SensorBehaviorTest {
  @Test
  public void testRoundTripWithFrequency() {
    SensorBehavior sb = new SensorBehavior();

    sb.shouldShowSettingsOnConnect = Arbitrary.bool();
    sb.loggingId = Arbitrary.string();
    sb.expectedSamplesPerSecond = Arbitrary.singleFloat();

    Parcel parcel = Parcel.obtain();
    sb.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);
    SensorBehavior clone = SensorBehavior.CREATOR.createFromParcel(parcel);
    assertEquals(sb.shouldShowSettingsOnConnect, clone.shouldShowSettingsOnConnect);
    assertEquals(sb.loggingId, clone.loggingId);
    assertEquals(sb.expectedSamplesPerSecond, clone.expectedSamplesPerSecond, 0.001);
  }
}
