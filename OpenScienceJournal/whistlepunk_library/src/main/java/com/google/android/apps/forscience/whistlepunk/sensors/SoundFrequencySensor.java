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

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.softsynth.math.FourierMath;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Displays sound frequency in Hertz (Hz).
 */
public class SoundFrequencySensor extends ScalarSensor {
    private static final int SAMPLE_RATE_IN_HZ = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final String ID = "SoundFrequencySource";
    private AudioRecord mRecord;
    private final ExecutorService mExecutor;
    private final int mMinBufferSize;
    private final int mBytesInBuffer;
    private final int mShortsInBuffer;
    private AtomicBoolean mRunning = new AtomicBoolean(false);

    public SoundFrequencySensor() {
        super(ID);
        mMinBufferSize =
                AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bytesInBuffer = 0;
        int shortsInBuffer = 0;
        if (mMinBufferSize > 0)  {
            shortsInBuffer = mMinBufferSize / 2;
            // Increase mShortsInBuffer to power of 2, if necessary.
            if ((shortsInBuffer & (shortsInBuffer - 1)) != 0) {
                shortsInBuffer = Integer.highestOneBit(shortsInBuffer) << 1;
            }
            bytesInBuffer = shortsInBuffer * 2;
        }
        this.mBytesInBuffer = bytesInBuffer;
        this.mShortsInBuffer = shortsInBuffer;
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected SensorRecorder makeScalarControl(final StreamConsumer c,
            final SensorEnvironment environment, Context context,
            final SensorStatusListener listener) {
        final Clock clock = environment.getDefaultClock();
        return new AbstractSensorRecorder() {
            @Override
            public void startObserving() {
                // TODO(lizlooney): Extract common logic from here and DecibelSensor to a place it
                // can be reused.
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
                if (mMinBufferSize < 0) {
                    // If this is the case, AudioRecord.getMinBufferSize returned an error.
                    listener.onSourceError(getId(), SensorStatusListener.ERROR_FAILED_TO_CONNECT,
                            "Could not connect to microphone");
                    return;
                }
                mRunning.set(true);
                // Use VOICE_COMMUNICATION to filter out audio coming from the speakers
                mRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, mBytesInBuffer);
                if (mRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    listener.onSourceError(getId(), SensorStatusListener.ERROR_FAILED_TO_CONNECT,
                            "Could not connect to microphone");
                    return;
                }
                mRecord.startRecording();
                // Check to see if we actually started recording before continuing.
                // AudioRecord#startRecording() logs an error but it has no return value and
                // doesn't throw an exception when someone else is using the mic.
                if (mRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    listener.onSourceError(getId(), SensorStatusListener.ERROR_FAILED_TO_CONNECT,
                            "Microphone in use by another application");
                    return;
                }
                mExecutor.execute(new Runnable() {
                    private FftAnalyzer fftAnalyzer;

                    @Override
                    public void run() {
                        short[] tempBuffer = new short[mShortsInBuffer];

                        fftAnalyzer = new FftAnalyzer(tempBuffer.length);

                        int readShorts = 0;
                        while (mRunning.get()) {
                            readShorts += mRecord.read(
                                    tempBuffer, readShorts, tempBuffer.length - readShorts);
                            if (readShorts == tempBuffer.length) {
                                sendBuffer(tempBuffer);
                                readShorts = 0;
                            }
                        }
                    }

                    private void sendBuffer(short[] tempBuffer) {
                        final long timestampMillis = clock.getNow();
                        double frequency = fftAnalyzer.determineFrequency(tempBuffer);
                        c.addData(timestampMillis, frequency);
                    }
                });
            }

            @Override
            public void stopObserving() {
                mRunning.set(false);
                if (mRecord != null) {
                    if (mRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        mRecord.stop();
                    }
                    mRecord.release();
                }
                mRecord = null;
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
            }

            @Override
            public void applyOptions(ReadableSensorOptions settings) {
                // do nothing, no settings apply to collection
            }
        };
    }

    /**
     * Determines the frequency by using FFT (Fast Fourier Transform) and looking for the frequency
     * with the largest magnitude.
     */
    private static class FftAnalyzer {
        private final int length;
        // Preallocated arrays to hold complex numbers (a + bi), and frequency magnitudes.
        private final double[] a;
        private final double[] b;
        private final double[] magnitudes;

        private FftAnalyzer(int length) {
            // Make sure n is a power of 2.
            if ((length & (length - 1)) != 0) {
                throw new RuntimeException("FFT sampling size must be power of 2");
            }

            this.length = length;
            a = new double[length];
            b = new double[length];
            magnitudes = new double[length];
        }

        private double determineFrequency(short[] samples) {
            if (samples.length != length) {
                throw new RuntimeException(
                        "Samples length is " + samples.length + ". Expected " + length + ".");
            }

            // Copy the samples into the a array, converting shorts to doubles.
            for (int i = 0; i < length; i++) {
                a[i] = ((double) samples[i]) / Short.MAX_VALUE;
                b[i] = 0.0;
            }

            // Use FFT.
            FourierMath.fft(length, a, b);

            // Calculate the magnitudes of different frequencies.
            FourierMath.calculateMagnitudes(a, b, magnitudes);

            // Find the frequency with the maximum magnitude.
            double maxMagnitude = 0;
            int indexOfMax = 0;
            for (int i = 0; i < length / 2; i++) {
                if (magnitudes[i] > maxMagnitude) {
                    maxMagnitude = magnitudes[i];
                    indexOfMax = i;
                }
            }
            // Convert index to frequency.
            return ((double) indexOfMax) * SAMPLE_RATE_IN_HZ / length;
        }
    }
}
