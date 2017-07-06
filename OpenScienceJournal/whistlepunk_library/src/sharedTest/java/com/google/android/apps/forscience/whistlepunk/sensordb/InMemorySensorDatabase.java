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

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.DataControllerImpl;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;

public class InMemorySensorDatabase implements SensorDatabase {
    private List<List<Reading>> mReadings = new ArrayList<>();

    public static DataControllerImpl makeSimpleController() {
        return new InMemorySensorDatabase().makeSimpleController(new MemoryMetadataManager());
    }

    public DataControllerImpl makeSimpleController(MemoryMetadataManager mmm) {
        return makeSimpleController(mmm, new HashMap<>());
    }

    public DataControllerImpl makeSimpleController(MemoryMetadataManager mmm, Clock clock) {
        return makeDataControllerImpl(mmm, new HashMap<>(), clock);
    }

    @NonNull
    public DataControllerImpl makeSimpleController(MemoryMetadataManager manager,
            Map<String, SensorProvider> providerMap) {
        return makeDataControllerImpl(manager, providerMap, new MonotonicClock());
    }

    public RecordingDataController makeSimpleRecordingController(
            MemoryMetadataManager memoryMetadataManager) {
        return makeDataControllerImpl(memoryMetadataManager, new HashMap<>(), new MonotonicClock());
    }

    @NonNull
    private DataControllerImpl makeDataControllerImpl(MemoryMetadataManager manager,
            Map<String, SensorProvider> providerMap, Clock clock) {
        return new DataControllerImpl(this, MoreExecutors.directExecutor(),
                MoreExecutors.directExecutor(), MoreExecutors.directExecutor(), manager,
                clock, providerMap, new ConnectableSensor.Connector(providerMap));
    }

    @Override
    public void addScalarReading(String databaseTag, int resolutionTier, long timestampMillis,
            double value) {
        getTierReadings(resolutionTier).add(new Reading(databaseTag, timestampMillis, value));
    }

    private List<Reading> getTierReadings(int resolutionTier) {
        while (resolutionTier >= mReadings.size()) {
            mReadings.add(new ArrayList<Reading>());
        }
        return mReadings.get(resolutionTier);
    }

    @Override
    public ScalarReadingList getScalarReadings(String sensorTag, TimeRange range,
            int resolutionTier, int maxRecords) {
        final List<ScalarReading> readingsToReturn = new ArrayList<>();
        for (Reading reading : getReadings(resolutionTier)) {
            if (range.getTimes().contains(reading.getTimestampMillis())) {
                readingsToReturn.add(
                        new ScalarReading(reading.getTimestampMillis(), reading.getValue()));
            }
        }
        return new ScalarReadingList() {
            @Override
            public void deliver(StreamConsumer c) {
                for (ScalarReading scalarReading : readingsToReturn) {
                    c.addData(scalarReading.getCollectedTimeMillis(), scalarReading.getValue());
                }
            }

            @Override
            public int size() {
                return readingsToReturn.size();
            }

            @Override
            public List<ChartData.DataPoint> asDataPoints() {
                List<ChartData.DataPoint> result = new ArrayList<>();
                for (ScalarReading scalarReading : readingsToReturn) {
                    result.add(new ChartData.DataPoint(
                            scalarReading.getCollectedTimeMillis(), scalarReading.getValue()));
                }
                return result;
            }
        };
    }

    @Override
    public String getFirstDatabaseTagAfter(long timestamp) {
        return null;
    }

    @Override
    public void deleteScalarReadings(String sensorTag, TimeRange range) {
        for (List<Reading> readingList : mReadings) {
            for (int index = readingList.size() - 1; index >= 0; --index) {
                Reading reading = readingList.get(index);
                if (reading.getDatabaseTag().equals(sensorTag)
                        && range.getTimes().contains(reading.getTimestampMillis())) {
                    readingList.remove(index);
                }
            }
        }
    }

    @Override
    public Observable<ScalarReading> createScalarObservable(String[] sensorTags, TimeRange range,
            int resolutionTier) {
        return null;
    }

    public List<Reading> getReadings(int resolutionTier) {
        if (resolutionTier >= mReadings.size()) {
            return Collections.emptyList();
        } else {
            return mReadings.get(resolutionTier);
        }
    }

    public RecordingDataController makeSimpleRecordingController() {
        return makeSimpleRecordingController(new MemoryMetadataManager());
    }

    @VisibleForTesting
    public static class Reading {
        private final String mDatabaseTag;
        private final long mTimestampMillis;
        private final double mValue;

        public Reading(String databaseTag, long timestampMillis, double value) {
            mDatabaseTag = databaseTag;
            mTimestampMillis = timestampMillis;
            mValue = value;
        }

        public String getDatabaseTag() {
            return mDatabaseTag;
        }

        public long getTimestampMillis() {
            return mTimestampMillis;
        }

        public double getValue() {
            return mValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final Reading reading = (Reading) o;

            if (mTimestampMillis != reading.mTimestampMillis) {
                return false;
            }
            if (Double.compare(reading.mValue, mValue) != 0) {
                return false;
            }
            return !(mDatabaseTag != null ? !mDatabaseTag.equals(
                    reading.mDatabaseTag) : reading.mDatabaseTag != null);

        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = mDatabaseTag != null ? mDatabaseTag.hashCode() : 0;
            result = 31 * result + (int) (mTimestampMillis ^ (mTimestampMillis >>> 32));
            temp = Double.doubleToLongBits(mValue);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        /**
         * Results are only meant to be useful in test failure reports
         */
        @Override
        public String toString() {
            return "Reading{" +
                    "mDatabaseTag='" + mDatabaseTag + '\'' +
                    ", mTimestampMillis=" + mTimestampMillis +
                    ", mValue=" + mValue +
                    '}';
        }
    }
}
