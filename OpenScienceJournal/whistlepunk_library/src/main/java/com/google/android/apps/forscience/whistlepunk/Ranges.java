package com.google.android.apps.forscience.whistlepunk;

import com.google.common.collect.Range;

public class Ranges {

    static <T extends Comparable<T>> Range<T> span(Range<T> a, Range<T> b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.span(b);
    }
}
