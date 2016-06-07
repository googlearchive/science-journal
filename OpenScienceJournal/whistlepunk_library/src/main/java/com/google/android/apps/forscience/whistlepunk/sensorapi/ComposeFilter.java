package com.google.android.apps.forscience.whistlepunk.sensorapi;

/**
 * Creates a filter that applies two filters in order
 */
public class ComposeFilter {
    public static ValueFilter applyInOrder(final ValueFilter first, final ValueFilter second) {
        return new ValueFilter() {
            @Override
            public double filterValue(long timestamp, double value) {
                double firstValue = first.filterValue(timestamp, value);
                return second.filterValue(timestamp, firstValue);
            }
        };
    }
}
