package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;

/**
 * Data interface for sensor recorders
 */
public interface RecordingDataController {
    /**
     * @see com.google.android.apps.forscience.whistlepunk.sensordb.SensorDatabase#addScalarReading(String, int, long, double)
     */
    void addScalarReading(String sensorId, final int resolutionTier, long timestampMillis,
            double value);

    /**
     * Set the statistics for the given run and sensor
     *
     * @param runId (previously startLabelId) identifies the run
     */
    void setStats(String runId, String sensorId, RunStats runStats);

    /**
     * If an error is encountered storing data or stats for {@code sensorId}, notify {@code
     * listener}
     */
    void setDataErrorListenerForSensor(String sensorId, FailureListener listener);

    /**
     * Clear listener set by earlier call to {@code setDataErrorListener}
     */
    void clearDataErrorListenerForSensor(String sensorId);
}
