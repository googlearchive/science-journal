package com.google.android.apps.forscience.whistlepunk;

public class CurrentTimeClock implements Clock {
    @Override
    public long getNow() {
        return System.currentTimeMillis();
    }
}
