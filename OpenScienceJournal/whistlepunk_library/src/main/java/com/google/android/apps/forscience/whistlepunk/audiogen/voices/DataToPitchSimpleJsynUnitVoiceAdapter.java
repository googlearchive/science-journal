package com.google.android.apps.forscience.whistlepunk.audiogen.voices;

import com.google.android.apps.forscience.whistlepunk.audiogen.JsynUnitVoiceAdapterInterface;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices.SimpleJsynUnitVoice;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.softsynth.shared.time.TimeStamp;

/**
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator using pitch variation.
 * <p>
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator.
 * This implementation maps the data from the range (min-max) linearly to the pitch range
 * FREQ_MIN-FREQ_MAX.
 * </p>
 */
public class DataToPitchSimpleJsynUnitVoiceAdapter implements JsynUnitVoiceAdapterInterface {
    public static final double FREQ_MIN = 300;
    public static final double FREQ_MAX = 700;
    public static final double AMP_VALUE = 1.0; // default value for amplitude

    private final SimpleJsynUnitVoice mVoice;

    public DataToPitchSimpleJsynUnitVoiceAdapter(Synthesizer synth) {
        mVoice = new SimpleJsynUnitVoice();
        synth.add(mVoice);
    }

    public void noteOn(double value, double min, double max, TimeStamp timeStamp) {
        double freq = (value - min) / (max - min) * (FREQ_MAX - FREQ_MIN) + FREQ_MIN;
        mVoice.noteOn(freq, AMP_VALUE, timeStamp);
    }

    public SimpleJsynUnitVoice getVoice() {
      return mVoice;
    }
}
