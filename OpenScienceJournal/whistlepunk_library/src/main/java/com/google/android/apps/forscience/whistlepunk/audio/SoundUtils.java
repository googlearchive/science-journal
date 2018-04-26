/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SoundUtils {
  public static final int NUMBER_OF_PIANO_KEYS = 88;
  public static final double HALF_STEP_FREQUENCY_RATIO = 1.05946;
  public static final double LOWEST_PIANO_FREQUENCY = 27.5;
  public static final double HIGHEST_PIANO_FREQUENCY = 4186.01;

  private static final List<Double> pianoNoteFrequencies =
      new ArrayList<Double>(NUMBER_OF_PIANO_KEYS);

  private static void fillPianoNoteFrequencies() {
    // Fill the pianoNoteFrequencies list with the notes for an 88 key piano.
    double[] highNotes = {
      4186.01, // c
      3951.07, // b
      3729.31, // a#
      3520, // a
      3322.44, // g#
      3135.96, // g
      2959.96, // f#
      2793.83, // f
      2637.02, // e
      2489.02, // d#
      2349.32, // d
      2217.46, // c#
    };
    double multiplier = 1;
    while (pianoNoteFrequencies.size() < NUMBER_OF_PIANO_KEYS) {
      for (double note : highNotes) {
        if (pianoNoteFrequencies.size() == NUMBER_OF_PIANO_KEYS) {
          break;
        }
        pianoNoteFrequencies.add(note * multiplier);
      }
      multiplier /= 2;
    }
    Collections.reverse(pianoNoteFrequencies);
  }

  public static List<Double> getPianoNoteFrequencies() {
    if (pianoNoteFrequencies.isEmpty()) {
      fillPianoNoteFrequencies();
    }
    return pianoNoteFrequencies;
  }

  public static double calculateUncalibratedDecibels(short[] samples, int length) {
    double totalSquared = 0;

    for (int i = 0; i < length; i++) {
      short soundbits = samples[i];
      totalSquared += soundbits * soundbits;
    }

    // https://en.wikipedia.org/wiki/Sound_pressure
    final double quadraticMeanPressure = Math.sqrt(totalSquared / length);
    final double uncalibratedDecibels = 20 * Math.log10(quadraticMeanPressure);
    return uncalibratedDecibels;
  }
}
