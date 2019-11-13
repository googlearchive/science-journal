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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Analyzes audio data to determine the fundamental frequency. */
public class AudioAnalyzer {
  public static final int BUFFER_SIZE = 4096; // Must be a power of 2.
  // TODO(lizlooney): 32.0 is a hand-picked number that should be set via constructor.
  private static final double MINIMUM_NOISE_LEVEL = 32.0;

  private static final Comparator<Peak> FREQUENCY_ASCENDING =
      (first, second) -> (int) Math.signum(first.getFrequency() - second.getFrequency());

  private final FftAnalyzer fftAnalyzer;
  private final GoertzelAnalyzer goertzelAnalyzer;
  private final List<Peak> peaks = new ArrayList<>(20);
  // Map approximate fundamental frequency to list of actual frequencies. We use a TreeMap
  // instead of an Android SparseArray so that this audio code can be transpiled with j2objc.
  private final Map<Integer, List<Double>> mapOfFundamentalFrequencies = new TreeMap<>();

  public AudioAnalyzer(int sampleRateInHz) {
    fftAnalyzer = new FftAnalyzer(sampleRateInHz);
    goertzelAnalyzer = new GoertzelAnalyzer(sampleRateInHz);
  }

  public Double detectFundamentalFrequency(short[] samples) {
    peaks.clear();
    mapOfFundamentalFrequencies.clear();

    // Don't bother trying to determine the frequency if the buffer is half (or
    // more) filled with zeros or if the volume is too low to hear.
    int countZeros = 0;
    for (short s : samples) {
      if (s == 0) {
        countZeros++;
      }
    }
    if (countZeros >= samples.length / 2) {
      return null;
    }
    double uncalibratedDecibels = SoundUtils.calculateUncalibratedDecibels(samples, samples.length);
    if (uncalibratedDecibels < MINIMUM_NOISE_LEVEL) {
      return null;
    }

    fftAnalyzer.findPeaks(samples, peaks);
    // At this point, peaks is sorted by FFT value, in descending order.
    if (peaks.isEmpty()) {
      return null;
    }

    // Use Goertzel analyzer to more accurately determine the frequency of each peak.
    for (Peak peak : peaks) {
      double frequency =
          goertzelAnalyzer.findFrequencyWithHighestPower(samples, peak.getFrequencyEstimate());
      peak.setFrequency(frequency);
    }

    Peak tallestPeak = peaks.get(0);
    if (peaks.size() == 1) {
      return tallestPeak.getFrequency();
    }

    // Determine the fundamental frequency by examining harmonic ratios.
    Double fundamentalFrequency = determineFundamentalFrequencyFromHarmonics();

    if (fundamentalFrequency == null) {
      // No harmonics were recognized. Return the frequency of the tallest peak.
      fundamentalFrequency = tallestPeak.getFrequency();
    }

    return fundamentalFrequency;
  }

  /**
   * Determines the fundamental frequency by examining the harmonic ratios between peaks in the FFT
   * output. Returns null if no harmonic ratios are identified.
   */
  private Double determineFundamentalFrequencyFromHarmonics() {
    Collections.sort(peaks, FREQUENCY_ASCENDING);

    // Look for harmonic ratios to determine the fundamental frequency.
    List<Harmonic> harmonics = new ArrayList<>();
    // We look for harmonics 1 through 8, and even more if there are more than 8 peaks.
    // TODO(lizlooney): 8 is a hand-picked number that should be set via constructor.
    int maxHarmonic = Math.max(8, peaks.size());
    // Look at the ratios between peak frequencies.
    for (int i = 0; i < peaks.size(); i++) {
      Peak peakI = peaks.get(i);
      // Find harmonics between peakI and other peaks.
      for (int j = i + 1; j < peaks.size(); j++) {
        Peak peakJ = peaks.get(j);
        Harmonic harmonic = identifyHarmonicRatio(peakI, peakJ, maxHarmonic);
        if (harmonic != null) {
          harmonics.add(harmonic);
        }
      }
    }

    if (harmonics.isEmpty()) {
      return null;
    }

    // Adjust harmonics.
    for (Harmonic harmonic : harmonics) {
      harmonic.adjustHarmonic();
    }

    // Calculate the fundamental frequency for each harmonic relationship.
    for (Harmonic harmonic : harmonics) {
      addFundamentalFrequencyToMap(harmonic);
    }

    return chooseBestFundamentalFrequency();
  }

