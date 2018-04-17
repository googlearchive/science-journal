/*
 * Copyright 2011 Phil Burk, Mobileer Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jsyn.devices.android;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import com.jsyn.devices.AudioDeviceInputStream;
import com.jsyn.devices.AudioDeviceManager;
import com.jsyn.devices.AudioDeviceOutputStream;
import java.util.ArrayList;

/** Implement JSyn's AudioDeviceManager. Use Android AudioTrack to access the audio hardware. */
public class AndroidAudioForJSyn implements AudioDeviceManager {
  ArrayList<DeviceInfo> deviceRecords;
  private double suggestedOutputLatency = 0.100;
  private double suggestedInputLatency = 0.100;
  private int defaultInputDeviceID = -1;
  private int defaultOutputDeviceID = -1;

  public AndroidAudioForJSyn() {
    deviceRecords = new ArrayList<DeviceInfo>();
    DeviceInfo deviceInfo = new DeviceInfo();

    deviceInfo.name = "Android Audio";
    deviceInfo.maxInputs = 0;
    deviceInfo.maxOutputs = 2;
    defaultInputDeviceID = 0;
    defaultOutputDeviceID = 0;
    deviceRecords.add(deviceInfo);
  }

  @Override
  public String getName() {
    return "JSyn Android Audio";
  }

  class DeviceInfo {
    String name;
    int maxInputs;
    int maxOutputs;

    @Override
    public String toString() {
      return "AudioDevice: " + name + ", max in = " + maxInputs + ", max out = " + maxOutputs;
    }
  }

  private class AndroidAudioStream {
    short[] shortBuffer;
    int frameRate;
    int deviceID;
    int samplesPerFrame;
    AudioTrack audioTrack;
    int minBufferSize;
    int bufferSize;

    public AndroidAudioStream(int deviceID, int frameRate, int samplesPerFrame) {
      this.deviceID = deviceID;
      this.frameRate = frameRate;
      this.samplesPerFrame = samplesPerFrame;
    }

    public double getLatency() {
      int numFrames = bufferSize / samplesPerFrame;
      return ((double) numFrames) / frameRate;
    }
  }

  private class AndroidAudioOutputStream extends AndroidAudioStream
      implements AudioDeviceOutputStream {
    public AndroidAudioOutputStream(int deviceID, int frameRate, int samplesPerFrame) {
      super(deviceID, frameRate, samplesPerFrame);
    }

    @Override
    public void start() {
      minBufferSize =
          AudioTrack.getMinBufferSize(
              frameRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
      System.out.println("Audio minBufferSize = " + minBufferSize);
      bufferSize = (3 * (minBufferSize / 2)) & ~3;
      System.out.println("Audio bufferSize = " + bufferSize);
      audioTrack =
          new AudioTrack(
              AudioManager.STREAM_MUSIC,
              frameRate,
              AudioFormat.CHANNEL_OUT_STEREO,
              AudioFormat.ENCODING_PCM_16BIT,
              bufferSize,
              AudioTrack.MODE_STREAM);
      audioTrack.play();
    }

    /** Grossly inefficient. Call the array version instead. */
    @Override
    public void write(double value) {
      double[] buffer = new double[1];
      buffer[0] = value;
      write(buffer, 0, 1);
    }

    @Override
    public void write(double[] buffer) {
      write(buffer, 0, buffer.length);
    }

    @Override
    public void write(double[] buffer, int start, int count) {
      // Allocate buffer if needed.
      if ((shortBuffer == null) || (shortBuffer.length < count)) {
        shortBuffer = new short[count];
      }

      // Convert float samples to shorts.
      for (int i = 0; i < count; i++) {

        int sample = (int) (32767.0 * buffer[i + start]);
        if (sample > Short.MAX_VALUE) {
          sample = Short.MAX_VALUE;
        } else if (sample < Short.MIN_VALUE) {
          sample = Short.MIN_VALUE;
        }
        shortBuffer[i] = (short) sample;
      }

      audioTrack.write(shortBuffer, 0, count);
    }

    @Override
    public void stop() {
      audioTrack.stop();
      audioTrack.release();
    }

    @Override
    public void close() {}
  }

  private class AndroidAudioInputStream extends AndroidAudioStream
      implements AudioDeviceInputStream {

    public AndroidAudioInputStream(int deviceID, int frameRate, int samplesPerFrame) {
      super(deviceID, frameRate, samplesPerFrame);
    }

    @Override
    public void start() {}

    @Override
    public double read() {
      double[] buffer = new double[1];
      read(buffer, 0, 1);
      return buffer[0];
    }

    @Override
    public int read(double[] buffer) {
      return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(double[] buffer, int start, int count) {
      return 0;
    }

    @Override
    public void stop() {}

    @Override
    public int available() {
      return 0;
    }

    @Override
    public void close() {}
  }

  @Override
  public AudioDeviceOutputStream createOutputStream(
      int deviceID, int frameRate, int samplesPerFrame) {
    return new AndroidAudioOutputStream(deviceID, frameRate, samplesPerFrame);
  }

  @Override
  public AudioDeviceInputStream createInputStream(
      int deviceID, int frameRate, int samplesPerFrame) {
    if (frameRate > 0) throw new RuntimeException("JSyn audio input not implemented on Android.");
    return new AndroidAudioInputStream(deviceID, frameRate, samplesPerFrame);
  }

  @Override
  public double getDefaultHighInputLatency(int deviceID) {
    return 0.300;
  }

  @Override
  public double getDefaultHighOutputLatency(int deviceID) {
    return 0.300;
  }

  @Override
  public int getDefaultInputDeviceID() {
    return defaultInputDeviceID;
  }

  @Override
  public int getDefaultOutputDeviceID() {
    return defaultOutputDeviceID;
  }

  @Override
  public double getDefaultLowInputLatency(int deviceID) {
    return 0.100;
  }

  @Override
  public double getDefaultLowOutputLatency(int deviceID) {
    return 0.100;
  }

  @Override
  public int getDeviceCount() {
    return deviceRecords.size();
  }

  @Override
  public String getDeviceName(int deviceID) {
    return deviceRecords.get(deviceID).name;
  }

  @Override
  public int getMaxInputChannels(int deviceID) {
    return deviceRecords.get(deviceID).maxInputs;
  }

  @Override
  public int getMaxOutputChannels(int deviceID) {
    return deviceRecords.get(deviceID).maxOutputs;
  }

  @Override
  public int setSuggestedOutputLatency(double latency) {
    suggestedOutputLatency = latency;
    return 0;
  }

  @Override
  public int setSuggestedInputLatency(double latency) {
    suggestedInputLatency = latency;
    return 0;
  }
}
