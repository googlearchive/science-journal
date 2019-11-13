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

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

/** A data refresher which publishes the last known data value when streaming. */
public class DataRefresher extends Refresher {
  private static final int SENSOR_REFRESH_RATE = 100; // Refresh rate in ms.

  protected StreamConsumer streamConsumer;
  protected boolean streaming = false;
  private double value;
  private Clock clock;

  public DataRefresher(Scheduler scheduler, Clock clock) {
    this(scheduler, clock, SENSOR_REFRESH_RATE);
  }

  public DataRefresher(Scheduler scheduler, Clock clock, int sensorRefreshRateMillis) {
    super(scheduler, Delay.millis(sensorRefreshRateMillis));
    this.clock = clock;
  }

  public void setStreamConsumer(StreamConsumer consumer) {
    this.streamConsumer = consumer;
  }

  public void startStreaming() {
    if (!streaming) {
      streaming = true;
      refresh();
    }
  }

  public void stopStreaming() {
    streaming = false;
  }

  public void setValue(double value) {
    if (value != this.value) {
      this.value = value;
      refresh();
    }
  }

  public double getValue(long now) {
    return value;
  }

  @Override
  protected boolean doRefresh() {
    if (streaming && streamConsumer != null) {
      long now = clock.getNow();
      streamConsumer.addData(now, getValue(now));
    }
    return streaming;
  }
}
