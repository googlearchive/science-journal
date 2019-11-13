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

import com.google.android.apps.forscience.whistlepunk.audiogen.JsynUnitVoiceAdapter;
import com.jsyn.Synthesizer;
import com.softsynth.shared.time.TimeStamp;

/**
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator using pitch variation with envelope
 *
 * <p>Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator. This implementation maps the
 * data from the range (min-max) linearly to the pitch range FREQ_MIN-FREQ_MAX and converts the
 * pitches to notes by modulating them through an envelope.
 */
public class NotesVoice extends JsynUnitVoiceAdapter {
  private static final double MIN_VALUE_PERCENT_CHANGE = 10.; // min value percent delta trigger
  private static final long MIN_TIME_VALUE_CHANGE_MS = 125; // min time delta to trigger new audio
  private static final long MIN_TIME_CHANGE_MS = 1000; // min time change to trigger new audio

  private long oldTime = System.currentTimeMillis();
  private double oldValue = Double.MIN_VALUE;

  public NotesVoice(Synthesizer synth) {
    voice = new SineEnvelope();
    synth.add(voice);
  }

  public void noteOn(double newValue, double min, double max, TimeStamp timeStamp) {
    if (oldValue == Double.MIN_VALUE) {
      oldValue = newValue;
      return;
    }
    // Range checking, in case min or max is higher or lower than value (respectively).
    if (newValue < min) newValue = min;
    if (newValue > max) newValue = max;

    // Compute percent change of value within Y axis range.
    double dv = newValue - oldValue;
    double range = max - min;
    double change = Math.abs(dv / range * 100.);

    long newTime = System.currentTimeMillis();
    long dt = newTime - oldTime;

    // If the value hasn't changed more than MIN_VALUE_CHANGED, suppress new notes for up to
    // MIN_TIME_CHANGE_MS.  If value has changed more than MIN_VALUE_CHANGED, suppress new
    // notes for MIN_TIME_VALUE_CHANGE_MS.
    if ((change >= MIN_VALUE_PERCENT_CHANGE && dt > MIN_TIME_VALUE_CHANGE_MS)
        || (change < MIN_VALUE_PERCENT_CHANGE && dt > MIN_TIME_CHANGE_MS)) {
      oldTime = newTime;
      oldValue = newValue;
      double freq = (newValue - min) / (max - min) * (FREQ_MAX - FREQ_MIN) + FREQ_MIN;
      voice.noteOn(freq, AMP_VALUE, timeStamp);
      voice.noteOff(timeStamp.makeRelative(MIN_TIME_VALUE_CHANGE_MS / 1000.));
    }
  }
}
