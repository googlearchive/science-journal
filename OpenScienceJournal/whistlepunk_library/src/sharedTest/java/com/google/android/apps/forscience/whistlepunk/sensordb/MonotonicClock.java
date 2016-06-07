package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.android.apps.forscience.whistlepunk.Clock;

public class MonotonicClock implements Clock {
    long mNow = 1;

    @Override
    public long getNow() {
        return mNow++;
    }
}
