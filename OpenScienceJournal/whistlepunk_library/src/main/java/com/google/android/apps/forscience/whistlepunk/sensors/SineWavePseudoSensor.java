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
import com.google.android.apps.forscience.javalib.DataRefresher;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

public class SineWavePseudoSensor extends ScalarSensor {
  public static final String ID = "SINE_WAVE_X";
  public static final long DEFAULT_FREQENCY_MILLIS = 5000;
  public static final String PREFS_KEY_FREQUENCY_MILLIS = "prefs_frequency";
  private DataRefresher dataRefresher;

  public SineWavePseudoSensor() {
    // TODO(katie): Replace placeholder drawable with appropriate "unknown" sensor symbol.
    super(ID);
  }

  @Override
  protected SensorRecorder makeScalarControl(
      final StreamConsumer c,
      final SensorEnvironment environment,
      Context context,
      final SensorStatusListener listener) {
    return new AbstractSensorRecorder() {
      private long frequencyMillis = DEFAULT_FREQENCY_MILLIS;

      @Override
      public void startObserving() {
        dataRefresher =
            new DataRefresher(new SystemScheduler(), environment.getDefaultClock()) {
              @Override
              public double getValue(long now) {
                return computeValue(now);
              }
            };
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
        dataRefresher.setStreamConsumer(c);
        dataRefresher.startStreaming();
      }

      private double computeValue(long now) {
        final double value = Math.sin(Math.PI * 2 * now / frequencyMillis);
        return value;
      }

      @Override
      public void stopObserving() {
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
        if (dataRefresher != null) {
          dataRefresher.stopStreaming();
          dataRefresher = null;
        }
      }

      @Override
      public void applyOptions(ReadableSensorOptions settings) {
        frequencyMillis = settings.getLong(PREFS_KEY_FREQUENCY_MILLIS, DEFAULT_FREQENCY_MILLIS);
      }
    };
  }

  @Override
  protected SensorPresenter.OptionsPresenter createAdditionalScalarOptionsPresenter() {
    return new SineWaveOptionsPresenter();
  }
}
