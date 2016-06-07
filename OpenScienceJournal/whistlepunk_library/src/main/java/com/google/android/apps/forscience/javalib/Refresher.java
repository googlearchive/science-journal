package com.google.android.apps.forscience.javalib;

// TODO(saff): synchronize with similar classes from Weather app
public abstract class Refresher {
    private final Scheduler mScheduler;

    private boolean mRefreshScheduled = false;
    private Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            mRefreshScheduled = false;
            refresh();
        }
    };
    private Delay mDelay;

    public Refresher(Scheduler scheduler, Delay delay) {
        mScheduler = scheduler;
        mDelay = delay;
    }

    public void refresh() {
        final boolean rescheduleWouldBeUseful = doRefresh();
        if (rescheduleWouldBeUseful && !mRefreshScheduled) {
            mRefreshScheduled = true;
            mScheduler.schedule(mDelay, mRefreshRunnable);
        }
    }

    /**
     * Does the scheduler-specific work of refreshing
     *
     * @return true iff another refresh should be scheduled.
     */
    protected abstract boolean doRefresh();
}