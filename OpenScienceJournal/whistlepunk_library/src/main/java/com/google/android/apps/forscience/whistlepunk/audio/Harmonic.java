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

/** Represents a harmonic relationship. */
class Harmonic {
  private final Peak peakA;
  private final Peak peakB;
  private int a; // Antecedent or "A" term in the ratio A:B.
  private int b; // Consequent or "B" term in the ratio A:B.

  private Harmonic(Peak peakA, Peak peakB, int a, int b) {
    this.peakA = peakA;
    this.peakB = peakB;
    this.a = a;
    this.b = b;
  }

  /** Returns the peak that is associated with the antecedent term of this harmonic ratio. */
  Peak getPeakA() {
    return peakA;
  }

  /** Returns the peak that is associated with the consequent term of this harmonic ratio. */
  Peak getPeakB() {
    return peakB;
  }

  /** Returns the antecedent term of this harmonic ratio. */
  int getA() {
    return a;
  }

  /** Returns the consequent term of this harmonic ratio. */
  int getB() {
    return b;
  }

  /** Returns the term in this harmonic ratio that is associated with the given peak. */
  int getTermForPeak(Peak peak) {
    if (peak == peakA) {
      return a;
    } else if (peak == peakB) {
      return b;
    }
    throw new IllegalArgumentException("The given peak is not in this harmonic.");
  }

  /** Adjusts the harmonic based on other harmonic relationships. */
  void adjustHarmonic() {
    // Check if the harmonic should be adjusted by multiplying both terms by a constant.
    // For example, if the ratio of peakA to peakB is 1:2 and a ratio of peakA to another
    // peak is 3:X or a ratio of peakB to another peak is 6:Y, then the ratio 1:2 should be
    // adjusted to 3:6. The ratio is the same numeric value, but it indicates a different
    // fundamental frequency.
    Peak peakA = getPeakA();
    Peak peakB = getPeakB();
    int a = getA();
    int b = getB();

    int multiplier = 0;
    for (int term : peakA.getHarmonicTerms().descendingSet()) {
      if (term <= a) {
        // We only care about terms larger than a.
        break;
      }
      if (term % a == 0) {
        multiplier = term / a;
        break;
      }
    }
    for (int term : peakB.getHarmonicTerms().descendingSet()) {
      if (term <= b) {
        // We only care about terms larger than b.
        break;
      }
      if (term % b == 0) {
        int m = term / b;
        if (m > multiplier) {
          multiplier = m;
        }
        break;
      }
    }
    if (multiplier != 0) {
      multiply(multiplier);
    }
  }

  /** Multiplies the antecedent and consequent of this harmonic ratio by the given multiplier. */
  private void multiply(int multiplier) {
    a *= multiplier;
    b *= multiplier;
  }

  /** Creates a Harmonic for the given peaks and adds it to each peak. Returns the new harmonic. */
  static Harmonic addHarmonic(Peak peakA, Peak peakB, int a, int b) {
    Harmonic harmonic = new Harmonic(peakA, peakB, a, b);
    peakA.addHarmonic(harmonic);
    peakB.addHarmonic(harmonic);
    return harmonic;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Harmonic)) {
      return false;
    }

    Harmonic harmonic = (Harmonic) obj;
    return peakA == harmonic.peakA && peakB == harmonic.peakB;
  }

  @Override
  public int hashCode() {
    return peakA.hashCode() * 31 + peakB.hashCode();
  }
}
