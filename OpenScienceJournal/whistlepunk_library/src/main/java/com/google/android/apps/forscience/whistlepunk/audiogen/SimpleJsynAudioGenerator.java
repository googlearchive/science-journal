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

/**
 * Generates audio by mapping the input data to a range of frequencies.
 */
public class SimpleJsynAudioGenerator implements AudioGenerator {
    // Logging tag is truncated because it cannot be more than 24 characters long.
    private static final String TAG = "SimpleJsynAudioGenerato";
    private static final int SAMPLE_RATE = 44100;

    private final AndroidAudioForJSyn mAudioManager;
    private final Synthesizer mSynth;
    private JsynUnitVoiceAdapterInterface mAdapter = null;
    private LineOut mLineOut;
    private String mSonificationType = "";

    public SimpleJsynAudioGenerator() {
        this(SonificationTypeAdapterFactory.DEFAULT_SONIFICATION_TYPE);
    }

    public SimpleJsynAudioGenerator(String sonificationType) {
        mAudioManager = new AndroidAudioForJSyn();
        mSynth = JSyn.createSynthesizer(mAudioManager);
        // Add an output mixer.
        mSynth.add(mLineOut = new LineOut());
        setSonificationType(sonificationType);
    }

    @Override
    public void startPlaying() {
        // No input, dual channel (stereo) output.
        mSynth.start(SAMPLE_RATE, mAudioManager.getDefaultInputDeviceID(), 0,
                mAudioManager.getDefaultOutputDeviceID(), 2);
        mLineOut.start();
    }

    @Override
    public void stopPlaying() {
        if (mLineOut != null) {
            mLineOut.stop();
        }
        if (mSynth != null) {
            mSynth.stop();
        }
    }

    @Override
    public void destroy() {
        reset();
        mAdapter = null;
        mLineOut = null;
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
        if (mAdapter == null) {
            return;
        }
        if (min >= max) {
            return;
        }
        mAdapter.noteOn(value, min, max, mSynth.createTimeStamp());
    }

    @Override
    public void setSonificationType(String sonificationType) {
        if (TextUtils.equals(sonificationType, mSonificationType)) {
            return;
        }
        mSonificationType = sonificationType;
        if (mAdapter != null) {
            disconnect();
        };
        mAdapter = SonificationTypeAdapterFactory.getSonificationTypeAdapter(mSynth,
                sonificationType);
        if (mAdapter != null) {
            // Connect the oscillator to the output (both stereo channels).
            mAdapter.getVoice().getOutput().connect(0, mLineOut.input, 0);
            mAdapter.getVoice().getOutput().connect(0, mLineOut.input, 1);
        } else {
            Log.wtf(TAG, "Unexpected sonfication type: " + sonificationType);
        }
    }

    private void disconnect() {
        if (mAdapter != null) {
            mAdapter.getVoice().getOutput().disconnect(0, mLineOut.input, 0);
            mAdapter.getVoice().getOutput().disconnect(0, mLineOut.input, 1);
            mAdapter = null;
        }
    }
}