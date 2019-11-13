/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

/** Utility classes to determine elapsed time. */
public class ElapsedTimeUtils {
  public static final long SEC_IN_MIN = 60;
  public static final long MIN_IN_HOUR = 60;
  public static final long TENTHS_IN_SEC = 10;
  public static final long MS_IN_SEC = 1000;

  /**
   * Gets the round number of hours in the elapsed time given.
   *
   * @param elapsedTime milliseconds of elapsed time
   * @return the largest number of hours that fits evenly into this time.
   */
  public static long getHours(long elapsedTime) {
    return elapsedTime / MS_IN_SEC / (SEC_IN_MIN * MIN_IN_HOUR);
  }

  /**
   * Gets the round number of minutes in the elapsed time given after subtracting out the hours.
   *
   * @param elapsedTime milliseconds of elapsed time
   * @param hours the largest number of hours that fits evenly into this time.
   * @return the largest number of minutes that fits evenly into this time.
   */
  public static long getMins(long elapsedTime, long hours) {
    return (elapsedTime / MS_IN_SEC - hours * SEC_IN_MIN * MIN_IN_HOUR) / SEC_IN_MIN;
  }

  /**
   * Gets the round number of seconds in the elapsed time given after subtracting out the minutes
   * and hours.
   *
   * @param elapsedTime milliseconds of elapsed time
   * @param hours the largest number of hours that fits evenly into this time.
   * @param mins the largest number of minutes that fits evenly into this time.
   * @return the largest number of seconds that fits evenly into this time.
   */
  public static long getSecs(long elapsedTime, long hours, long mins) {
    return elapsedTime / MS_IN_SEC - hours * SEC_IN_MIN * MIN_IN_HOUR - mins * SEC_IN_MIN;
  }

  /**
   * Gets the round number of tenths of secs in the elapsed time given after subtracting out the
   * seconds, minutes and hours.
   *
   * @param elapsedTime milliseconds of elapsed time
   * @param hours the largest number of hours that fits evenly into this time.
   * @param mins the largest number of minutes that fits evenly into this time.
   * @param secs the largest number of seconds that fits evenly into this time.
   * @return the largest number of tenths of seconds that fits evenly into this time.
   */
  public static long getTenthsOfSecs(long elapsedTime, long hours, long mins, long secs) {
    return elapsedTime * TENTHS_IN_SEC / MS_IN_SEC
        - hours * SEC_IN_MIN * MIN_IN_HOUR * TENTHS_IN_SEC
        - mins * SEC_IN_MIN * TENTHS_IN_SEC
        - secs * TENTHS_IN_SEC;
  }
}
