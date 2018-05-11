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

package com.google.android.apps.forscience.whistlepunk.audiogen;

import android.text.TextUtils;
import android.util.Log;
import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.devices.android.AndroidAudioForJSyn;
import com.jsyn.unitgen.LineOut;

/** Generates audio by mapping the input data to a range of frequencies. */
public class SimpleJsynAudioGenerator implements AudioGenerator {
  // Logging tag is truncated because it cannot be more than 24 characters long.
  private static final String TAG = "SimpleJsynAudioGenerato";
  private static final int SAMPLE_RATE = 44100;

  private final AndroidAudioForJSyn audioManager;
  private final Synthesizer synth;
  private JsynUnitVoiceAdapterInterface adapter = null;
  private LineOut lineOut;
  private String sonificationType = "";

  public SimpleJsynAudioGenerator() {
    this(SonificationTypeAdapterFactory.DEFAULT_SONIFICATION_TYPE);
  }

  public SimpleJsynAudioGenerator(String sonificationType) {
    audioManager = new AndroidAudioForJSyn();
    synth = JSyn.createSynthesizer(audioManager);
    // Add an output mixer.
    synth.add(lineOut = new LineOut());
    setSonificationType(sonificationType);
  }

  @Override
  public void startPlaying() {
    // No input, dual channel (stereo) output.
    synth.start(
        SAMPLE_RATE,
        audioManager.getDefaultInputDeviceID(),
        0,
        audioManager.getDefaultOutputDeviceID(),
        2);
    lineOut.start();
  }

  @Override
  public void stopPlaying() {
    if (lineOut != null) {
      lineOut.stop();
    }
    if (synth != null) {
      synth.stop();
    }
  }

  @Override
  public void destroy() {
    reset();
    adapter = null;
    lineOut = null;
  }

  @Override
  public void reset() {
    stopPlaying();
    disconnect();
  }

  @Override
  public void addData(long unusedTimestamp, double value, double min, double max) {
    // Assume data is only added near now, and in order.
    // TODO: use Jsyn scheduling to play data in timestamp order.
    if (adapter == null) {
      return;
    }
    if (min >= max) {
      return;
    }
    adapter.noteOn(value, min, max, synth.createTimeStamp());
  }

  @Override
  public void setSonificationType(String sonificationType) {
    if (TextUtils.equals(sonificationType, this.sonificationType)) {
      return;
    }
    this.sonificationType = sonificationType;
    if (adapter != null) {
      disconnect();
    }
    ;
    adapter = SonificationTypeAdapterFactory.getSonificationTypeAdapter(synth, sonificationType);
    if (adapter != null) {
      // Connect the oscillator to the output (both stereo channels).
      adapter.getVoice().getOutput().connect(0, lineOut.input, 0);
      adapter.getVoice().getOutput().connect(0, lineOut.input, 1);
    } else {
      Log.wtf(TAG, "Unexpected sonfication type: " + sonificationType);
    }
  }

  private void disconnect() {
    if (adapter != null) {
      adapter.getVoice().getOutput().disconnect(0, lineOut.input, 0);
      adapter.getVoice().getOutput().disconnect(0, lineOut.input, 1);
      adapter = null;
    }
  }
}
