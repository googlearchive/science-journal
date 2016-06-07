package com.google.android.apps.forscience.whistlepunk;

import java.util.List;

/**
 * Interface for persisting which sensors have been used.
 */
public interface SensorHistoryStorage {
    public List<String> getMostRecentSensorIds();
    public void setMostRecentSensorIds(List<String> ids);
}
