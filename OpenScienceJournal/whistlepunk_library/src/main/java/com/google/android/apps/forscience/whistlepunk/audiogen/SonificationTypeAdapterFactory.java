package com.google.android.apps.forscience.whistlepunk.audiogen;

import com.google.android.apps.forscience.whistlepunk.audiogen.voices
        .DataToAmplitudeSimpleJsynUnitVoiceAdapter;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices
        .DataToPentatonicScalePitchEnvelopeSimpleJsynUnitVoiceAdapter;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices
        .DataToPitchEnvelopeSimpleJsynUnitVoiceAdapter;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices
        .DataToPitchSimpleJsynUnitVoiceAdapter;
import com.jsyn.Synthesizer;

public class SonificationTypeAdapterFactory {

    public static JsynUnitVoiceAdapterInterface getSonificationTypeAdapter(
            Synthesizer synth, String sonification_type) {
        if (sonification_type.equals("d2p")) {
            return new DataToPitchSimpleJsynUnitVoiceAdapter(synth);
        } else if (sonification_type.equals("d2a")) {
            return new DataToAmplitudeSimpleJsynUnitVoiceAdapter(synth);
        } else if (sonification_type.equals("d2pe")) {
            return new DataToPitchEnvelopeSimpleJsynUnitVoiceAdapter(synth);
        } else if (sonification_type.equals("d2ps")) {
            return new DataToPentatonicScalePitchEnvelopeSimpleJsynUnitVoiceAdapter(synth);
        } else {
            return null;
        }
    }


}