  /**
   * Identifies the harmonic ratio between the two given peaks. Returns null if no harmonic ratio is
   * found.
   */
  private Harmonic identifyHarmonicRatio(Peak peakI, Peak peakJ, int maxHarmonic) {
    double ratio = peakJ.getFrequency() / peakI.getFrequency();

    boolean foundHarmonicRatio = false;
    int termI = 0;
    int termJ = 0;
    double smallestError = Integer.MAX_VALUE;
    for (int a = 1; a < maxHarmonic; a++) {
      for (int b = a + 1; b <= maxHarmonic; b++) {
        // Skip ratios if we've already looked at an equivalent ratio.
        // For instance, we skip 2:4 because we already looked at 1:2.
        if (gcd(a, b) != 1) {
          continue;
        }
        double r = ((double) b) / a;
        double error = Math.abs(ratio - r);
        // TODO(lizlooney): 0.01 is a hand-picked number that should be set via
        // constructor.
        if (error <= 0.01) {
          if (error < smallestError) {
            smallestError = error;
            termI = a;
            termJ = b;
            foundHarmonicRatio = true;
          }
        }
      }
    }
    if (foundHarmonicRatio) {
      return Harmonic.addHarmonic(peakI, peakJ, termI, termJ);
    }
    return null;
  }

  /** Returns the greatest common divisor of the given integers. */
  private static int gcd(int a, int b) {
    while (b > 0) {
      int temp = b;
      b = a % b;
      a = temp;
    }
    return a;
  }

  /**
   * Calculates the fundamental frequencies indicated by the given harmonic relationship and adds
   * them to the mapOfFundamentalFrequencies.
   */
  private void addFundamentalFrequencyToMap(Harmonic harmonic) {
    Peak peakA = harmonic.getPeakA();
    Peak peakB = harmonic.getPeakB();
    int a = harmonic.getA();
    int b = harmonic.getB();
    addFundamentalFrequencyToMap(peakA.getFrequency() / a);
    addFundamentalFrequencyToMap(peakB.getFrequency() / b);
  }

  /** Adds the given fundamentalFrequency to the mapOfFundamentalFrequencies. */
  private void addFundamentalFrequencyToMap(double fundamentalFrequency) {
    // Determine the appropriate Integer key, based on the given fundamentalFrequency.
    int roundedFrequency = (int) Math.round(fundamentalFrequency);
    // Look for existing keys close to the rounded frequency.
    List<Double> frequencies = null;
    // TODO(lizlooney): try using mapOfFundamentalFrequencies.headMap and
    // mapOfFundamentalFrequencies.tailMap to look at closest neighbors.
    for (Map.Entry<Integer, List<Double>> entry : mapOfFundamentalFrequencies.entrySet()) {
      Integer key = entry.getKey();
      // TODO(lizlooney): Try using a ratio instead of a difference to check if key and
      // roundedFrequency are close. If that works well, any hand-picked numbers should be
      // set via constructor.
      if (Math.abs(key - roundedFrequency) < 10) {
        frequencies = entry.getValue();
        break;
      }
    }
    if (frequencies == null) {
      // No keys are close enough to roundedFrequency.
      frequencies = new ArrayList<>(10);
      mapOfFundamentalFrequencies.put(roundedFrequency, frequencies);
    }
    frequencies.add(fundamentalFrequency);
  }

  /**
   * Chooses the best fundamental frequency based on what has been added to the
   * mapOfFundamentalFrequencies.
   */
  private double chooseBestFundamentalFrequency() {
    // mapOfFundamentalFrequencies contains one or more approximate frequencies (keys),
    // each of which is associated with one or more actual frequencies. The number of
    // actual frequencies corresponds to the number of harmonic ratios that indicate that
    // the frequency is the fundamental frequency.
    // Look for the one with the most actual frequencies.
    // Because mapOfFundamentalFrequencies is sorted (it's a TreeMap), if there are two
    // approximate frequencies with the same number of actual frequencies, we will choose
    // the one with the lower approximate frequency.
    List<Double> bestFrequencies = Collections.emptyList();
    for (Map.Entry<Integer, List<Double>> entry : mapOfFundamentalFrequencies.entrySet()) {
      List<Double> frequencies = entry.getValue();
      if (frequencies.size() > bestFrequencies.size()) {
        bestFrequencies = frequencies;
      }
    }

    // bestFrequencies contains one or more actual frequencies that are close to the
    // fundamental frequency. Calculate the mean. That's a good estimate of the fundamental
    // frequency.
    double meanFrequency = 0;
    for (double f : bestFrequencies) {
      meanFrequency += f;
    }
    meanFrequency /= bestFrequencies.size();
    return meanFrequency;
  }
}
