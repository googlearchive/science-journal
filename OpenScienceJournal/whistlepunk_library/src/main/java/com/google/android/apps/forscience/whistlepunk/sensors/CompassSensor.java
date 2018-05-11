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

/** Class to create a compass sensor from the magnetic field and accelerometer. */
public class CompassSensor extends ScalarSensor {
  public static final String ID = "CompassSensor";
  private SensorEventListener sensorEventListener;

  public CompassSensor() {
    super(ID);
  }

  @Override
  protected SensorRecorder makeScalarControl(
      StreamConsumer c,
      SensorEnvironment environment,
      Context context,
      SensorStatusListener listener) {
    return new AbstractSensorRecorder() {
      @Override
      public void startObserving() {
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
        SensorManager sensorManager = getSensorManager(context);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensorEventListener != null) {
          getSensorManager(context).unregisterListener(sensorEventListener);
        }
        final Clock clock = environment.getDefaultClock();
        sensorEventListener =
            new SensorEventListener() {
              private float[] orientation = new float[3];
              private float[] magneticRotation;
              private float[] acceleration;
              private float[] rotation = new float[9];
              private float[] inclination = new float[9];

              @Override
              public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                  acceleration = event.values;
                } else {
                  magneticRotation = event.values;
                }
                // Update data as long as we have a value for both. This is the highest
                // rate of update.
                // If we want a slower rate, we can update when *both* values have changed,
                // or only when magneticRotation changes, for example.
                if (acceleration == null || magneticRotation == null) {
                  return;
                }
                boolean hasRotation =
                    SensorManager.getRotationMatrix(
                        rotation, inclination, acceleration, magneticRotation);
                if (hasRotation) {
                  SensorManager.getOrientation(rotation, orientation);
                  // Use a positive angle in degrees between 0 and 360.
                  c.addData(clock.getNow(), 360 - (360 - (Math.toDegrees(orientation[0]))) % 360);
                }
              }

              @Override
              public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };
        sensorManager.registerListener(
            sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(
            sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
      }

      @Override
      public void stopObserving() {
        getSensorManager(context).unregisterListener(sensorEventListener);
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
      }
    };
  }

  public static boolean isCompassSensorAvailable(AvailableSensors availableSensors) {
    return availableSensors.isSensorAvailable(Sensor.TYPE_ACCELEROMETER)
        && availableSensors.isSensorAvailable(Sensor.TYPE_MAGNETIC_FIELD);
  }
}
