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
package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.javalib.Consumer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles all the information that needs to be passed from a background sensor thread to the
 * foreground UI thread for store and display.
 *
 * <p>SensorMessages are mutable, and are pooled to reduce allocations. The use pattern is:
 *
 * <pre>
 *     SensorObserver observer = [observer that will receive data on foreground thread]
 *
 *     // Create a pool
 *     SensorMessage.Pool pool = new SensorMessage.Pool(observer);
 *
 *     // When new data comes in, obtain a message and populate it
 *     // obtain() will reuse an idle old message if one is available.
 *     SensorMessage message = mMessagePool.obtain();
 *
 *     // populate data
 *     message.setTimestamp(timestampMillis);
 *     message.getData().putString(key, value);
 *     // ... any other data to set
 *
 *     // Now, run the runnable on the UI thread to
 *     // (a) push data to the observer, and
 *     // (b) release the message for reuse
 *     handler.post(message.getRunnable());
 * </pre>
 */
public class SensorMessage {
  private final Runnable runnable;
  private long timestamp = -1;

  private SensorObserver.Data data = new SensorObserver.Data();

  private SensorMessage(final Consumer<SensorMessage> onNewData) {
    runnable =
        new Runnable() {
          @Override
          public void run() {
            onNewData.take(SensorMessage.this);
          }
        };
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public long getTimestamp() {
    return timestamp;
  }

  /**
   * The bundle returned here is mutable. Add data to it to be delivered to the observer. When
   * getRunnable() is run, the data will be delivered, and then cleared out for reuse.
   */
  public SensorObserver.Data getData() {
    return data;
  }

  /**
   * @return a runnable that will deliver this data to the observer, and then release this message
   *     for reuse.
   */
  public Runnable getRunnable() {
    return runnable;
  }

  /** A pool of reusable SensorMessages. */
  public static class Pool {
    private final Consumer<SensorMessage> onNewData;
    private ConcurrentLinkedQueue<SensorMessage> queue = new ConcurrentLinkedQueue<>();

    /** Creates a pool of messages that will deliver data to {@code observer} */
    public Pool(final SensorObserver observer) {
      onNewData =
          new Consumer<SensorMessage>() {
            @Override
            public void take(SensorMessage sensorMessage) {
              observer.onNewData(sensorMessage.getTimestamp(), sensorMessage.getData());
              release(sensorMessage);
            }
          };
    }

    /** @return a reused message if any are available, or a new one if necessary. */
    public SensorMessage obtain() {
      SensorMessage obtained = queue.poll();
      if (obtained == null) {
        return new SensorMessage(onNewData);
      }
      return obtained;
    }

    private void release(SensorMessage released) {
      released.data.clear();
      released.timestamp = -1;
      queue.add(released);
    }
  }
}
