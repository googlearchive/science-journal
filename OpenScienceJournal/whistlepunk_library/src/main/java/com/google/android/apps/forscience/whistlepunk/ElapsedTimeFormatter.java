package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;

/**
 * Formats elapsed time (in seconds) to a Xmin Ys format, like 5min 11s.
 */
public class ElapsedTimeFormatter {

    private static final long SECS_IN_A_MIN = 60;
    private static ElapsedTimeFormatter sInstance;
    private final String mShortFormat;
    private final String mLongFormat;
    private final String mAccessibleShortFormat;
    private final String mAccessibleLongFormat;

    public static ElapsedTimeFormatter getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ElapsedTimeFormatter(context.getApplicationContext());
        }
        return sInstance;
    }

    private ElapsedTimeFormatter(Context context) {
        mShortFormat = context.getResources().getString(R.string.elapsed_time_short_format);
        mLongFormat = context.getResources().getString(R.string.elapsed_time_long_format);
        mAccessibleShortFormat = context.getResources().getString(
                R.string.accessible_elapsed_time_short_format);
        mAccessibleLongFormat = context.getResources().getString(
                R.string.accessible_elapsed_time_long_format);
    }

    public String format(long elapsedTime) {
        if (Math.abs(elapsedTime) >= SECS_IN_A_MIN) {
            return String.format(mLongFormat, elapsedTime / SECS_IN_A_MIN,
                    Math.abs(elapsedTime % SECS_IN_A_MIN));
        } else {
            return String.format(mShortFormat, elapsedTime);
        }
    }

    public String formatForAccessibility(long elapsedTime) {
        if (Math.abs(elapsedTime) >= SECS_IN_A_MIN) {
            return String.format(mAccessibleLongFormat, elapsedTime / SECS_IN_A_MIN,
                    Math.abs(elapsedTime % SECS_IN_A_MIN));
        } else {
            return String.format(mAccessibleShortFormat, elapsedTime);
        }
    }

}
