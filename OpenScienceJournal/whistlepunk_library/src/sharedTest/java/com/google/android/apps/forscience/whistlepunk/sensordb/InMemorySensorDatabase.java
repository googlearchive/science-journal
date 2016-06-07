package com.google.android.apps.forscience.whistlepunk.sensordb;

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.DataControllerImpl;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemorySensorDatabase implements SensorDatabase {
    private List<List<Reading>> mReadings = new ArrayList<>();

    @NonNull
    public DataController makeSimpleController(MemoryMetadataManager manager) {
        return makeDataControllerImpl(manager);
    }

    public RecordingDataController makeSimpleRecordingController(
            MemoryMetadataManager memoryMetadataManager) {
        return makeDataControllerImpl(memoryMetadataManager);
    }

    @NonNull
    private DataControllerImpl makeDataControllerImpl(MemoryMetadataManager manager) {
        return new DataControllerImpl(this, MoreExecutors.directExecutor(),
                MoreExecutors.directExecutor(), MoreExecutors.directExecutor(), manager,
                new MonotonicClock());
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
        };
    }

    @Override
    public String getFirstDatabaseTagAfter(long timestamp) {
        return null;
    }

    public List<Reading> getReadings(int resolutionTier) {
        if (resolutionTier >= mReadings.size()) {
            return Collections.emptyList();
        } else {
            return mReadings.get(resolutionTier);
        }
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
