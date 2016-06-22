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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Metadata object for the stats stored along with a run
 */
public class RunStats {
    Map<String, Double> mStats = new HashMap<>();

    public void putStat(String key, double value) {
        mStats.put(key, value);
    }

    public Set<String> getKeys() {
        return mStats.keySet();
    }

    public double getStat(String key) {
        return mStats.get(key);
    }

    public double getStat(String key, double defaultValue) {
        if (mStats.containsKey(key)) {
            return mStats.get(key);
        } else {
            return defaultValue;
        }
    }

    public boolean hasStat(String key) {
        return mStats.containsKey(key);
    }

    public int getIntStat(String key) {
        return (int) Math.round(getStat(key));
    }
}
