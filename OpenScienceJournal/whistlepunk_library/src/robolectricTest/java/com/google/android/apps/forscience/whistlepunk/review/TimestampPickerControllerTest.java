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

package com.google.android.apps.forscience.whistlepunk.review;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.android.apps.forscience.whistlepunk.ElapsedTimeUtils;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the TimestampPickerController class. */
@RunWith(RobolectricTestRunner.class)
public class TimestampPickerControllerTest {

  TimestampPickerController.OnTimestampErrorListener errorListener =
      new TimestampPickerController.OnTimestampErrorListener() {
        @Override
        public void onTimestampError(int errorId) {
          // unused
        }
      };

  @Test
  public void testGetTimeString() {
    TimestampPickerController tpc =
        new TimestampPickerController(Locale.US, true, "-", ":", ":", errorListener);
    tpc.setTimestampRange(0, 10 * ElapsedTimeUtils.MS_IN_SEC, 0, 0);
    assertEquals("0:00:00.000", tpc.getTimeString());

    tpc.setTimestampRange(0, 10 * ElapsedTimeUtils.MS_IN_SEC, 0, ElapsedTimeUtils.MS_IN_SEC);
    assertEquals("0:00:01.000", tpc.getTimeString());

    tpc.setTimestampRange(0, 10 * ElapsedTimeUtils.MS_IN_SEC, 0, 1);
    assertEquals("0:00:00.001", tpc.getTimeString());

    // Negative 1 second -- cropping wider
    tpc.setTimestampRange(
        ElapsedTimeUtils.MS_IN_SEC,
        10 * ElapsedTimeUtils.MS_IN_SEC,
        0,
        -1 * ElapsedTimeUtils.MS_IN_SEC);
    assertEquals("-0:00:01.000", tpc.getTimeString());
  }

  @Test
  public void testParseTimeString() {
    TimestampPickerController tpc =
        new TimestampPickerController(Locale.getDefault(), true, "-", ":", ":", errorListener);
    tpc.setTimestampRange(0, 10 * ElapsedTimeUtils.MS_IN_SEC, 0, 0);
    tpc.updateSelectedTime("0:00:03.141");
    assertEquals(3141, tpc.getSelectedTime());

    tpc.updateSelectedTime("0:00:03.14");
    assertEquals(3140, tpc.getSelectedTime());

    // Zero time moves forward a second
    tpc.setTimestampRange(0, 10 * ElapsedTimeUtils.MS_IN_SEC, ElapsedTimeUtils.MS_IN_SEC, 0);
    tpc.updateSelectedTime("0:00:03.141");
    assertEquals(4141, tpc.getSelectedTime());

    // Try negative one second (should be 0 timestamp)
    tpc.updateSelectedTime("-0:00:01.000");
    assertEquals(0, tpc.getSelectedTime());
  }

  @Test
  public void testParseTimeStringOutsideRange() {
    TimestampPickerController tpc =
        new TimestampPickerController(Locale.getDefault(), true, "-", ":", ":", errorListener);
    tpc.setTimestampRange(0, 10 * ElapsedTimeUtils.MS_IN_SEC, 0, 0);
    int error = tpc.updateSelectedTime("1:00:00.000");
    assertNotEquals(error, TimestampPickerController.NO_ERROR);

    error = tpc.updateSelectedTime("-1:00:00.000");
    assertNotEquals(error, TimestampPickerController.NO_ERROR);
  }
}
