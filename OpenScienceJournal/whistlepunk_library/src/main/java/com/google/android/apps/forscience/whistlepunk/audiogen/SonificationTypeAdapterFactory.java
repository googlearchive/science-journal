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

import com.google.android.apps.forscience.whistlepunk.audiogen.voices.AmplitudeVoice;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices.ConductorVoice;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices.DefaultVoice;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices.NotesVoice;
import com.google.android.apps.forscience.whistlepunk.audiogen.voices.ScaleVoice;
import com.jsyn.Synthesizer;

public class SonificationTypeAdapterFactory {

  public static final String DEFAULT_SONIFICATION_TYPE = "d2p";
  public static final String SCALE_SONIFICATION_TYPE = "d2ps";
  public static final String NOTES_SONIFICATION_TYPE = "d2pe";
  public static final String CONDUCTOR_SONIFICATION_TYPE = "conductor";
  public static final String AMPLITUDE_SONIFICATION_TYPE = "d2a";

  // The sonification types available. This MUST be in the same order as the string arrays
  // sonification_types_prod + sonification_types_dev.
  public static final String[] SONIFICATION_TYPES =
      new String[] {
        DEFAULT_SONIFICATION_TYPE,
        SCALE_SONIFICATION_TYPE,
        NOTES_SONIFICATION_TYPE,
        CONDUCTOR_SONIFICATION_TYPE,
        AMPLITUDE_SONIFICATION_TYPE
      };

  public static JsynUnitVoiceAdapterInterface getSonificationTypeAdapter(
      Synthesizer synth, String sonification_type) {
    if (sonification_type.equals(DEFAULT_SONIFICATION_TYPE)) {
      return new DefaultVoice(synth);
    } else if (sonification_type.equals(AMPLITUDE_SONIFICATION_TYPE)) {
      return new AmplitudeVoice(synth);
    } else if (sonification_type.equals(NOTES_SONIFICATION_TYPE)) {
      return new NotesVoice(synth);
    } else if (sonification_type.equals(SCALE_SONIFICATION_TYPE)) {
      return new ScaleVoice(synth);
    } else if (sonification_type.equals(CONDUCTOR_SONIFICATION_TYPE)) {
      return new ConductorVoice(synth);
    } else {
      return null;
    }
  }
}
