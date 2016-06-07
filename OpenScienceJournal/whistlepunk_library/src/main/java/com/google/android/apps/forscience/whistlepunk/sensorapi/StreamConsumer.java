package com.google.android.apps.forscience.whistlepunk.sensorapi;

/**
 * Consumes new single-value time-series data points as they are generated.
 */
public interface StreamConsumer {
    /**
     * A new value has been seen.  Caveat implementor: this may be called from any thread.
     */
    void addData(long timestampMillis, double value);
}
