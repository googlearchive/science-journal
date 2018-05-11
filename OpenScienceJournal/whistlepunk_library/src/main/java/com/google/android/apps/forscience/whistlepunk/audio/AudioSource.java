/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioSource {
  public static final int SAMPLE_RATE_IN_HZ = 44100;
  private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
  private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private volatile Future<?> future;
  private final int minBufferSizeInBytes;
  private final Object lockAudioReceivers = new Object();
  private final List<AudioReceiver> audioReceivers = new ArrayList<>();

  public interface AudioReceiver {
    /**
     * Called when a full buffer of audio data has been read and is ready to be processed by the
     * AudioReceiver.
     */
    void onReceiveAudio(short[] buffer);
  }

  public AudioSource() {
    minBufferSizeInBytes =
        AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT);
  }

  /** Registers the given AudioReceiver. Returns true if successful, false otherwise. */
  public boolean registerAudioReceiver(AudioReceiver audioReceiver) {
    if (minBufferSizeInBytes < 0 || audioReceiver == null) {
      // If this is the case, AudioRecord.getMinBufferSize returned an error.
      return false;
    }
    synchronized (lockAudioReceivers) {
      if (audioReceivers.contains(audioReceiver)) {
        // This audioReceiver was already added.
        return false;
      }
      audioReceivers.add(audioReceiver);
      if (audioReceivers.size() == 1) {
        start();
      }
      boolean success = running.get();
      // success will be false if the AudioRecord could not be initialized or could not start
      // recording.
      if (!success) {
        audioReceivers.remove(audioReceiver);
      }
      return success;
    }
  }

  public void unregisterAudioReceiver(AudioReceiver audioReceiver) {
    boolean needToStop = false;
    synchronized (lockAudioReceivers) {
      if (!audioReceivers.isEmpty()) {
        audioReceivers.remove(audioReceiver);
        needToStop = audioReceivers.isEmpty();
      }
    }
    if (needToStop) {
      stop();
    }
  }

  private void start() {
    // FYI: the current thread holds lockAudioReceivers.
    // Use VOICE_COMMUNICATION to filter out audio coming from the speakers
    final AudioRecord audioRecord =
        new AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE_IN_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSizeInBytes);
    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
      audioRecord.release();
      return;
    }

    audioRecord.startRecording();
    // AudioRecord.startRecording() logs an error but it has no return value and
    // doesn't throw an exception when someone else is using the mic.
    if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
      audioRecord.release();
      return;
    }

    running.set(true);
    future =
        executorService.submit(
            () -> {
              short[] buffer = new short[minBufferSizeInBytes / 2];
              int offset = 0;
              boolean goodDataRead = false;

              while (running.get()) {
                int readShorts = audioRecord.read(buffer, offset, buffer.length - offset);
                // On some devices (Moto E, for example) we get a bunch of zeros when we first
                // start reading. Ignore those zeros.
                if (!goodDataRead) {
                  int countLeadingZeros = 0;
                  while (countLeadingZeros < readShorts && buffer[countLeadingZeros] == 0) {
                    countLeadingZeros++;
                  }
                  if (countLeadingZeros > 0) {
                    if (readShorts > countLeadingZeros) {
                      System.arraycopy(
                          buffer, countLeadingZeros, buffer, 0, readShorts - countLeadingZeros);
                    }
                    readShorts -= countLeadingZeros;
                  }
                  goodDataRead = (readShorts > 0);
                }
                offset += readShorts;
                // If the buffer is full, call the Receivers.
                if (offset == buffer.length) {
                  synchronized (lockAudioReceivers) {
                    for (AudioReceiver audioReceiver : audioReceivers) {
                      audioReceiver.onReceiveAudio(buffer);
                    }
                  }
                  offset = 0;
                }
              }

              audioRecord.stop();
              audioRecord.release();
            });
  }

  private void stop() {
    running.set(false);

    // Wait for the task to finish so we know the AudioRecord has been released.
    try {
      future.get();
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      // Be a good citizen and set the interrupt flag.
      Thread.currentThread().interrupt();
    }
  }

  @VisibleForTesting
  public List<AudioReceiver> getRecievers() {
    return audioReceivers;
  }

  @VisibleForTesting
  public boolean isRunning() {
    return running.get();
  }

  @VisibleForTesting
  public void unregisterAllAudioReceivers() {
    synchronized (lockAudioReceivers) {
      while (!audioReceivers.isEmpty()) {
        unregisterAudioReceiver(audioReceivers.get(0));
      }
    }
  }
}
