package com.google.android.apps.forscience.whistlepunk;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class MemorySensorHistoryStorage implements SensorHistoryStorage {
    List<String> mStored = new ArrayList<>();

    @Override
    public List<String> getMostRecentSensorIds() {
        return Lists.newArrayList(mStored);
    }

    @Override
    public void setMostRecentSensorIds(List<String> ids) {
        mStored.clear();
        mStored.addAll(ids);
    }
}
