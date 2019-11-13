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
import com.google.android.apps.forscience.javalib.DataRefresher;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AvailableSensors;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

/**
 * Class to get sensor data from the Ambient Light sensor. This uses a DataRefresher to send the
 * data to deal with low frequency of SensorEvent. This sensor does not send onSensorChanged events
 * very frequently, leading to the graph being updated infrequently and then jumping when it does
 * get an update without a refresher.
 */
public class AmbientLightSensor extends ScalarSensor {
  public static final String ID = "AmbientLightSensor";
  private final SystemScheduler scheduler = new SystemScheduler();
  private SensorEventListener sensorEventListener;
  private DataRefresher dataRefresher;

  public AmbientLightSensor() {
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
        dataRefresher = new DataRefresher(scheduler, environment.getDefaultClock());
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
        SensorManager sensorManager = getSensorManager(context);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (sensorEventListener != null) {
          getSensorManager(context).unregisterListener(sensorEventListener);
        }
        sensorEventListener =
            new SensorEventListener() {
              @Override
              public void onSensorChanged(SensorEvent event) {
                // values[0] is the ambient light level in SI lux units.
                dataRefresher.setValue(event.values[0]);
                dataRefresher.startStreaming();
              }

              @Override
              public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };
        sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_UI);
        dataRefresher.setStreamConsumer(c);
      }

      @Override
      public void stopObserving() {
        getSensorManager(context).unregisterListener(sensorEventListener);
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
        if (dataRefresher != null) {
          dataRefresher.stopStreaming();
          dataRefresher = null;
        }
      }

      @Override
      public void applyOptions(ReadableSensorOptions settings) {
        // do nothing, no settings apply to collection
      }
    };
  }

  public static boolean isAmbientLightAvailable(AvailableSensors availableSensors) {
    return availableSensors.isSensorAvailable(Sensor.TYPE_LIGHT);
  }
}
