package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;

/**
 * Formats elapsed time (in seconds) to a short m:ss or h:mm:ss format.
 */
public class ElapsedTimeAxisFormatter {

    private static final long SEC_IN_MIN = 60;
    private static final long MIN_IN_HOUR = 60;

    private static ElapsedTimeAxisFormatter sInstance;
    private final String mSmallFormat;
    private final String mLargeFormat;

    public static ElapsedTimeAxisFormatter getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ElapsedTimeAxisFormatter(context);
        }
        return sInstance;
    }

    private ElapsedTimeAxisFormatter(Context context) {
        mSmallFormat = context.getResources().getString(R.string.elapsed_time_axis_format_small);
        mLargeFormat = context.getResources().getString(R.string.elapsed_time_axis_format_large);
    }

    public String format(long elapsedTime) {
        boolean isNegative = elapsedTime < 0;
        elapsedTime = Math.abs(elapsedTime);
        long hours = elapsedTime / (SEC_IN_MIN * MIN_IN_HOUR);
        long mins = (elapsedTime - hours * SEC_IN_MIN * MIN_IN_HOUR) / SEC_IN_MIN;
        long secs = elapsedTime - hours * SEC_IN_MIN * MIN_IN_HOUR - mins * SEC_IN_MIN;

        String result;
        if (hours > 0) {
            result = String.format(mLargeFormat, hours, mins, secs);
        } else {
            result = String.format(mSmallFormat, mins, secs);
        }
        return (isNegative ? "-" : "") + result;
    }

}
