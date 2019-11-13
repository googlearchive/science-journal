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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.os.Bundle;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;

/**
 * Observes changes to a sensor, potentially serialized between processes.
 *
 * <p>Assume all calls are on the main thread
 */
public interface SensorObserver {
  /**
   * Called when new data arrives. Extender must copy or extract any values from {@code data} that
   * it wishes to use after returning; caller can re-use the same reference to reduce allocations.
   */
  void onNewData(long timestamp, Data data);

  class Data {
    private double value;
    private boolean hasValidValue;

    // stats
    public double min;
    public double max;
    public double average;

    public void clear() {
      hasValidValue = false;
    }

    public boolean hasValidValue() {
      return hasValidValue;
    }

    public void setValue(double newValue) {
      value = newValue;
      hasValidValue = true;
    }

    public double getValue() {
      return value;
    }

    public Bundle asBundle() {
      // TODO: test, and optimize
      Bundle bundle = new Bundle();
      bundle.putDouble(ScalarSensor.BUNDLE_KEY_SENSOR_VALUE, value);
      bundle.putDouble(StatsAccumulator.KEY_MAX, max);
      bundle.putDouble(StatsAccumulator.KEY_MIN, min);
      bundle.putDouble(StatsAccumulator.KEY_AVERAGE, average);
      return bundle;
    }
  }
}
