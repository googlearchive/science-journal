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

import static com.google.android.apps.forscience.whistlepunk.audio.AudioSource.SAMPLE_RATE_IN_HZ;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.audio.AudioAnalyzer;
import com.google.android.apps.forscience.whistlepunk.audio.AudioSource;
import com.google.android.apps.forscience.whistlepunk.audio.AudioSource.AudioReceiver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

/** A sensor that displays the pitch in Hertz (Hz). */
public class PitchSensor extends ScalarSensor {
  public static final String ID = "PitchSensor";

  public PitchSensor() {
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
        new AudioReceiver() {
          private final AudioAnalyzer audioAnalyzer = new AudioAnalyzer(SAMPLE_RATE_IN_HZ);
          private final short[] audioAnalyzerBuffer = new short[AudioAnalyzer.BUFFER_SIZE];
          private int audioAnalyzerBufferOffset;
          private Double previousFrequency;

          @Override
          public void onReceiveAudio(short[] audioSourceBuffer) {
            int audioSourceBufferOffset = 0;
            while (audioSourceBufferOffset < audioSourceBuffer.length) {
              // Repeat the previous frequency value while we collect and analyze new data.
              if (previousFrequency != null) {
                c.addData(clock.getNow(), previousFrequency);
              }

              int lengthToCopy =
                  Math.min(
                      audioSourceBuffer.length - audioSourceBufferOffset,
                      audioAnalyzerBuffer.length - audioAnalyzerBufferOffset);
              System.arraycopy(
                  audioSourceBuffer,
                  audioSourceBufferOffset,
                  audioAnalyzerBuffer,
                  audioAnalyzerBufferOffset,
                  lengthToCopy);
              audioAnalyzerBufferOffset += lengthToCopy;
              audioSourceBufferOffset += lengthToCopy;

              // If audioAnalyzerBuffer is full, analyze it.
              if (audioAnalyzerBufferOffset == audioAnalyzerBuffer.length) {
                Double frequency = audioAnalyzer.detectFundamentalFrequency(audioAnalyzerBuffer);
                long timestampMillis = clock.getNow();
                if (frequency == null) {
                  // Unable to detect frequency, likely due to low volume.
                  c.addData(timestampMillis, 0);
                } else if (isDrasticSpike(frequency)) {
                  // Avoid drastic changes that show as spikes in the graph between notes
                  // being played on an instrument. If the new value is more than 50%
                  // different from the previous value, skip it.
                  // Note that since we set previousFrequency to frequency below, we
                  // will never skip two consecutive values.
                  frequency = null;
                } else {
                  c.addData(timestampMillis, frequency);
                }
                previousFrequency = frequency;

                // Since we've analyzed that buffer, set the offset back to 0.
                audioAnalyzerBufferOffset = 0;
              }
            }
          }

          private boolean isDrasticSpike(double frequency) {
            return previousFrequency != null
                && Math.abs(frequency - previousFrequency) / previousFrequency > 0.50;
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
}
