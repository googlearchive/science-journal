package com.google.android.apps.forscience.whistlepunk;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.DataRefresher;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

import java.util.ArrayList;

/**
 * Tests for the Refresher and the DataRefresher classes.
 */
public class DataRefresherTest extends AndroidTestCase {

    // A stream consumer that can return the most recent data and timestamp it received.
    public class MockStreamConsumer implements StreamConsumer {
        private long mTimestamp;
        private double mValue;
        private boolean mDataAdded = false;

        @Override
        public void addData(long timestampMillis, double value) {
            mTimestamp = timestampMillis;
            mValue = value;
            mDataAdded = true;
        }

        public long getLastTimestamp() {
            return mTimestamp;
        }

        public double getLastValue() {
            return mValue;
        }

        private boolean dataAdded() {
            return mDataAdded;
        }
    }

    // A mock scheduler that executes runnables after a delay.
    // Note that this does may not run the runnables in order of when they were
    // scheduled to be run: After time is incremented, any applicable runnable is run
    // immediately.
    public class MockScheduler implements Scheduler {
        class QueuedRunnable {
            public long executeAfter;
            public Runnable runnable;
        }

        private long mCurrentTime = 0;
        private ArrayList<QueuedRunnable> mRunnables = new ArrayList<QueuedRunnable>();

        @Override
        public void schedule(Delay delay, Runnable doThis) {
            if (delay.asMillis() == 0) {
                doThis.run();
            } else {
                // Add to list of runnables
                QueuedRunnable qr = new QueuedRunnable();
                qr.executeAfter = delay.asMillis() + mCurrentTime;
                qr.runnable = doThis;
                mRunnables.add(qr);
            }
        }

        public Clock getClock() {
            return new Clock() {
                @Override
                public long getNow() {
                    return mCurrentTime;
                }
            };
        }

        private void incrementTime(long ms) {
            mCurrentTime += ms;
            // Check which runnables can be executed and removed from the list.
            for (QueuedRunnable qr : mRunnables) {
                if (qr.executeAfter <= mCurrentTime) {
                    qr.runnable.run();
                    mRunnables.remove(qr);
                }
            }
        }
    }

    private MockStreamConsumer mStreamConsumer;
    private MockScheduler mScheduler;

    public void testDataRefresherDoesNotRefreshWhenNotStreaming() {
        DataRefresher dr = makeRefresher();
        dr.setStreamConsumer(mStreamConsumer);
        dr.setValue(2.718);
        assertFalse(mStreamConsumer.dataAdded());

        mScheduler.incrementTime(100);

        assertFalse(mStreamConsumer.dataAdded());
        dr.stopStreaming();
        assertFalse(mStreamConsumer.dataAdded());

        mScheduler.incrementTime(100);

        assertFalse(mStreamConsumer.dataAdded());
    }

    public void testDataRefresherUsesUpdatedValue() {
        DataRefresher dr = makeRefresher();
        dr.setStreamConsumer(mStreamConsumer);
        assertFalse(mStreamConsumer.dataAdded());

        dr.setValue(3.14159);
        dr.startStreaming();
        assertEquals(3.14159, mStreamConsumer.getLastValue());

        dr.setValue(1.618);
        mScheduler.incrementTime(100);

        assertEquals(1.618, mStreamConsumer.getLastValue());

    }

    public void testDataRefresherGetsValueWithOverridableGetValueFunction() {
        DataRefresher dr = new DataRefresher(mScheduler, mScheduler.getClock()) {
            @Override
            public double getValue(long now) {
                return 42d;
            }
        };
        dr.setStreamConsumer(mStreamConsumer);
        dr.startStreaming();
        assertEquals(42d, mStreamConsumer.getLastValue());

        mScheduler.incrementTime(100);

        assertEquals(42d, mStreamConsumer.getLastValue());
    }

    public void testDataRefresherStopsUpdatingValuesOnStopStreaming() {
        DataRefresher dr = makeRefresher();
        dr.setStreamConsumer(mStreamConsumer);
        dr.setValue(255d);
        dr.startStreaming();

        assertEquals(255d, mStreamConsumer.getLastValue());

        dr.stopStreaming();
        dr.setValue(254d);
        mScheduler.incrementTime(100);

        // The value should not have been updated because we are not streaming.
        assertEquals(255d, mStreamConsumer.getLastValue());
    }

    private DataRefresher makeRefresher() {
        return new DataRefresher(mScheduler, mScheduler.getClock());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mStreamConsumer = new MockStreamConsumer();
        mScheduler = new MockScheduler();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }


}
