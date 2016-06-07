package com.google.android.apps.forscience.whistlepunk.sensors;

import android.os.Handler;
import android.os.Looper;

import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.Scheduler;

/**
 * Schedules tasks using built-in Android looper
 */
public class SystemScheduler implements Scheduler {
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void schedule(Delay delay, Runnable doThis) {
        mHandler.postDelayed(doThis, delay.asMillis());
    }
}
