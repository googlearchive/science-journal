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
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/** Represents a peak in FFT (Fast Fourier Transform) output. */
class Peak {
  private final int fftIndex;
  private final double frequencyEstimate;
  private final double fftValue;
  private double frequency;
  private final List<Harmonic> harmonics = new ArrayList<>();

  Peak(int fftIndex, double frequencyEstimate, double fftMagnitude, double fftProminence) {
    this.fftIndex = fftIndex;
    this.frequencyEstimate = frequencyEstimate;
    fftValue = fftMagnitude * fftProminence;
  }

  /**
   * Returns the FFT value for this peak. The FFT value is a value that determined by the FFT
   * magnitude and the prominence of the peak.
   */
  double getFftValue() {
    return fftValue;
  }

  /**
   * Returns the frequency estimate of this peak, based on which FFT bin corresponds to the peak.
   */
  double getFrequencyEstimate() {
    return frequencyEstimate;
  }

  /** Returns the frequency as determined by applying a series of Goertzel filters. */
  double getFrequency() {
    return frequency;
  }

  /** Sets the frequency as determined by applying a series of Goertzel filters. */
  void setFrequency(double frequency) {
    this.frequency = frequency;
  }

  /** Adds the given harmonic relationship to the harmonics list. */
  void addHarmonic(Harmonic harmonic) {
    harmonics.add(harmonic);
  }

  /** Returns the set of harmonic ratio terms that have been identified for this peak. */
  NavigableSet<Integer> getHarmonicTerms() {
    TreeSet<Integer> terms = new TreeSet<>();
    for (Harmonic harmonic : harmonics) {
      terms.add(harmonic.getTermForPeak(this));
    }
    return terms;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Peak)) {
      return false;
    }

    Peak peak = (Peak) obj;
    return fftIndex == peak.fftIndex;
  }

  @Override
  public int hashCode() {
    return fftIndex;
  }
}
