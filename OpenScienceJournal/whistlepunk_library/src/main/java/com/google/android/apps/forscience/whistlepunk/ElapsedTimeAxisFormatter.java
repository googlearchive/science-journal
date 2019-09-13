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
import android.content.res.Resources;
import android.util.LruCache;

/**
 * Formats elapsed time (in ms) to a short m:ss or h:mm:ss format. Can also format to tenths of a
 * second using formatToTenths.
 */
// TODO: Switch to using JodaTime library or DateUtils.formatElapsedTime.
public class ElapsedTimeAxisFormatter {

  private static ElapsedTimeAxisFormatter instance;
  private final String extraSmallFormat;
  private final String smallFormat;
  private final String largeFormat;
  private final String smallFormatTenths;
  private final String largeFormatTenths;
  private LruCache<Long, String> cacheWithTenths;
  private LruCache<Long, String> cacheWithoutTenths;

  private final ReusableFormatter formatter;

  public static ElapsedTimeAxisFormatter getInstance(Context context) {
    if (instance == null) {
      instance = new ElapsedTimeAxisFormatter(context);
    }
    return instance;
  }

  private ElapsedTimeAxisFormatter(Context context) {
    Resources res = context.getResources();
    extraSmallFormat = res.getString(R.string.elapsed_time_axis_format_extra_small);
    smallFormat = res.getString(R.string.elapsed_time_axis_format_small);
    largeFormat = res.getString(R.string.elapsed_time_axis_format_large);
    smallFormatTenths = res.getString(R.string.elapsed_time_axis_format_small_tenths);
    largeFormatTenths = res.getString(R.string.elapsed_time_axis_format_large_tenths);

    formatter = new ReusableFormatter();

    cacheWithTenths = new LruCache<>(128 /* entries */);
    cacheWithoutTenths = new LruCache<>(128 /* entries */);
  }

  /**
   * Returns a formatted string for elapsedTimeMs using a cache, if a miss occurs, then compute the
   * formatted string and add it to the cache
   *
   * @param elapsedTimeMs Timestamp to be formatted
   * @param includeTenths Whether to include the tenths place in the formatted string
   * @return The formatted string in HH:MM:SS or HH:MM:SS.T
   */
  public String format(long elapsedTimeMs, boolean includeTenths) {
    long absoluteElapsedTimeMs = Math.abs(elapsedTimeMs);
    // Key into the cache based on timestamp with
    // reduced precision to increase hit likelihood
    long timeIndex;
    String formattedString;
    if (includeTenths) {
      timeIndex = elapsedTimeMs / 100;
      formattedString = cacheWithTenths.get(timeIndex);
    } else {
      timeIndex = elapsedTimeMs / 1000;
      formattedString = cacheWithoutTenths.get(timeIndex);
    }

    // Cache hit
    if (formattedString != null) {
      return formattedString;
    }

    long hours = ElapsedTimeUtils.getHours(absoluteElapsedTimeMs);
    long minutes = ElapsedTimeUtils.getMins(absoluteElapsedTimeMs, hours);
    long seconds = ElapsedTimeUtils.getSecs(absoluteElapsedTimeMs, hours, minutes);
    if (includeTenths) {
      long tenths =
          ElapsedTimeUtils.getTenthsOfSecs(absoluteElapsedTimeMs, hours, minutes, seconds);
      if (hours > 0) {
        formattedString =
            formatter.format(largeFormatTenths, hours, minutes, seconds, tenths).toString();
      } else {
        formattedString = formatter.format(smallFormatTenths, minutes, seconds, tenths).toString();
      }
    } else {
      if (hours > 0) {
        formattedString = formatter.format(largeFormat, hours, minutes, seconds).toString();
      } else if (minutes > 0) {
        formattedString = formatter.format(smallFormat, minutes, seconds).toString();
      } else {
        formattedString = formatter.format(extraSmallFormat, seconds).toString();
      }
    }

    boolean isNegative = elapsedTimeMs < 0;
    if (isNegative) {
      formattedString = formatter.format("-%s", formattedString).toString();
    }

    if (includeTenths) {
      cacheWithTenths.put(timeIndex, formattedString);
    } else {
      cacheWithoutTenths.put(timeIndex, formattedString);
    }

    return formattedString;
  }
}
