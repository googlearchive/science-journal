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

package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.audiogen.JsynUnitVoiceAdapterInterface;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices.SimpleJsynUnitVoice;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.softsynth.shared.time.TimeStamp;

/**
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator.
 * <p>
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator.
 * This implementation maps the data from the range (min-max) linearly to the range
 * FREQ_HZ_MIN-FREQ_HZ_MAX.
 * </p>
 */
public class SimpleJsynUnitVoiceAdapter implements JsynUnitVoiceAdapterInterface {
    public static final double FREQ_HZ_MIN = 300;
    public static final double FREQ_HZ_MAX = 700;
    public static final double AMP_VALUE = 0.5; // default value for amplitude

    private final SimpleJsynUnitVoice mVoice;

    public SimpleJsynUnitVoiceAdapter(Synthesizer synth) {
        mVoice = new SimpleJsynUnitVoice();
        synth.add(mVoice);
    }

    public void noteOn(double value, double min, double max, TimeStamp timeStamp) {
        double freq = (value - min) / (max - min) * (FREQ_HZ_MAX - FREQ_HZ_MIN) + FREQ_HZ_MIN;
        mVoice.noteOn(freq, AMP_VALUE, timeStamp);
    }

    public SimpleJsynUnitVoice getVoice() {
        return mVoice;
    }

    public UnitOutputPort getOutput() {
        return mVoice.getOutput();
    }
}
