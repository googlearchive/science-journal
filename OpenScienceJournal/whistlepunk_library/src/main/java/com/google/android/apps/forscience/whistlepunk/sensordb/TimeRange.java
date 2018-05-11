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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.common.collect.Range;

public class TimeRange {
  // TODO(saff): share code with Weather app?

  public enum ObservationOrder {
    NEWEST_FIRST,
    OLDEST_FIRST
  }

  public static final TimeRange NOW = newest(null);

  public static TimeRange newest(Range<Long> times) {
    return new TimeRange(times, ObservationOrder.NEWEST_FIRST);
  }

  public static TimeRange oldest(Range<Long> times) {
    return new TimeRange(times, ObservationOrder.OLDEST_FIRST);
  }

  private Range<Long> times;
  private ObservationOrder order;

  /**
   * @param times inclusive range of timestamps of interest. May be Range.all() or null in order to
   *     get the most recent observations overall.
   */
  private TimeRange(Range<Long> times, ObservationOrder order) {
    this.times = times;
    this.order = order;
  }

  public Range<Long> getTimes() {
    return times;
  }

  public ObservationOrder getOrder() {
    return order;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final TimeRange that = (TimeRange) o;

    if (order != that.order) {
      return false;
    }
    return times.equals(that.times);
  }

  @Override
  public int hashCode() {
    int result = times.hashCode();
    result = 31 * result + order.hashCode();
    return result;
  }
}
