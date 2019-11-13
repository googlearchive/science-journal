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
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.audio.AudioSource;
import com.google.android.apps.forscience.whistlepunk.audio.AudioSource.AudioReceiver;
import com.google.android.apps.forscience.whistlepunk.audio.SoundUtils;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

/**
 * Displays sound pressure levels in uncalibrated decibels (I have not tried to figure out the
 * 16-bit integer corresponding to 20 micro-Pascals, nor am I convinced it is the same from device
 * to device). Results should be comparable between readings on the same device, but not necessarily
 * between devices.
 */
public class DecibelSensor extends ScalarSensor {
  public static final String ID = "DecibelSource";

  public DecibelSensor() {
    super(ID);
  }

  @Override
  protected SensorRecorder makeScalarControl(
      final StreamConsumer c,
      SensorEnvironment environment,
      Context context,
      final SensorStatusListener listener) {
    final Clock clock = environment.getDefaultClock();
    final AudioSource audioSource = environment.getAudioSource();
    final AudioReceiver audioReceiver =
        buffer -> {
          long timestampMillis = clock.getNow();
          double uncalibratedDecibels =
              SoundUtils.calculateUncalibratedDecibels(buffer, buffer.length);
          if (isValidReading(uncalibratedDecibels)) {
            c.addData(timestampMillis, uncalibratedDecibels);
          }
        };
    return new AbstractSensorRecorder() {
      @Override
      public void startObserving() {
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
        if (!audioSource.registerAudioReceiver(audioReceiver)) {
          listener.onSourceError(
              getId(),
              SensorStatusListener.ERROR_FAILED_TO_CONNECT,
              "Could not connect to microphone");
        }
      }

      @Override
      public void stopObserving() {
        audioSource.unregisterAudioReceiver(audioReceiver);
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
      }

      @Override
      public void applyOptions(ReadableSensorOptions settings) {
        // do nothing, no settings apply to collection
      }
    };
  }

  private boolean isValidReading(double reading) {
    // Some devices (mostly Samsung) seem to spit out some -Double.MAX_VALUE points when the
    // mic starts which mess up graphing/audio due to an absurd yMin so we drop them since they
    // are bad data anyways.
    return reading > -Double.MAX_VALUE;
  }
}
