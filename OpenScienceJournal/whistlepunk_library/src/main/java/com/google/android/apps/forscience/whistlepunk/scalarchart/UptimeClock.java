package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.os.SystemClock;

import com.google.android.apps.forscience.whistlepunk.Clock;

public class UptimeClock implements Clock {
    @Override
    public long getNow() {
        return SystemClock.uptimeMillis();
    }
}
