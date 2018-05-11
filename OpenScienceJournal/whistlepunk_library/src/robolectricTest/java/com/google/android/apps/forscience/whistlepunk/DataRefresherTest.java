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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.android.apps.forscience.javalib.DataRefresher;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the Refresher and the DataRefresher classes. */
@RunWith(RobolectricTestRunner.class)
public class DataRefresherTest {
  // A stream consumer that can return the most recent data and timestamp it received.
  private class MockStreamConsumer implements StreamConsumer {
    private long timestamp;
    private double value;
    private boolean dataAdded = false;

    @Override
    public boolean addData(long timestampMillis, double value) {
      timestamp = timestampMillis;
      this.value = value;
      dataAdded = true;
      return true;
    }

    public long getLastTimestamp() {
      return timestamp;
    }

    public double getLastValue() {
      return value;
    }

    private boolean dataAdded() {
      return dataAdded;
    }
  }

  private MockStreamConsumer streamConsumer = new MockStreamConsumer();
  private MockScheduler scheduler = new MockScheduler();

  @Test
  public void testDataRefresherDoesNotRefreshWhenNotStreaming() {
    DataRefresher dr = makeRefresher();
    dr.setStreamConsumer(streamConsumer);
    dr.setValue(2.718);
    assertFalse(streamConsumer.dataAdded());

    scheduler.incrementTime(100);

    assertFalse(streamConsumer.dataAdded());
    dr.stopStreaming();
    assertFalse(streamConsumer.dataAdded());

    scheduler.incrementTime(100);

    assertFalse(streamConsumer.dataAdded());
  }

  @Test
  public void testDataRefresherUsesUpdatedValue() {
    DataRefresher dr = makeRefresher();
    dr.setStreamConsumer(streamConsumer);
    assertFalse(streamConsumer.dataAdded());

    dr.setValue(3.14159);
    dr.startStreaming();
    assertEquals(3.14159, streamConsumer.getLastValue(), 0.001);

    dr.setValue(1.618);
    scheduler.incrementTime(100);

    assertEquals(1.618, streamConsumer.getLastValue(), 0.001);
  }

  @Test
  public void testDataRefresherGetsValueWithOverridableGetValueFunction() {
    DataRefresher dr =
        new DataRefresher(scheduler, scheduler.getClock()) {
          @Override
          public double getValue(long now) {
            return 42d;
          }
        };
    dr.setStreamConsumer(streamConsumer);
    dr.startStreaming();
    assertEquals(42d, streamConsumer.getLastValue(), 0.001);

    scheduler.incrementTime(100);

    assertEquals(42d, streamConsumer.getLastValue(), 0.001);
  }

  @Test
  public void testDataRefresherStopsUpdatingValuesOnStopStreaming() {
    DataRefresher dr = makeRefresher();
    dr.setStreamConsumer(streamConsumer);
    dr.setValue(255d);
    dr.startStreaming();

    assertEquals(255d, streamConsumer.getLastValue(), 0.001);

    dr.stopStreaming();
    dr.setValue(254d);
    scheduler.incrementTime(100);

    // The value should not have been updated because we are not streaming.
    assertEquals(255d, streamConsumer.getLastValue(), 0.001);
  }

  private DataRefresher makeRefresher() {
    return new DataRefresher(scheduler, scheduler.getClock());
  }
}
