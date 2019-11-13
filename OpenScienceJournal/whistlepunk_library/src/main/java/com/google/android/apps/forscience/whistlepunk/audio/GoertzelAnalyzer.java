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

/** Determines the frequency by using a series of Goertzel filters. */
class GoertzelAnalyzer {
  private final int sampleRateInHz;

  GoertzelAnalyzer(int sampleRateInHz) {
    this.sampleRateInHz = sampleRateInHz;
  }

  /**
   * Applies a series of Goertzel filters to frequencies near the given frequency estimate, and
   * returns the frequency with the highest power
   */
  double findFrequencyWithHighestPower(short[] samples, double frequencyEstimate) {
    // Choose accuracy based on frequency estimate. Lower frequencies need to be more
    // accurate than higher frequencies.
    double accuracy;
    // TODO(lizlooney): Try using frequencyEstimate / 10_000 instead of this stepped scale. If
    // that works well, the 10_000 is a hand-picked number that should be set via constructors
    // in AudioAnalyzer and GoertzelAnalyzer.
    if (frequencyEstimate < 100) {
      accuracy = 0.01;
    } else if (frequencyEstimate < 1000) {
      accuracy = 0.1;
    } else {
      accuracy = 1;
    }

    // loFrequency is lower than hiFrequency, but the order of powerAtLoFrequency and
    // powerAtHiFrequency is not relevant.
    double loFrequency = frequencyEstimate - 10;
    double powerAtLoFrequency = calculatePower(samples, loFrequency);
    double hiFrequency = frequencyEstimate + 10;
    double powerAtHiFrequency = calculatePower(samples, hiFrequency);

    do {
      // greatestPower is greater than secondGreatestPower, but the order of
      // frequencyWithGreatestPower and frequencyWithSecondGreatestPower is not relevant.
      double greatestPower,
          frequencyWithGreatestPower,
          secondGreatestPower,
          frequencyWithSecondGreatestPower;
      if (powerAtLoFrequency > powerAtHiFrequency) {
        greatestPower = powerAtLoFrequency;
        frequencyWithGreatestPower = loFrequency;
        secondGreatestPower = powerAtHiFrequency;
        frequencyWithSecondGreatestPower = hiFrequency;
      } else {
        greatestPower = powerAtHiFrequency;
        frequencyWithGreatestPower = hiFrequency;
        secondGreatestPower = powerAtLoFrequency;
        frequencyWithSecondGreatestPower = loFrequency;
      }

      // Divide the interval between loFrequency and hiFrequency into 4 parts and get the
      // Goertzel power at each division.
      double interval = (hiFrequency - loFrequency) / 4;
      for (double frequency = loFrequency + interval;
          frequency < hiFrequency;
          frequency += interval) {
        double power = calculatePower(samples, frequency);
        // Keep track of the greatest power as greatestPower and the second greatest
        // power as secondGreatestPower.
        if (power > greatestPower) {
          // Move greatestPower to secondGreatestPower.
          secondGreatestPower = greatestPower;
          frequencyWithSecondGreatestPower = frequencyWithGreatestPower;
          // Replace greatestPower.
          greatestPower = power;
          frequencyWithGreatestPower = frequency;
        } else if (power > secondGreatestPower) {
          // Replace secondGreatestPower.
          secondGreatestPower = power;
          frequencyWithSecondGreatestPower = frequency;
        }
      }

      double previousHi = hiFrequency;
      double previousLo = loFrequency;

      // Figure out which of the two frequencies with the greatest powers is lower and
      // which is higher.
      if (frequencyWithGreatestPower > frequencyWithSecondGreatestPower) {
        hiFrequency = frequencyWithGreatestPower;
        powerAtHiFrequency = greatestPower;
        loFrequency = frequencyWithSecondGreatestPower;
        powerAtLoFrequency = secondGreatestPower;
      } else {
        hiFrequency = frequencyWithSecondGreatestPower;
        powerAtHiFrequency = secondGreatestPower;
        loFrequency = frequencyWithGreatestPower;
        powerAtLoFrequency = greatestPower;
      }

      // If the low and high frequencies haven't changed, then we aren't finding a peak
      // and we can give up.
      if (doublesEqual(previousHi, hiFrequency) && doublesEqual(previousLo, loFrequency)) {
        break;
      }
    } while (hiFrequency - loFrequency > accuracy);

    return (powerAtLoFrequency > powerAtHiFrequency) ? loFrequency : hiFrequency;
  }

  /** Returns true if the given doubles are equal enough, false otherwise. */
  private static boolean doublesEqual(double d1, double d2) {
    return Math.abs(d1 - d2) < 0.000001;
  }

  /** Calculates the power at the given target frequency. */
  private double calculatePower(short[] samples, double targetFrequency) {
    double normalizedFrequency = targetFrequency / sampleRateInHz;
    double coeff = 2 * Math.cos(2 * Math.PI * normalizedFrequency);
    double sPrev1 = 0;
    double sPrev2 = 0;

    for (short sample : samples) {
      double s = sample + coeff * sPrev1 - sPrev2;
      sPrev2 = sPrev1;
      sPrev1 = s;
    }
    return sPrev2 * sPrev2 + sPrev1 * sPrev1 - coeff * sPrev1 * sPrev2;
  }
}
