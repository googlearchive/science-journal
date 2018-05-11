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

import com.google.android.apps.forscience.whistlepunk.audiogen.voices.SimpleJsynUnitVoiceBase;
import com.jsyn.unitgen.UnitVoice;
import com.softsynth.shared.time.TimeStamp;

public class JsynUnitVoiceAdapter implements JsynUnitVoiceAdapterInterface {
  protected SimpleJsynUnitVoiceBase voice = null;
  // Range of generated frequencies is [FREQ_MIN,FREQ_MAX).  Lower than 200Hz is very quiet on
  // small speakers, while higher than 800Hz tends to sound like a metal detector.
  protected static final double FREQ_MIN = 220.;
  protected static final double FREQ_MAX = 783.991;
  protected static final double AMP_VALUE = 1.0; // default value for amplitude

  public void noteOn(double value, double min, double max, TimeStamp timeStamp) {
    double freq = (value - min) / (max - min) * (FREQ_MAX - FREQ_MIN) + FREQ_MIN;
    voice.noteOn(freq, AMP_VALUE, timeStamp);
  }

  public void noteOff(TimeStamp timeStamp) {
    voice.noteOff(timeStamp);
  }

  public UnitVoice getVoice() {
    return voice;
  }
}
