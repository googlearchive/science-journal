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

import static com.google.android.apps.forscience.whistlepunk.audio.AudioAnalyzer.BUFFER_SIZE;
import static com.google.android.apps.forscience.whistlepunk.audio.SoundUtils.HIGHEST_PIANO_FREQUENCY;
import static com.google.android.apps.forscience.whistlepunk.audio.SoundUtils.LOWEST_PIANO_FREQUENCY;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Performs FFT (Fast Fourier Transform) and finds peaks.
 */
class FftAnalyzer {
    // TODO(lizlooney): 5 is a hand-picked number that should be set via constructors in
    // AudioAnalyzer and FftAnalyzer.
    private static final int MOVING_AVERAGE_WINDOW_SIZE = 5;
    private static final Comparator<Peak> FFT_VALUE_DESCENDING = (first, second) ->
            (int) Math.signum(second.getFftValue() - first.getFftValue());

    private final int mSampleRateInHz;
    private final int mIndexOfLowestNote;
    private final int mIndexOfHighestNote;
    // Pre-allocated arrays to hold complex numbers (a + bi), and magnitudes.
    private final double[] mA = new double[BUFFER_SIZE];
    private final double[] mB = new double[BUFFER_SIZE];
    private final double[] mMagnitudes;
    private final double[] mMovingAverageValues;
    private final MovingAverage mMovingAverage = new MovingAverage(MOVING_AVERAGE_WINDOW_SIZE);

    FftAnalyzer(int sampleRateInHz) {
        mSampleRateInHz = sampleRateInHz;
        mIndexOfLowestNote = frequencyToIndex(LOWEST_PIANO_FREQUENCY);
        mIndexOfHighestNote = frequencyToIndex(HIGHEST_PIANO_FREQUENCY);
        mMagnitudes = new double[mIndexOfHighestNote + MOVING_AVERAGE_WINDOW_SIZE];
        mMovingAverageValues = new double[mIndexOfHighestNote + MOVING_AVERAGE_WINDOW_SIZE];
    }

    /**
     * Performs FFT (Fast Fourier Transform) and finds peaks in the FFT output. Adds the peaks
     * to the given List. When this method returns, the list is sorted by FFT value, in descending
     * order.
     */
    void findPeaks(short[] samples, List<Peak> peaks) {
        // Copy the samples into the mA array, converting shorts to doubles.
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (i < samples.length) {
                mA[i] = ((double) samples[i]) / Short.MAX_VALUE;
            } else {
                mA[i] = 0.0;
            }
            mB[i] = 0.0;
        }

        // Use FFT to convert the audio signal from time domain to frequency domain.
        // performFft() calculates the FFT in place, modifying the elements of mA and mB arrays.
        // The results of FFT are complex numbers expressed in the form a + bi, where a and b are
        // real numbers and i is the imaginary unit. mA[] will contain the "a" numbers and mB[]
        // will contain the "b" numbers.
        performFft();

        // Calculate the magnitudes.
        // Use a moving average to smooth out the magnitudes.
        mMovingAverage.clear();
        double mean = 0;
        for (int i = mIndexOfLowestNote; i < mMagnitudes.length; i++) {
            // The magnitude of a complex number a + bi, is the square root of (a*a + b*b).
            mMagnitudes[i] = Math.sqrt(mA[i] * mA[i] + mB[i] * mB[i]);
            // Note that mMovingAverageValues[] is skewed since it averages the window of values
            // up to and including [i]. In findIndexOfMaxMagnitude below, we take that into
            // account.
            mMovingAverageValues[i] = mMovingAverage.insertAndReturnAverage(mMagnitudes[i]);
            mean += mMovingAverageValues[i];
        }
        mean /= (mMagnitudes.length - mIndexOfLowestNote);

        // Find peaks.
        for (int i = mIndexOfLowestNote; i <= mIndexOfHighestNote; i++) {
            // TODO(lizlooney): 2.0 is a hand-picked number that should be set via constructors in
            // AudioAnalyzer and FftAnalyzer.
            if (mMovingAverageValues[i] < 2.0 * mean) {
                // Not a peak because the value is too low.
                // Peaks must be at least two times the global mean.
                continue;
            }
            // TODO(lizlooney): 10 is a hand-picked number that should be set via constructors in
            // AudioAnalyzer and FftAnalyzer.
            double prominenceOfPeak = determineProminenceOfPeak(i, mean / 10);
            // TODO(lizlooney): 1.0 is a hand-picked number that should be set via constructors in
            // AudioAnalyzer and FftAnalyzer.
            if (prominenceOfPeak > 1.0) {
                int indexOfMaxMagnitude = findIndexOfMaxMagnitude(i);
                peaks.add(new Peak(indexOfMaxMagnitude, indexToFrequency(indexOfMaxMagnitude),
                        mMagnitudes[indexOfMaxMagnitude], prominenceOfPeak));
            }
        }

        if (peaks.isEmpty()) {
            return;
        }

