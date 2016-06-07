package com.google.android.apps.forscience.whistlepunk.sensordb;

/**
 * Stores and retrieves sensor data from on-device storage.  All access should be be from a single
 * background thread; all calls are blocking, and do not perform internal synchronization.
 */
public interface SensorDatabase {
    /**
     * See {@link #getScalarReadings(String, TimeRange, int, int)} for semantics of these params
     */
    void addScalarReading(String sensorTag, int resolutionTier, long timestampMillis, double value);

    /**
     * Get stored scalar records
     *
     * @param sensorTag  get records with this tag
     * @param range      specifies both the timestamps of interest, and the order in which records
     *                   should be returned
     * @param resolutionTier the "resolution tier" requested.  Use 0 for all points recorded,
     *                       1 for roughly 10% sample, 2 for 1% sample, etc.
     * @param maxRecords 0 if all records can be returned (may be very big).  If >0, only this
     *                   many records will be returned, starting from the direction given by
     *                   range#getOrder
     * @return a list of readings read from the database
     */
    ScalarReadingList getScalarReadings(String sensorTag, TimeRange range, int resolutionTier,
            int maxRecords);

    /**
     * Find the first sensor reading after {@code timestamp}.  Return the database tag that
     * represents the sensor corresponding to the reading.  This is likely to only be of value
     * as long as we're only recording one sensor at a time.
     */
    String getFirstDatabaseTagAfter(long timestamp);
}
