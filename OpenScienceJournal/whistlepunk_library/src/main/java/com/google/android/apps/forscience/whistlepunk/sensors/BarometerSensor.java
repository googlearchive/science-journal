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

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AvailableSensors;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

/** Class to get sensor data from the Pressure Sensor (barometer). */
public class BarometerSensor extends ScalarSensor {
  public static final String ID = "BarometerSensor";
  private SensorEventListener sensorEventListener;

  public BarometerSensor() {
    super(ID);
  }

  @Override
  protected SensorRecorder makeScalarControl(
      final StreamConsumer c,
      final SensorEnvironment environment,
      final Context context,
      final SensorStatusListener listener) {
    return new AbstractSensorRecorder() {
      @Override
      public void startObserving() {
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
        SensorManager sensorManager = getSensorManager(context);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (sensorEventListener != null) {
          getSensorManager(context).unregisterListener(sensorEventListener);
        }
        final Clock clock = environment.getDefaultClock();
        sensorEventListener =
            new SensorEventListener() {
              @Override
              public void onSensorChanged(SensorEvent event) {
                // values[0]: Atmospheric pressure in hPa (millibar).
                // 1 hPa == 1 millibar
                c.addData(clock.getNow(), event.values[0]);
              }

              @Override
              public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };
        sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_UI);
      }

      @Override
      public void stopObserving() {
        getSensorManager(context).unregisterListener(sensorEventListener);
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
      }
    };
  }

  public static boolean isBarometerSensorAvailable(AvailableSensors availableSensors) {
    return availableSensors.isSensorAvailable(Sensor.TYPE_PRESSURE);
  }
}
