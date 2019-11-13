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

package com.google.android.apps.forscience.javalib;

import java.util.concurrent.TimeUnit;

public class Delay {
  public static final Delay ZERO = millis(0);

  private final long delay;
  private final TimeUnit unit;

  public static Delay seconds(long secs) {
    return new Delay(secs, TimeUnit.SECONDS);
  }

  public static Delay millis(long millis) {
    return new Delay(millis, TimeUnit.MILLISECONDS);
  }

  public static Delay micros(int micros) {
    return new Delay(micros, TimeUnit.MICROSECONDS);
  }

  private Delay(long delay, TimeUnit unit) {
    this.delay = delay;
    this.unit = unit;
  }

  public long getDelay() {
    return delay;
  }

  public TimeUnit getUnit() {
    return unit;
  }

  @Override
  public String toString() {
    return "Delay{" + delay + " " + unit + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Delay delay = (Delay) o;

    if (this.delay != delay.delay) {
      return false;
    }
    return unit == delay.unit;
  }

  @Override
  public int hashCode() {
    int result = (int) (delay ^ (delay >>> 32));
    result = 31 * result + unit.hashCode();
    return result;
  }

  public long asMillis() {
    return TimeUnit.MILLISECONDS.convert(delay, unit);
  }

  public boolean isZero() {
    return delay == 0;
  }
}
