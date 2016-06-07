package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.SensorHistoryStorage;

/**
 * Encapsulates services that sensors need to do their jobs
 */
public interface SensorEnvironment {
    RecordingDataController getDataController();
    BleClient getBleClient();

    /**
     * @return report values using this clock, _unless_ the sensor has a better built-in
     * timestamp to use (for example, given skew, it's better to believe the
     * timestamps in the BLE packets than the clock value)
     */
    Clock getDefaultClock();

    SensorHistoryStorage getSensorHistoryStorage();
}
