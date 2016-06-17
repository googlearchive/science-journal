package com.google.android.apps.forscience.whistlepunk.metadata;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;

import java.util.ArrayList;
import java.util.List;

public class Run {

    private final String mRunId;
    private final int mRunIndex;
    private String mTitle = "";
    private boolean mArchived = false;
    private List<GoosciSensorLayout.SensorLayout> mSensorLayouts;
    private boolean mAutoZoomEnabled;

    public Run(String runId, int runIndex, List<GoosciSensorLayout.SensorLayout> sensorLayouts,
            boolean autoZoomEnabled) {
        mRunId = runId;
        mRunIndex = runIndex;
        mSensorLayouts = sensorLayouts;
        mAutoZoomEnabled = autoZoomEnabled;
    }

    public String getId() {
        return mRunId;
    }

    public int getRunIndex() {
        return mRunIndex;
    }

    public List<String> getSensorIds() {
        List<String> result = new ArrayList<>();
        for (GoosciSensorLayout.SensorLayout layout : mSensorLayouts) {
            result.add(layout.sensorId);
        }
        return result;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public boolean isArchived() {
        return mArchived;
    }

    public void setArchived(boolean archived) {
        mArchived = archived;
    }

    public List<GoosciSensorLayout.SensorLayout> getSensorLayouts() {
        return mSensorLayouts;
    }

    public boolean getAutoZoomEnabled() {
        return mAutoZoomEnabled;
    }

    public void setAutoZoomEnabled(boolean enableAutoZoom) {
        mAutoZoomEnabled = enableAutoZoom;
    }
}