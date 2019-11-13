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

import com.google.android.apps.forscience.whistlepunk.TimedEvent;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A reading from the database. Comparable based on timestamp only, to find a given timestamp in a
 * list of readings.
 */
public class ScalarReading implements TimedEvent, Comparable<ScalarReading> {
  public static final String SENSOR_TAG_UNDEFINED = "undefined";

  private final long timestampMillis;
  private final double value;
  private final String sensorTag;

  public ScalarReading(long timestampMillis, double value, String sensorTag) {
    this.timestampMillis = timestampMillis;
    this.value = value;
    this.sensorTag = sensorTag;
  }

  public ScalarReading(long timestampMillis, double value) {
    this(timestampMillis, value, SENSOR_TAG_UNDEFINED);
  }

  /** Warning: this can use a lot of memory. Prefer to maintain the ScalarReadingList */
  public static List<ScalarReading> slurp(ScalarReadingList list) {
    final List<ScalarReading> readings = new ArrayList<>();
    list.deliver(
        new StreamConsumer() {
          @Override
          public boolean addData(long timestampMillis, double value) {
            readings.add(new ScalarReading(timestampMillis, value));
            return true;
          }
        });
    return readings;
  }

  /** Only use this for testing. */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ScalarReading that = (ScalarReading) o;

    if (timestampMillis != that.timestampMillis) {
      return false;
    }
    if (!Objects.equals(sensorTag, that.sensorTag)) {
      return false;
    }
    return Double.compare(that.value, value) == 0;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = (int) (timestampMillis ^ (timestampMillis >>> 32));
    temp = Double.doubleToLongBits(value);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    result = 31 * result + sensorTag.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "ScalarReading{"
        + "mTimestampMillis="
        + timestampMillis
        + ", mValue="
        + value
        + ", mSensorTag="
        + sensorTag
        + '}';
  }

  @Override
  public long getCollectedTimeMillis() {
    return timestampMillis;
  }

  public double getValue() {
    return value;
  }

  public String getSensorTag() {
    return sensorTag;
  }

  @Override
  public int compareTo(ScalarReading another) {
    return Long.compare(timestampMillis, another.timestampMillis);
  }
}
