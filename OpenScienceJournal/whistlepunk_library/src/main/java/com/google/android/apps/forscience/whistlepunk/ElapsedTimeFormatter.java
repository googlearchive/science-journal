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

import android.content.Context;

/** Formats elapsed time (in seconds) to a Xmin Ys format, like 5min 11s. */
public class ElapsedTimeFormatter {

  private static final long SECS_IN_A_MIN = 60;
  private static ElapsedTimeFormatter instance;
  private final String shortFormat;
  private final String longFormat;
  private final String accessibleShortFormat;
  private final String accessibleLongFormat;

  public static ElapsedTimeFormatter getInstance(Context context) {
    if (instance == null) {
      instance = new ElapsedTimeFormatter(context.getApplicationContext());
    }
    return instance;
  }

  private ElapsedTimeFormatter(Context context) {
    shortFormat = context.getResources().getString(R.string.elapsed_time_short_format);
    longFormat = context.getResources().getString(R.string.elapsed_time_long_format);
    accessibleShortFormat =
        context.getResources().getString(R.string.accessible_elapsed_time_short_format);
    accessibleLongFormat =
        context.getResources().getString(R.string.accessible_elapsed_time_long_format);
  }

  public String format(long elapsedTime) {
    if (Math.abs(elapsedTime) >= SECS_IN_A_MIN) {
      return String.format(
          longFormat, elapsedTime / SECS_IN_A_MIN, Math.abs(elapsedTime % SECS_IN_A_MIN));
    } else {
      return String.format(shortFormat, elapsedTime);
    }
  }

  public String formatForAccessibility(long elapsedTime) {
    if (Math.abs(elapsedTime) >= SECS_IN_A_MIN) {
      return String.format(
          accessibleLongFormat, elapsedTime / SECS_IN_A_MIN, Math.abs(elapsedTime % SECS_IN_A_MIN));
    } else {
      return String.format(accessibleShortFormat, elapsedTime);
    }
  }
}
