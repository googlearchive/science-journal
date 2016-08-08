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

import com.google.android.apps.forscience.whistlepunk.audiogen.voices
        .DataToAmplitudeSimpleJsynUnitVoiceAdapter;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices
        .DataToPentatonicScalePitchSimpleJsynUnitVoiceAdapter;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices
        .DataToPitchEnvelopeSimpleJsynUnitVoiceAdapter;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices
        .DataToPitchSimpleJsynUnitVoiceAdapter;
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
            return new DataToPentatonicScalePitchSimpleJsynUnitVoiceAdapter(synth);
        } else {
            return null;
        }
    }


}
