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

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ZoomRecorder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Metadata object for the stats stored along with a run. This object is to convert TrialStats to
 * key/value pairs which can be read and written to the database.
 */
public class RunStats {
    private static final int TYPE_NOT_FOUND = -1;
    private static final int DEFAULT_VALUE = 0;
    private TrialStats mTrialStats;
    private static Map<String, Integer> keyMap = new HashMap<>();

    public static RunStats fromTrialStats(TrialStats trialStats) {
        if (keyMap.size() == 0) {
            initializeKeyMap();
        }
        RunStats runStats = new RunStats(trialStats);
        return runStats;
    }

    private RunStats(TrialStats trialStats) {
        mTrialStats = trialStats;
    }

    private static void initializeKeyMap() {
        keyMap.put(StatsAccumulator.KEY_AVERAGE, GoosciTrial.SensorStat.AVERAGE);
        keyMap.put(StatsAccumulator.KEY_MIN, GoosciTrial.SensorStat.MINIMUM);
        keyMap.put(StatsAccumulator.KEY_MAX, GoosciTrial.SensorStat.MAXIMUM);
        keyMap.put(StatsAccumulator.KEY_NUM_DATA_POINTS, GoosciTrial.SensorStat.NUM_DATA_POINTS);
        keyMap.put(StatsAccumulator.KEY_TOTAL_DURATION, GoosciTrial.SensorStat.TOTAL_DURATION);
        keyMap.put(ZoomRecorder.STATS_KEY_TIER_COUNT,
                GoosciTrial.SensorStat.ZOOM_PRESENTER_TIER_COUNT);
        keyMap.put(ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS,
                GoosciTrial.SensorStat.ZOOM_PRESENTER_ZOOM_LEVEL_BETWEEN_TIERS);
    }

    public RunStats(String sensorId) {
        mTrialStats = new TrialStats(sensorId);
    }

    public TrialStats getTrialStats() {
        return mTrialStats;
    }

    public void putStat(String key, double value) {
        int type = keyToType(key);
        mTrialStats.putStat(type, value);
    }

    public double getStat(String key) {
        return getStat(key, DEFAULT_VALUE);
    }

    public double getStat(String key, double defaultValue) {
        return mTrialStats.getStatValue(keyToType(key), defaultValue);
    }

    public boolean hasStat(String key) {
        return mTrialStats.hasStat(keyToType(key));
    }

    public int getStatus() {
        if (mTrialStats.statsAreValid()) {
            return StatsAccumulator.STATUS_VALID;
        }
        return StatsAccumulator.STATUS_NEEDS_UPDATE;
    }

    public void setStatus(int newStatus) {
        if (newStatus == StatsAccumulator.STATUS_NEEDS_UPDATE) {
            mTrialStats.setStatStatus(GoosciTrial.SensorTrialStats.NEEDS_UPDATE);
        } else if (newStatus == StatsAccumulator.STATUS_VALID) {
            mTrialStats.setStatStatus(GoosciTrial.SensorTrialStats.VALID);
        }
    }

    public Set<String> getKeys() {
        Set<String> result = new HashSet<>();
        for (String key : keyMap.keySet()) {
            if (mTrialStats.hasStat(keyToType(key))) {
                result.add(key);
            }
        }
        return result;
    }

    private static int keyToType(String key) {
        if (keyMap.size() == 0) {
            initializeKeyMap();
        }
        if (keyMap.containsKey(key)) {
            return keyMap.get(key);
        }
        return TYPE_NOT_FOUND;
    }
}
