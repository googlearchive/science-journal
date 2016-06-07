package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;

import java.util.List;

public interface StatsListener {
    void onStatsUpdated(List<StreamStat> stats);
}
