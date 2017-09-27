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

package com.google.android.apps.forscience.whistlepunk;

import android.os.Bundle;
import android.support.annotation.IntDef;

import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of stats for a scalar value changing over time, and informs listeners when they
 * change.
 */
public class StatsAccumulator {
    public static final String KEY_MIN = "stats_min";
    public static final String KEY_MAX = "stats_max";
    public static final String KEY_AVERAGE = "stats_average";
    public static final String KEY_NUM_DATA_POINTS = "stats_count";
    public static final String KEY_TOTAL_DURATION = "stats_total_duration";
    public static final String KEY_STATUS = "status";


    @IntDef({STATUS_VALID, STATUS_NEEDS_UPDATE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface StatStatus{}

    public static final int STATUS_VALID = 0;
    public static final int STATUS_NEEDS_UPDATE = 1;


    public static class StatsDisplay {
        private List<StatsListener> mStatsListeners = new ArrayList<StatsListener>();
        private StreamStat mMinStat;
        private StreamStat mMaxStat;
        private StreamStat mAvgStat;
        private List<StreamStat> mStreamStats = new ArrayList<>();

        public StatsDisplay(NumberFormat numberFormat) {
            mMinStat = new StreamStat(StreamStat.TYPE_MIN, numberFormat);
            mMaxStat = new StreamStat(StreamStat.TYPE_MAX, numberFormat);
            mAvgStat = new StreamStat(StreamStat.TYPE_AVERAGE, numberFormat);
            mStreamStats.add(mMinStat);
            mStreamStats.add(mMaxStat);
            mStreamStats.add(mAvgStat);
        }

        public void clear() {
            mMinStat.clear();
            mMaxStat.clear();
            mAvgStat.clear();
        }

        public void updateFromBundle(SensorObserver.Data bundle) {
            updateStreamStats(bundle.min, bundle.max, bundle.average);
        }

        public List<StreamStat> updateStreamStats(double yMin, double yMax, double average) {
            mMinStat.setValue(yMin);
            mMaxStat.setValue(yMax);
            mAvgStat.setValue(average);

            updateListeners();

            return mStreamStats;
        }

        public void addStatsListener(StatsListener listener) {
            mStatsListeners.add(listener);
        }

        public List<StreamStat> updateStreamStats(TrialStats trialStats) {
            if (trialStats.hasStat(GoosciTrial.SensorStat.MINIMUM)) {
                mMinStat.setValue(trialStats.getStatValue(GoosciTrial.SensorStat.MINIMUM, 0));
            }
            if (trialStats.hasStat(GoosciTrial.SensorStat.MAXIMUM)) {
                mMaxStat.setValue(trialStats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, 0));
            }
            if (trialStats.hasStat(GoosciTrial.SensorStat.AVERAGE)) {
                mAvgStat.setValue(trialStats.getStatValue(GoosciTrial.SensorStat.AVERAGE, 0));
            }

            updateListeners();
            return mStreamStats;
        }

        private void updateListeners() {
            for (int index = 0, count = mStatsListeners.size(); index < count; ++index) {
                mStatsListeners.get(index).onStatsUpdated(mStreamStats);
            }
        }
    }

    // Save information about the recording min, max and sum, as well as when recording
    // began and how many data points we have received, so that the stats calculation can
    // be done very efficiently.
    private double mMin;
    private double mMax;
    private double mSum;

    private long mStartTimestamp = RecordingMetadata.NOT_RECORDING;
    private long mLatestTimestamp = RecordingMetadata.NOT_RECORDING;
    private int mStatSize;

    private String mSensorId;

    public StatsAccumulator(String sensorId) {
        mSensorId = sensorId;
        clearStats();
    }

    // Clears the stream stats.
    public void clearStats() {
        mMin = Double.MAX_VALUE;
        mMax = -Double.MAX_VALUE;
        mSum = 0;
        mStartTimestamp = RecordingMetadata.NOT_RECORDING;
        mLatestTimestamp = RecordingMetadata.NOT_RECORDING;
        mStatSize = 0;
    }

    public boolean isInitialized() {
        return mStatSize > 0;
    }

    // Update the stream stats based on the new timestamp and value.
    // Assumes that all new timestamps acquired are bigger than the recording start time.
    public void updateRecordingStreamStats(long timestampMillis, double value) {
        mLatestTimestamp = timestampMillis;
        mStatSize++;
        if (mStartTimestamp == RecordingMetadata.NOT_RECORDING) {
            mStartTimestamp = timestampMillis;
            mMin = value;
            mMax = value;
            mSum = value;
        } else {
            if (value > mMax) {
                mMax = value;
            } else if (value < mMin) {
                mMin = value;
            }
            mSum = mSum + value;
        }
    }

    private double getAverage() {
        return mSum / mStatSize;
    }

    public long getLatestTimestamp() {
        return mLatestTimestamp;
    }

    public void addStatsToBundle(SensorObserver.Data data) {
        data.min = mMin;
        data.max = mMax;
        data.average = getAverage();
    }

    public void updateDisplayDirectly(StatsDisplay display) {
        final SensorObserver.Data bundle = new SensorObserver.Data();
        addStatsToBundle(bundle);
        display.updateFromBundle(bundle);
    }

    public TrialStats makeSaveableStats() {
        TrialStats stats = new TrialStats(mSensorId);
        populateTrialStats(stats);
        return stats;
    }

    public void populateTrialStats(TrialStats stats) {
        stats.setStatStatus(GoosciTrial.SensorTrialStats.VALID);
        stats.putStat(GoosciTrial.SensorStat.MINIMUM, mMin);
        stats.putStat(GoosciTrial.SensorStat.MAXIMUM, mMax);
        stats.putStat(GoosciTrial.SensorStat.AVERAGE, getAverage());
        stats.putStat(GoosciTrial.SensorStat.NUM_DATA_POINTS, mStatSize);
        stats.putStat(GoosciTrial.SensorStat.TOTAL_DURATION, mLatestTimestamp - mStartTimestamp);
    }
}