package com.google.android.apps.forscience.javalib;

public interface Scheduler {
    /**
     * schedule a task to happen at a specified time in the future.
     */
    void schedule(Delay delay, Runnable doThis);
}
