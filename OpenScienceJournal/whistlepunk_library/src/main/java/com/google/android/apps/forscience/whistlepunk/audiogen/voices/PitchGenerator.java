package com.google.android.apps.forscience.whistlepunk.audiogen.voices;

import android.util.Log;

import com.google.common.primitives.Ints;
import java.util.ArrayList;

/**
 * Given a scale (an integer array of zero-based note indices), expand the scale to include all
 * pitches in [pitchMin, pitchMax] that are valid members of the scale.
 * <p>
 * @param scale array of zero-based note indices.  0 indicates the root note,
 *              11 the highest (in a 12-tone system).
 * @param pitchMin lowest pitch generated in the expansion.  pitchMin is guaranteed to be in the
 *                 resulting array if the scale includes the root note.
 * @param pitchMax highest pitch generated in the expansion.  pitchMax is not guaranteed to be in
 *                 the resulting array if the scale does not include that note.
 * </p>
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
