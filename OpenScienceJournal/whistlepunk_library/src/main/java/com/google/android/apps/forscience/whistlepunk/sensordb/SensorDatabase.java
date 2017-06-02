/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.sensordb;

import io.reactivex.Observable;

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

    /**
     * Deletes the scalar records for the given sensor for the given time range.
     */
    void deleteScalarReadings(String sensorTag, TimeRange range);

    Observable<ScalarReading> createScalarObservable(String[] sensorTags, TimeRange range,
            int resolutionTier);
}
