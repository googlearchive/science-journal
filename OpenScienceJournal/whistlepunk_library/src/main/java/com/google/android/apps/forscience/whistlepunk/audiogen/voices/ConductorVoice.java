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

package com.google.android.apps.forscience.whistlepunk.audiogen.voices;

import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.audiogen.JsynUnitVoiceAdapter;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.softsynth.math.AudioMath;
import com.softsynth.shared.time.TimeStamp;

/**
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator using pentatonic scale with silence
 * for small values.
 *
 * <p>Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator. This implementation maps the
 * data from the range (min+5%-max) linearly pitches in the pentatonic C major scale covering a wide
 * range of frequencies, Values lower than min+5% are mapped to silence.
 */
public class ConductorVoice extends JsynUnitVoiceAdapter {
  public static final String TAG = "ConductorVoice";
  private static final boolean LOCAL_LOGV = false;

  private final int[] pitches;

  private static final int scale[] = {
    0 /* C */, 2 /* D */, 4 /* E */, 7 /* G */, 9 /* A */
  }; // pentatonic scale
  // Range of generated frequencies is one octave
  protected static final double FREQ_MIN = 261.63;
  protected static final double FREQ_MAX = 440.0;
  private static final int PITCH_MIN = (int) Math.floor(AudioMath.frequencyToPitch(FREQ_MIN));
  private static final int PITCH_MAX = (int) Math.floor(AudioMath.frequencyToPitch(FREQ_MAX));
  private double min = Double.MAX_VALUE;
  private double max = Double.MIN_VALUE;
  private int prevIndex = -1;
  double prevThresh = Double.MIN_VALUE;
  boolean playing = false;
  double oldValue = Double.MIN_VALUE;

  public ConductorVoice(Synthesizer synth) {
    pitches = PitchGenerator.generatePitches(scale, PITCH_MIN, PITCH_MAX);
    voice = new SineEnvelope();
    synth.add(voice);
    EnvelopeDAHDSR DAHDSR = ((SineEnvelope) getVoice()).getDAHDSR();
    DAHDSR.hold.set(1000);
    DAHDSR.sustain.set(1000);
  }

  public void noteOn(double value, double unusedMin, double unusedMax, TimeStamp timeStamp) {
    // Range checking, in case min or max needs adjustment
    if (value < min) {
      if (LOCAL_LOGV) {
        Log.v(TAG, "new min: " + value);
      }
      min = value;
    }
    if (value > max) {
      if (LOCAL_LOGV) {
        Log.v(TAG, "new max: " + value);
      }
      max = value;
    }
    // If value < min+5%, map to silence
    double thresh = min + (max - min) * .05;
    if (thresh != prevThresh) {
      if (LOCAL_LOGV) {
        Log.v(TAG, "new thresh: " + thresh);
      }
      prevThresh = thresh;
    }

    oldValue = value;
    if (value > thresh) {
      if (LOCAL_LOGV && oldValue != value) {
        Log.v(TAG, "value: " + value + " above threshold: " + thresh);
      }
      int index = (int) Math.floor((value - thresh) / (max - thresh) * (pitches.length - 1));
      if (index != prevIndex) {
        if (LOCAL_LOGV) {
          Log.v(TAG, "New Index: " + index);
        }
        double freq = AudioMath.pitchToFrequency(pitches[index]);
        voice.noteOn(freq, AMP_VALUE, timeStamp);
        playing = true;
        prevIndex = index;
      }
    } else {
      if (playing && LOCAL_LOGV) {
        Log.v(TAG, "Note off with value: " + value + " and threshold " + thresh);
      }
      voice.noteOff(timeStamp);
      playing = false;
      prevIndex = -1;
    }
  }
}
