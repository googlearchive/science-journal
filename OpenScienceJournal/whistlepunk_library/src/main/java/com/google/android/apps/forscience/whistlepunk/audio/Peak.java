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
  private final int mFftIndex;
  private final double mFrequencyEstimate;
  private final double mFftValue;
  private double mFrequency;
  private final List<Harmonic> mHarmonics = new ArrayList<>();

  Peak(int fftIndex, double frequencyEstimate, double fftMagnitude, double fftProminence) {
    mFftIndex = fftIndex;
    mFrequencyEstimate = frequencyEstimate;
    mFftValue = fftMagnitude * fftProminence;
  }

  /**
   * Returns the FFT value for this peak. The FFT value is a value that determined by the FFT
   * magnitude and the prominence of the peak.
   */
  double getFftValue() {
    return mFftValue;
  }

  /**
   * Returns the frequency estimate of this peak, based on which FFT bin corresponds to the peak.
   */
  double getFrequencyEstimate() {
    return mFrequencyEstimate;
  }

  /** Returns the frequency as determined by applying a series of Goertzel filters. */
  double getFrequency() {
    return mFrequency;
  }

  /** Sets the frequency as determined by applying a series of Goertzel filters. */
  void setFrequency(double frequency) {
    mFrequency = frequency;
  }

  /** Adds the given harmonic relationship to the mHarmonics list. */
  void addHarmonic(Harmonic harmonic) {
    mHarmonics.add(harmonic);
  }

  /** Returns the set of harmonic ratio terms that have been identified for this peak. */
  NavigableSet<Integer> getHarmonicTerms() {
    TreeSet<Integer> terms = new TreeSet<>();
    for (Harmonic harmonic : mHarmonics) {
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
    return mFftIndex == peak.mFftIndex;
  }

  @Override
  public int hashCode() {
    return mFftIndex;
  }
}
