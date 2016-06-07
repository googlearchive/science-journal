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
