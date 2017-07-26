/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.android.apps.forscience.javalib.DataRefresher;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for the Refresher and the DataRefresher classes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class DataRefresherTest {
    // A stream consumer that can return the most recent data and timestamp it received.
    private class MockStreamConsumer implements StreamConsumer {
        private long mTimestamp;
        private double mValue;
        private boolean mDataAdded = false;

        @Override
        public boolean addData(long timestampMillis, double value) {
            mTimestamp = timestampMillis;
            mValue = value;
            mDataAdded = true;
            return true;
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

    private MockStreamConsumer mStreamConsumer = new MockStreamConsumer();
    private MockScheduler mScheduler = new MockScheduler();

    @Test
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

    @Test
    public void testDataRefresherUsesUpdatedValue() {
        DataRefresher dr = makeRefresher();
        dr.setStreamConsumer(mStreamConsumer);
        assertFalse(mStreamConsumer.dataAdded());

        dr.setValue(3.14159);
        dr.startStreaming();
        assertEquals(3.14159, mStreamConsumer.getLastValue(), 0.001);

        dr.setValue(1.618);
        mScheduler.incrementTime(100);

        assertEquals(1.618, mStreamConsumer.getLastValue(), 0.001);

    }

    @Test
    public void testDataRefresherGetsValueWithOverridableGetValueFunction() {
        DataRefresher dr = new DataRefresher(mScheduler, mScheduler.getClock()) {
            @Override
            public double getValue(long now) {
                return 42d;
            }
        };
        dr.setStreamConsumer(mStreamConsumer);
        dr.startStreaming();
        assertEquals(42d, mStreamConsumer.getLastValue(), 0.001);

        mScheduler.incrementTime(100);

        assertEquals(42d, mStreamConsumer.getLastValue(), 0.001);
    }

    @Test
    public void testDataRefresherStopsUpdatingValuesOnStopStreaming() {
        DataRefresher dr = makeRefresher();
        dr.setStreamConsumer(mStreamConsumer);
        dr.setValue(255d);
        dr.startStreaming();

        assertEquals(255d, mStreamConsumer.getLastValue(), 0.001);

        dr.stopStreaming();
        dr.setValue(254d);
        mScheduler.incrementTime(100);

        // The value should not have been updated because we are not streaming.
        assertEquals(255d, mStreamConsumer.getLastValue(), 0.001);
    }

    private DataRefresher makeRefresher() {
        return new DataRefresher(mScheduler, mScheduler.getClock());
    }
}
