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

/**
 * Class to get scalar, linear data from the linear accelerometer sensor by combining acceleration
 * in all three axis. The force of gravity is excluded.
 */
public class LinearAccelerometerSensor extends ScalarSensor {
  public static final String ID = "LinearAccelerometerSensor";
  private SensorEventListener sensorEventListener;

  public LinearAccelerometerSensor() {
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
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (sensorEventListener != null) {
          getSensorManager(context).unregisterListener(sensorEventListener);
        }
        final Clock clock = environment.getDefaultClock();
        sensorEventListener =
            new SensorEventListener() {
              @Override
              public void onSensorChanged(SensorEvent event) {
                c.addData(
                    clock.getNow(),
                    Math.sqrt(
                        event.values[0] * event.values[0]
                            + event.values[1] * event.values[1]
                            + event.values[2] * event.values[2]));
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

  public static boolean isLinearAccelerometerAvailable(AvailableSensors availableSensors) {
    return availableSensors.isSensorAvailable(Sensor.TYPE_LINEAR_ACCELERATION);
  }
}
