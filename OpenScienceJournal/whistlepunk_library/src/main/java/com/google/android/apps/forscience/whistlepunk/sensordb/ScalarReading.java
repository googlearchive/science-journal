package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.android.apps.forscience.whistlepunk.TimedEvent;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * A reading from the database.  Comparable based on timestamp only, to find a given timestamp
 * in a list of readings.
 */
public class ScalarReading implements TimedEvent, Comparable<ScalarReading> {
    private final long mTimestampMillis;
    private final double mValue;

    public ScalarReading(long timestampMillis, double value) {
        mTimestampMillis = timestampMillis;
        mValue = value;
    }

    /**
     * Warning: this can use a lot of memory.  Prefer to maintain the ScalarReadingList
     */
    public static List<ScalarReading> slurp(ScalarReadingList list) {
        final List<ScalarReading> readings = new ArrayList<>();
        list.deliver(new StreamConsumer() {
            @Override
            public void addData(long timestampMillis, double value) {
                readings.add(new ScalarReading(timestampMillis, value));
            }
        });
        return readings;
    }

    /**
     * Only use this for testing.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ScalarReading that = (ScalarReading) o;

        if (mTimestampMillis != that.mTimestampMillis) {
            return false;
        }
        return Double.compare(that.mValue, mValue) == 0;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (mTimestampMillis ^ (mTimestampMillis >>> 32));
        temp = Double.doubleToLongBits(mValue);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ScalarReading{" +
                "mTimestampMillis=" + mTimestampMillis +
                ", mValue=" + mValue +
                '}';
    }

    @Override
    public long getCollectedTimeMillis() {
        return mTimestampMillis;
    }

    public double getValue() {
        return mValue;
    }

    @Override
    public int compareTo(ScalarReading another) {
        return Long.compare(mTimestampMillis, another.mTimestampMillis);
    }
}
