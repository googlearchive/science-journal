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

import com.google.common.primitives.Ints;
import java.util.ArrayList;

/**
 * Given a scale (an integer array of zero-based note indices), expand the scale to include all
 * pitches in [pitchMin, pitchMax] that are valid members of the scale.
 *
 * <p>
 *
 * @param scale array of zero-based note indices. 0 indicates the root note, 11 the highest (in a
 *     12-tone system).
 * @param pitchMin lowest pitch generated in the expansion. pitchMin is guaranteed to be in the
 *     resulting array if the scale includes the root note.
 * @param pitchMax highest pitch generated in the expansion. pitchMax is not guaranteed to be in the
 *     resulting array if the scale does not include that note.
 */
class PitchGenerator {

  public static int[] generatePitches(int scale[], int pitchMin, int pitchMax) {
    ArrayList<Integer> pitches = new ArrayList<>();

    for (int pitch = pitchMin; pitch <= pitchMax; pitch += 12) {
      for (int i = 0; i < scale.length; i++) {
        if (pitch + scale[i] > pitchMax) {
          break;
        }
        pitches.add(pitch + scale[i]);
      }
    }

    return Ints.toArray(pitches);
  }
}
