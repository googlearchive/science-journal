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

package com.google.android.apps.forscience.whistlepunk.audiogen;

import android.os.Handler;
import android.util.Log;
import android.view.ViewTreeObserver;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.RunReviewOverlay;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.List;

public class AudioPlaybackController {

    public interface AudioPlaybackListener {
        void onAudioPlaybackStarted();
        void onTimestampUpdated(long activeTimestamp);
        void onAudioPlaybackStopped();
    }

    public static final String TAG = "AudioPlaybackController";

    public static final int LAST_TONE_DURATION_MS = 10;

    // NOTE: For sensors with more than 100 datapoints in 1 second, these constants may need to be
    // adjusted!
    private static final int DATAPOINTS_PER_AUDIO_PLAYBACK_LOAD = 200;
    private static final int PLAYBACK_STATUS_NOT_PLAYING = 0;
    private static final int PLAYBACK_STATUS_LOADING = 1;
    private static final int PLAYBACK_STATUS_PLAYING = 2;
    private int mPlaybackStatus = PLAYBACK_STATUS_NOT_PLAYING;
    private SimpleJsynAudioGenerator mAudioGenerator;
    private Handler mHandler;
    private Runnable mPlaybackRunnable;
    private AudioPlaybackListener mAudioPlaybackListener;

    private double mYMin;
    private double mYMax;

    public AudioPlaybackController(AudioPlaybackListener listener) {
        mAudioGenerator = new SimpleJsynAudioGenerator();
        mAudioPlaybackListener = listener;
    }

    public void startPlayback(final DataController dataController, final long firstTimestamp,
            final long lastTimestamp, long xMinToLoad, final String sensorId) {
        if (mPlaybackStatus != PLAYBACK_STATUS_NOT_PLAYING) {
            return;
        }
        final long xMax = lastTimestamp;
        final List<ChartData.DataPoint> audioData = new ArrayList<>();

        if (xMinToLoad == RunReviewOverlay.NO_TIMESTAMP_SELECTED) {
            xMinToLoad = firstTimestamp;
        } else {
            if ((xMax - xMinToLoad) < .05 * (xMax - firstTimestamp)) {
                // If we are 95% or more towards the end, start at the beginning.
                // This allows for some slop.
                xMinToLoad = firstTimestamp;
            }
        }

        mHandler = new Handler();
        mPlaybackRunnable = new Runnable() {
            boolean mFullyLoaded = false;
            boolean mLoading = false;

            @Override
            public void run() {
                if (audioData.size() == 0) {
                    if (mFullyLoaded) {
                        stopPlayback();
                    } else {
                        // Wait for more data to come in.
                        mHandler.postDelayed(mPlaybackRunnable, LAST_TONE_DURATION_MS);
                    }
                    return;
                }

                // Every time we play a data point, we remove it from the list.
                ChartData.DataPoint point = audioData.remove(0);
                long timestamp = point.getX();

                // Load more data when needed, i.e. when only 10% of the loaded data points are
                // left in the list, and we aren't fully loaded yet.
                long lastTimestamp = audioData.size() == 0 ? timestamp :
                        audioData.get(audioData.size() - 1).getX();
                if (audioData.size() < DATAPOINTS_PER_AUDIO_PLAYBACK_LOAD / 10 && !mFullyLoaded &&
                        !mLoading) {
                    mLoading = true;
                    dataController.getScalarReadings(sensorId, /* tier 0 */ 0,
                            TimeRange.oldest(Range.openClosed(lastTimestamp, xMax)),
                            DATAPOINTS_PER_AUDIO_PLAYBACK_LOAD,
                            new MaybeConsumer<ScalarReadingList>() {
                                @Override
                                public void success(ScalarReadingList list) {
                                    mLoading = false;
                                    if (list.size() == 0) {
                                        mFullyLoaded = true;
                                    }
                                    audioData.addAll(list.asDataPoints());
                                }

                                @Override
                                public void fail(Exception e) {
                                    Log.e(TAG, "Error loading audio playback data");
                                    stopPlayback();
                                }
                            });
                }

                // Now play the tone, and get set up for the next callback, if one is needed.
                try {
                    mAudioGenerator.addData(timestamp, point.getY(), mYMin, mYMax);
                    if (mAudioPlaybackListener != null) {
                        mAudioPlaybackListener.onTimestampUpdated(timestamp);
                    }
                } finally {
                    // If this is the second to last point, some special handling
                    // needs to be done to determine when to make the last tone.
                    if (audioData.size() > 0) {
                        // Play the next note after the time between this point and the
                        // next point has elapsed.
                        // mPlaybackIndex is now the index of the next point.
                        mHandler.postDelayed(mPlaybackRunnable,
                                audioData.get(0).getX() - timestamp);
                    } else {
                        // The last note gets some duration.
                        mHandler.postDelayed(mPlaybackRunnable, LAST_TONE_DURATION_MS);
                    }
                }
            }
        };

        // Load the first set of scalar readings, and start playing as soon as they are loaded.
        dataController.getScalarReadings(sensorId, /* tier 0 */ 0,
                TimeRange.oldest(Range.closed(xMinToLoad, xMax)),
                DATAPOINTS_PER_AUDIO_PLAYBACK_LOAD, new MaybeConsumer<ScalarReadingList>() {
                    @Override
                    public void success(ScalarReadingList list) {
                        if (mAudioPlaybackListener != null) {
                            audioData.addAll(list.asDataPoints());
                            mAudioGenerator.startPlaying();
                            mPlaybackRunnable.run();
                            mPlaybackStatus = PLAYBACK_STATUS_PLAYING;
                            mAudioPlaybackListener.onAudioPlaybackStarted();
                        } else {
                            stopPlayback();
                        }
                    }

                    @Override
                    public void fail(Exception e) {
                        if (Log.isLoggable(TAG, Log.ERROR)) {
                            Log.e(TAG, "Error loading audio playback data");
                            stopPlayback();
                        }
                    }
                });

        mPlaybackStatus = PLAYBACK_STATUS_LOADING;
    }

    public void stopPlayback() {
        if (mPlaybackStatus == PLAYBACK_STATUS_NOT_PLAYING) {
            return;
        }
        mHandler.removeCallbacks(mPlaybackRunnable);
        mAudioGenerator.stopPlaying();
        mPlaybackStatus = PLAYBACK_STATUS_NOT_PLAYING;
        if (mAudioPlaybackListener != null) {
            mAudioPlaybackListener.onAudioPlaybackStopped();
        }
    }

    public void clearListener() {
        mAudioPlaybackListener = null;
    }

    public boolean isPlaying() {
        return mPlaybackStatus == PLAYBACK_STATUS_PLAYING;
    }

    public boolean isNotPlaying() {
        return mPlaybackStatus == PLAYBACK_STATUS_NOT_PLAYING;
    }

    public void setSonificationType(String sonificationType) {
        mAudioGenerator.setSonificationType(sonificationType);
    }

    public void setYAxisRange(double yMin, double yMax) {
        mYMin = yMin;
        mYMax = yMax;
    }
}
