package com.google.android.apps.forscience.whistlepunk.audiogen.voices;

import com.jsyn.Synthesizer;
import com.softsynth.math.AudioMath;

/**
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator using pentatonic scale.
 * <p>
 * Adapt the SimpleJsynUnitVoice to the SimpleJsynAudioGenerator.
 * This implementation maps the data from the range (min-max) linearly pitches in the pentatonic C
 * major scale covering a wide range of frequencies.
 *
 * </p>
 */
public class DataToPentatonicScalePitchEnvelopeSimpleJsynUnitVoiceAdapter extends
        DataToScalePitchEnvelopeSimpleJsynUnitVoiceAdapter {
    public static final String TAG = "DataToScalePitchEnvelopeSimpleJsynUnitVoiceAdapter";

    private static final int scale[] = {
            0 /* C */, 2 /* D */, 4 /* E */, 7 /* G */, 9 /* A */ }; // pentatonic scale
    // Range of generated frequencies is [FREQ_MIN,FREQ_MAX).  Lower than 200Hz is very quiet on
    // small speakers, while higher than 800Hz tends to sound like a metal detector.
    private static final double FREQ_MIN = 220.;
    private static final double FREQ_MAX = 783.991;
    private static final int PITCH_MIN = (int) Math.floor(AudioMath.frequencyToPitch(FREQ_MIN));
    private static final int PITCH_MAX = (int) Math.floor(AudioMath.frequencyToPitch(FREQ_MAX));

    public DataToPentatonicScalePitchEnvelopeSimpleJsynUnitVoiceAdapter(Synthesizer synth) {
        super(synth, scale, PITCH_MIN, PITCH_MAX);
    }

}
