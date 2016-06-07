package com.google.android.apps.forscience.whistlepunk;

import com.google.common.collect.Range;

// TODO(saff): convert to non-enum?
enum NextRequestType {
    NONE, FIRST, NEXT_LOWER, NEXT_HIGHER;

    static NextRequestType compute(Range<Long> alreadyRequested, long minWanted, long maxWanted) {
        if (maxWanted < minWanted) {
            return NONE;
        } else if (alreadyRequested == null) {
            return FIRST;
        } else if (alreadyRequested.hasLowerBound() &&
                minWanted < alreadyRequested.lowerEndpoint()) {
            return NEXT_LOWER;
        } else if (alreadyRequested.hasUpperBound() &&
                maxWanted > alreadyRequested.upperEndpoint()) {
            return NEXT_HIGHER;
        } else {
            return NONE;
        }
    }
}
