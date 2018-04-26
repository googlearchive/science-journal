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

import com.google.android.apps.forscience.whistlepunk.ElapsedTimeUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Test for the elapsed time utils class. */
@RunWith(RobolectricTestRunner.class)
public class ElapsedTimeUtilsTest {
  private static final long MS_IN_HOUR =
      ElapsedTimeUtils.SEC_IN_MIN * ElapsedTimeUtils.MIN_IN_HOUR * ElapsedTimeUtils.MS_IN_SEC;

  @Test
  public void testGetHours() {
    int hours = 3;
    long ms = hours * MS_IN_HOUR;
    assertEquals(hours, ElapsedTimeUtils.getHours(ms));

    // Three hours, 1 ms
    ms += 1;
    assertEquals(hours, ElapsedTimeUtils.getHours(ms));

    // Three hours, 1 second, 1 ms
    ms += ElapsedTimeUtils.MS_IN_SEC;
    assertEquals(hours, ElapsedTimeUtils.getHours(ms));

    // Three hours, 1 minute, 1 second, 1 ms
    ms += ElapsedTimeUtils.MS_IN_SEC * 60;
    assertEquals(hours, ElapsedTimeUtils.getHours(ms));
  }

  @Test
  public void testGetMinutes() {
    int hours = 3;
    long ms = hours * MS_IN_HOUR;
    assertEquals(0, ElapsedTimeUtils.getMins(ms, hours));

    // Three hours, 1 ms
    ms += 1;
    assertEquals(hours, ElapsedTimeUtils.getHours(ms));
    assertEquals(0, ElapsedTimeUtils.getMins(ms, hours));

    // Three hours, 1 second, 1 ms
    ms += ElapsedTimeUtils.MS_IN_SEC;
    assertEquals(0, ElapsedTimeUtils.getMins(ms, hours));

    // Three hours, 1 minute, 1 second, 1 ms
    ms += ElapsedTimeUtils.MS_IN_SEC * 60;
    assertEquals(1, ElapsedTimeUtils.getMins(ms, hours));

    // Three hours, 2 minutes, 1 second, 1 ms
    ms += ElapsedTimeUtils.MS_IN_SEC * 60;
    assertEquals(2, ElapsedTimeUtils.getMins(ms, hours));
  }

  @Test
  public void testGetSecs() {
    int hours = 3;
    long ms = hours * MS_IN_HOUR;
    assertEquals(0, ElapsedTimeUtils.getSecs(ms, hours, 0));

    // Three hours, 1 ms
    ms += 1;
    assertEquals(hours, ElapsedTimeUtils.getHours(ms));
    assertEquals(0, ElapsedTimeUtils.getSecs(ms, hours, 0));

    // Three hours, 1 second, 1 ms
    ms += ElapsedTimeUtils.MS_IN_SEC;
    assertEquals(1, ElapsedTimeUtils.getSecs(ms, hours, 0));

    // Three hours, 1 minute, 1 second, 1 ms
    ms += ElapsedTimeUtils.MS_IN_SEC * 60;
    assertEquals(1, ElapsedTimeUtils.getSecs(ms, hours, 1));

    assertEquals(4, ElapsedTimeUtils.getSecs(4200, 0, 0));
  }
}
