package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

public interface ScalarReadingList {
    /**
     * Delivers all of the readings in this list, in order, to the given consumer, on the calling
     * thread.
     */
    void deliver(StreamConsumer c);

    /**
     * Returns the size of the ScalarReadingList.
     */
    int size();
}
