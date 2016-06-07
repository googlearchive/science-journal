package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.SensorHistoryStorage;

public class MemorySensorEnvironment implements SensorEnvironment {
    private final RecordingDataController mDataController;
    private FakeBleClient mBleClient;
    private SensorHistoryStorage mHistoryStorage;

    public MemorySensorEnvironment(RecordingDataController dataController, FakeBleClient bleClient,
            SensorHistoryStorage shs) {
        mDataController = dataController;
        mBleClient = bleClient;
        mHistoryStorage = shs;
    }

    @Override
    public RecordingDataController getDataController() {
        return mDataController;
    }

    @Override
    public BleClient getBleClient() {
        return mBleClient;
    }

    @Override
    public Clock getDefaultClock() {
        return null;
    }

    @Override
    public SensorHistoryStorage getSensorHistoryStorage() {
        return mHistoryStorage;
    }

}