        // We only need 10 (or fewer) good peaks.
        // A "good" peak is defined as having an FFT value that is greater than or equal to
        // 1/25th of the highest FFT value.
        Collections.sort(peaks, FFT_VALUE_DESCENDING);
        double highestFft = peaks.get(0).getFftValue();
        for (int i = peaks.size() - 1; i > 0; i--) {
            // TODO(lizlooney): 10 and 25 are hand-picked numbers that should be set via
            // constructors in AudioAnalyzer and FftAnalyzer.
            if (i < 10 && peaks.get(i).getFftValue() >= highestFft / 25) {
                break;
            }
            peaks.remove(i);
        }
    }

    private void performFft() {
        // Non-recursive version of the Cooley-Tukey FFT,  base on code from
        // https://introcs.cs.princeton.edu/java/97data/InplaceFFT.java.html

        // Bit reversal permutation.
        int shift = 1 + Integer.numberOfLeadingZeros(BUFFER_SIZE);
        for (int i = 0; i < BUFFER_SIZE; i++) {
            int j = Integer.reverse(i) >>> shift;
            if (j > i) {
                double temp = mA[j];
                mA[j] = mA[i];
                mA[i] = temp;
                temp = mB[j];
                mB[j] = mB[i];
                mB[i] = temp;
            }
        }

        // Butterfly updates.
        for (int l = 2; l <= BUFFER_SIZE; l += l) {
            int lHalf = l / 2;
            for (int k = 0; k < lHalf; k++) {
                double kth = -2 * k * Math.PI / l;
                double wA = Math.cos(kth);
                double wB = Math.sin(kth);
                for (int j = 0; j < BUFFER_SIZE / l; j++) {
                    int index1 = j * l + k + lHalf;
                    int index2 = j * l + k;
                    double xA = mA[index1];
                    double xB = mB[index1];

                    // Multiply complex numbers.
                    // tao = x * w
                    double taoA = xA * wA - xB * wB;
                    double taoB = xA * wB + xB * wA;

                    mA[index1] = mA[index2] - taoA;
                    mB[index1] = mB[index2] - taoB;
                    mA[index2] = mA[index2] + taoA;
                    mB[index2] = mB[index2] + taoB;
                }
            }
        }
    }

    /**
     * Determine the prominence of the peak at the given index. The prominence is determined by
     * the moving average value at the index, compared with the moving average values in the
     * local area.
     */
    private double determineProminenceOfPeak(int index, double boundaryValue) {
        double value = mMovingAverageValues[index];
        // Look at values at lower and higher indices to determine the local area. The
        // boundaries of the local area are values that are less than or equal to the given
        // boundaryValue or greater than the value at index.
        // At the same time, calculate the local mean.
        double localMean = 0;
        int count = 0;
        for (int indexStartArea = index - 1, indexEndArea = index + 1;
                indexStartArea >= 0 && indexEndArea < mMovingAverageValues.length;
                indexStartArea--, indexEndArea++) {
            if (mMovingAverageValues[indexStartArea] <= boundaryValue) {
                break;
            }
            // TODO(lizlooney): 5 is a hand-picked number that should be set via constructors in
            // AudioAnalyzer and FftAnalyzer.
            if (mMovingAverageValues[indexStartArea] > value) {
                if (index - indexStartArea < 5) {
                    // Not a peak because a greater value is nearby.
                    return 0;
                }
                break;
            }
            if (mMovingAverageValues[indexEndArea] <= boundaryValue) {
                break;
            }
            if (mMovingAverageValues[indexEndArea] > value) {
                if (indexEndArea - index < 5) {
                    // Not a peak because a greater value is nearby.
                    return 0;
                }
                break;
            }
            localMean += mMovingAverageValues[indexStartArea]
                    + mMovingAverageValues[indexEndArea];
            count += 2;
        }
        localMean /= count;

        // Avoid unexpected divide by zero.
        if (localMean == 0.0) {
            return 0;
        }
        return value / localMean;
    }

    /**
     * Considering only the magnitudes that contribute to the given moving average index,
     * returns the index of the greatest magnitude.
     */
    private int findIndexOfMaxMagnitude(int index) {
        // Remember that mMovingAverageValues[] is skewed since it averages the window of values up
        // to and including [index].
        int indexOfMaxMagnitude = index;
        double maxMagnitude = mMagnitudes[index];
        for (int i = Math.max(0, index - MOVING_AVERAGE_WINDOW_SIZE + 1);
                i < index; i++) {
            if (mMagnitudes[i] > maxMagnitude) {
                indexOfMaxMagnitude = i;
                maxMagnitude = mMagnitudes[indexOfMaxMagnitude];
            }
        }
        return indexOfMaxMagnitude;
    }

    /**
     * Converts the given FFT bin index to a frequency.
     */
    private double indexToFrequency(double index) {
        return index * mSampleRateInHz / BUFFER_SIZE;
    }

    /**
     * Converts the given frequency to an FFT bin index.
     */
    private int frequencyToIndex(double frequency) {
        return (int) Math.round(frequency * BUFFER_SIZE / mSampleRateInHz);
    }
}
