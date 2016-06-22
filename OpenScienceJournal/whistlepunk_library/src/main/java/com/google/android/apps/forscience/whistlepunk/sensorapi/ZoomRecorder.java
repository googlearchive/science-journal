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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.whistlepunk.RecordingDataController;

/**
 * Stores data at multiple granularities.  For each run of N*2 data points in tier X, there are 2
 * data points in tier X+1, and those are the max and min data points over that run.
 *
 * This seems to allow us to capture the general shape of the graph better than trying to, for
 * example, synthesize an "average" data point for the run.
 */
public class ZoomRecorder {
    /**
     * Statistics key for the number of resolution tiers that have stored data in the
     * Database for the current run.
     */
    public static final String STATS_KEY_TIER_COUNT = "stats_tier_count";

    /**
     * Statistics key for the ratio of data points between resolution tiers.  For example, if this
     * is 10, then for every 10 data points in resolution tier N, there's 1 in tier N+1.
     */
    public static final String STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS = "stats_zoom_level";

    private final String mSensorId;
    private final int mZoomBufferSize;
    private final int mTier;

    private int mSeenThisPass = 0;
    private long mTimestampOfMinSeen;
    private double mValueOfMinSeen;
    private long mTimestampOfMaxSeen;
    private double mValueOfMaxSeen;
    private ZoomRecorder mNextTierUp = null;

    /**
     * @param zoomBufferSize how many data points we can store before sending summary data points to
     *                       the next tier up.  Note that since we send 2 summary points per buffer,
     *                       (max and min), each tier will hold (2 / zoomBufferSize) as many data
     *                       points as the next tier down.
     */
    public ZoomRecorder(String id, int zoomBufferSize, int tier) {
        mSensorId = id;
        mTier = tier;
        mZoomBufferSize = zoomBufferSize;
        resetBuffer();
    }

    public void clear() {
        mNextTierUp = null;
        resetBuffer();
    }

    private void resetBuffer() {
        mSeenThisPass = 0;
        mValueOfMinSeen = Double.MAX_VALUE;
        mValueOfMaxSeen = -Double.MAX_VALUE;
        mTimestampOfMaxSeen = mTimestampOfMinSeen = -1;
    }

    public void addData(long timestampMillis, double value, RecordingDataController dc) {
        mSeenThisPass++;
        if (value > mValueOfMaxSeen) {
            mValueOfMaxSeen = value;
            mTimestampOfMaxSeen = timestampMillis;
        }
        if (value < mValueOfMinSeen) {
            mValueOfMinSeen = value;
            mTimestampOfMinSeen = timestampMillis;
        }
        if (mSeenThisPass == mZoomBufferSize) {
            flush(dc);
        }
    }

    private void addReadingAtThisTier(RecordingDataController dc, long timestamp,
            double value) {
        dc.addScalarReading(mSensorId, mTier, timestamp, value);
        getNextTierUp().addData(timestamp, value, dc);
    }

    private ZoomRecorder getNextTierUp() {
        if (mNextTierUp == null) {
            mNextTierUp = new ZoomRecorder(mSensorId, mZoomBufferSize, mTier + 1);
        }
        return mNextTierUp;
    }

    public int countTiers() {
        if (mNextTierUp == null) {
            // If we don't have a parent, then we haven't written anything at our level yet, so
            // the total count is our tier number
            return mTier;
        } else {
            return mNextTierUp.countTiers();
        }
    }

    public void flushAllTiers(RecordingDataController dc) {
        if (mNextTierUp != null) {
            mNextTierUp.flushAllTiers(dc);
            mNextTierUp = null;
        }
        flush(dc);
    }

    public void flush(RecordingDataController dc) {
        if (mSeenThisPass > 0) {
            // order of adding data to DB doesn't matter
            addReadingAtThisTier(dc, mTimestampOfMinSeen, mValueOfMinSeen);
            addReadingAtThisTier(dc, mTimestampOfMaxSeen, mValueOfMaxSeen);
            resetBuffer();
        }
    }
}
