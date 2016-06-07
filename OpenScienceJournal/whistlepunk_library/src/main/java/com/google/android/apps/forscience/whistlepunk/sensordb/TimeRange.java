package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.common.collect.Range;

public class TimeRange {
    // TODO(saff): share code with Weather app?

    public enum ObservationOrder {
        NEWEST_FIRST, OLDEST_FIRST
    }

    public static final TimeRange NOW = newest(null);

    public static TimeRange newest(Range<Long> times) {
        return new TimeRange(times, ObservationOrder.NEWEST_FIRST);
    }

    public static TimeRange oldest(Range<Long> times) {
        return new TimeRange(times, ObservationOrder.OLDEST_FIRST);
    }

    private Range<Long> mTimes;
    private ObservationOrder mOrder;

    /**
     * @param times inclusive range of timestamps of interest.  May be Range.all() or null in order
     * to get the most recent observations overall.
     */
    private TimeRange(Range<Long> times, ObservationOrder order) {
        mTimes = times;
        mOrder = order;
    }

    public Range<Long> getTimes() {
        return mTimes;
    }

    public ObservationOrder getOrder() {
        return mOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final TimeRange that = (TimeRange) o;

        if (mOrder != that.mOrder) {
            return false;
        }
        return mTimes.equals(that.mTimes);

    }

    @Override
    public int hashCode() {
        int result = mTimes.hashCode();
        result = 31 * result + mOrder.hashCode();
        return result;
    }
}
