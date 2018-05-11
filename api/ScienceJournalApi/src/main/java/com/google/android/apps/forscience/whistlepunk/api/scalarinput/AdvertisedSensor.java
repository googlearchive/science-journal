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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.Log;

/**
 * Sensor that is advertised through the API and connectable.
 *
 * <p>A sensor may be connected to multiple times, sometimes quickly cycling between {@link
 * #disconnect()} and {@link #connect()}, if the user is switching between multiple sensors.
 */
public abstract class AdvertisedSensor {
  private static final String TAG = "AdvertisedSensor";
  private final String address;
  private final String name;
  private ISensorStatusListener listener = null;

  protected AdvertisedSensor(String address, String name) {
    this.address = address;
    this.name = name;
  }

  /** Override to provide non-default behavior */
  protected SensorBehavior getBehavior() {
    return new SensorBehavior();
  }

  /** Override to specify sensor appearance */
  protected SensorAppearanceResources getAppearance() {
    return new SensorAppearanceResources();
  }

  /**
   * Connect to the sensor (for example, establish a BLE connection). Do _not_ start streaming data
   * yet.
   *
   * @return true if connection successful
   * @throws Exception if something goes wrong during connection that can be reported
   */
  protected boolean connect() throws Exception {
    return true;
  }

  public static interface DataConsumer {
    /** @return true iff there is anyone still interested in this data */
    public boolean isReceiving();

    /**
     * @param timestamp a timestamp (if none provided by an external device, use {@link
     *     System#currentTimeMillis()}.
     * @param value the sensor's current value
     */
    public void onNewData(long timestamp, double value);
  }

  /**
   * Stream data by calling {@link DataConsumer#onNewData(long, double)} as often as new data is
   * available, until {@link DataConsumer#isReceiving()} returns false.
   */
  protected abstract void streamData(DataConsumer c);

  /**
   * Stop streaming and clean up resources. When done, it should still be possible to call {@link
   * #connect()} again and reconnect.
   */
  protected abstract void disconnect();

  final void startObserving(final ISensorObserver observer, final ISensorStatusListener listener)
      throws RemoteException {
    listener.onSensorConnecting();
    try {
      if (!connect()) {
        listener.onSensorError("Could not connect");
        return;
      }
    } catch (Exception e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Connection error", e);
      }
      listener.onSensorError(e.getMessage());
      return;
    }
    listener.onSensorConnected();
    this.listener = listener;

    streamData(
        new DataConsumer() {
          @Override
          public boolean isReceiving() {
            return AdvertisedSensor.this.listener != null;
          }

          @Override
          public void onNewData(long timestamp, double value) {
            try {
              try {
                observer.onNewData(timestamp, value);
              } catch (DeadObjectException e) {
                reportError(e);
                stopObserving();
              }
            } catch (RemoteException e) {
              reportError(e);
            }
          }
        });
  }

  final void stopObserving() throws RemoteException {
    disconnect();
    if (listener != null) {
      listener.onSensorDisconnected();
      listener = null;
    }
  }

  String getAddress() {
    return address;
  }

  public String getName() {
    return name;
  }

  private void reportError(RemoteException e) {
    if (Log.isLoggable(TAG, Log.ERROR)) {
      Log.e(TAG, "error sending data", e);
    }
  }
}
