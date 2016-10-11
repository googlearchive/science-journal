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
import com.softsynth.math.AudioMath;
import com.softsynth.shared.time.TimeStamp;

/**
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator using pentatonic scale with
 * silence for small values.
 * <p>
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator.
 * This implementation maps the data from the range (min+10%-max) linearly pitches in the pentatonic
 * C major scale covering a wide range of frequencies,
 * Values lower than min+5% are mapped to silence.
 * </p>
 */
public class ConductorVoice extends JsynUnitVoiceAdapter {
    public static final String TAG = "ConductorVoice";
    private static final boolean LOCAL_LOGV = false;

    private final int[] mPitches;

    private static final int scale[] = {
            0 /* C */, 2 /* D */, 4 /* E */, 7 /* G */, 9 /* A */}; // pentatonic scale
    // Range of generated frequencies is one octave
    protected static final double FREQ_MIN = 261.63;
    protected static final double FREQ_MAX = 440.0;
    private static final int PITCH_MIN = (int) Math.floor(AudioMath.frequencyToPitch(FREQ_MIN));
    private static final int PITCH_MAX = (int) Math.floor(AudioMath.frequencyToPitch(FREQ_MAX));
    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;
    private int prevIndex = -1;

    public ConductorVoice(Synthesizer synth) {
        mPitches = PitchGenerator.generatePitches(scale, PITCH_MIN, PITCH_MAX);
        mVoice = new SineEnvelope();
        synth.add(mVoice);
    }

    public void noteOn(double value, double unusedMin, double unusedMax, TimeStamp timeStamp) {
        // Default implementation (or any implementation with no pitches) does nothing.
        if (mPitches.length == 0) return;
        // Range checking, in case min or max needs adjustment
        if (value < min) min = value;
        if (value > max) max = value;
        // If value < min+10%, map to silence
        double thresh = min + (max - min) * .05;
        if (LOCAL_LOGV) {
            Log.d(TAG, "value: " + value + " min: " + min + " max: " + max + " threshold: " + thresh);
        }
        if (value >= thresh) {
            int index = (int) Math.floor((value - thresh) / (max - thresh) * (mPitches.length - 1));
            if (index != prevIndex) {
                double freq = AudioMath.pitchToFrequency(mPitches[index]);
                mVoice.noteOn(freq, AMP_VALUE, timeStamp);
            }
            prevIndex = index;
        } else {
            mVoice.noteOff(timeStamp);
        }
    }
}
