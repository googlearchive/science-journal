package com.google.android.apps.forscience.whistlepunk.sensorapi;

/**
 * Takes a stream of scalar values, and produces an altered stream of scalar values having the same
 * timestamps.  Can be used to do simple frequency extraction, or re-scale units.
 */
public interface ValueFilter {
    ValueFilter IDENTITY = new ValueFilter() {
        @Override
        public double filterValue(long timestamp, double value) {
            return value;
        }
    };

    double filterValue(long timestamp, double value);
}
