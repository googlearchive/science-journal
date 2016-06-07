package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.os.Bundle;

import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;

import java.util.ArrayList;
import java.util.List;

public class RecordingSensorObserver implements SensorObserver {
    private List<ScalarReading> mReadings = new ArrayList<>();

    @Override
    public void onNewData(long timestamp, Bundle bundle) {
        mReadings.add(new ScalarReading(timestamp, ScalarSensor.getValue(bundle)));
    }

    public List<ScalarReading> getReadings() {
        return mReadings;
    }
}
