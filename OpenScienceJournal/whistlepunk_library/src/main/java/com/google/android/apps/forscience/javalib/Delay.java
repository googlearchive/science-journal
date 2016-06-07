package com.google.android.apps.forscience.javalib;

import java.util.concurrent.TimeUnit;

public class Delay {
    private final long mDelay;
    private final TimeUnit mUnit;

    public static Delay seconds(long secs) {
        return new Delay(secs, TimeUnit.SECONDS);
    }

    public static Delay millis(int millis) {
        return new Delay(millis, TimeUnit.MILLISECONDS);
    }

    public static Delay micros(int micros) {
        return new Delay(micros, TimeUnit.MICROSECONDS);
    }

    private Delay(long delay, TimeUnit unit) {
        mDelay = delay;
        mUnit = unit;
    }

    public long getDelay() {
        return mDelay;
    }

    public TimeUnit getUnit() {
        return mUnit;
    }

    @Override
    public String toString() {
        return "Delay{" + mDelay + " " + mUnit + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final Delay delay = (Delay) o;

        if (mDelay != delay.mDelay) {
            return false;
        }
        return mUnit == delay.mUnit;

    }

    @Override
    public int hashCode() {
        int result = (int) (mDelay ^ (mDelay >>> 32));
        result = 31 * result + mUnit.hashCode();
        return result;
    }

    public long asMillis() {
        return TimeUnit.MILLISECONDS.convert(mDelay, mUnit);
    }
}
