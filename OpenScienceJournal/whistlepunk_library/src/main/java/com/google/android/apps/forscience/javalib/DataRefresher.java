package com.google.android.apps.forscience.javalib;

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

/**
 * A data refresher which publishes the last known data value when streaming.
 */
public class DataRefresher extends Refresher {
    private static final int SENSOR_REFRESH_RATE = 100;  // Refresh rate in ms.

    protected StreamConsumer streamConsumer;
    protected boolean streaming = false;
    private double mValue;
    private Clock mClock;

    public DataRefresher(Scheduler scheduler, Clock clock) {
        super(scheduler, Delay.millis(SENSOR_REFRESH_RATE));
        mClock = clock;
    }

    public void setStreamConsumer(StreamConsumer consumer) {
        this.streamConsumer = consumer;
    }

    public void startStreaming() {
        if (!streaming) {
            streaming = true;
            refresh();
        }
    }

    public void stopStreaming() {
        streaming = false;
    }

    public void setValue(double value) {
        if (value != mValue) {
            mValue = value;
            refresh();
        }
    }

    public double getValue(long now) {
        return mValue;
    }

    @Override
    protected boolean doRefresh() {
        if (streaming && streamConsumer != null) {
            long now = mClock.getNow();
            streamConsumer.addData(now, getValue(now));
        }
        return streaming;
    }
}
