package com.google.android.apps.forscience.whistlepunk.audiogen.voices;

import com.google.android.apps.forscience.whistlepunk.audiogen.JsynUnitVoiceAdapterInterface;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices.SimpleJsynUnitVoice;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.softsynth.shared.time.TimeStamp;

/**
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator using amplitude variation.
 * <p>
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator.
 * This implementation maps the data from the range (min-max) linearly to the amplitude range
 * AMP_MIN-AMP_MAX.
 * </p>
 */
public class DataToAmplitudeSimpleJsynUnitVoiceAdapter implements JsynUnitVoiceAdapterInterface {
    public static final double AMP_MIN = 0.01;
    public static final double AMP_MAX = 1.0;
    public static final double FREQ_VALUE = 440; // default value for amplitude

    private final SimpleJsynUnitVoice mVoice;

    public DataToAmplitudeSimpleJsynUnitVoiceAdapter(Synthesizer synth) {
        mVoice = new SimpleJsynUnitVoice();
        synth.add(mVoice);
    }

    public void noteOn(double value, double min, double max, TimeStamp timeStamp) {
        double amp = (value - min) / (max - min) * (AMP_MAX - AMP_MIN) + AMP_MIN;
        mVoice.noteOn(FREQ_VALUE, amp, timeStamp);
    }

    public SimpleJsynUnitVoice getVoice() {
      return mVoice;
    }
}
