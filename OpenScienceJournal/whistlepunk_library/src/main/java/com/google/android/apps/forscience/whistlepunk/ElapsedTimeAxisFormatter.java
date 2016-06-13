package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.res.Resources;

/**
 * Formats elapsed time (in ms) to a short m:ss or h:mm:ss format.
 * Can also format to tenths of a second using formatToTenths.
 */
public class ElapsedTimeAxisFormatter {

    private static final long SEC_IN_MIN = 60;
    private static final long MIN_IN_HOUR = 60;
    private static final long TENTHS_IN_SEC = 10;
    private static final long MS_IN_SEC = 1000;

    private static ElapsedTimeAxisFormatter sInstance;
    private final String mSmallFormat;
    private final String mLargeFormat;
    private final String mSmallFormatTenths;
    private final String mLargeFormatTenths;
    private long mTempHours;
    private long mTempMins;
    private long mTempSecs;
    private long mTempTenthsOfSecs;

    public static ElapsedTimeAxisFormatter getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ElapsedTimeAxisFormatter(context);
        }
        return sInstance;
    }

    private ElapsedTimeAxisFormatter(Context context) {
        Resources res = context.getResources();
        mSmallFormat = res.getString(R.string.elapsed_time_axis_format_small);
        mLargeFormat = res.getString(R.string.elapsed_time_axis_format_large);
        mSmallFormatTenths = res.getString(R.string.elapsed_time_axis_format_small_tenths);
        mLargeFormatTenths = res.getString(R.string.elapsed_time_axis_format_large_tenths);
    }

    public String format(long elapsedTimeMs) {
        updateElapsedTimeValues(elapsedTimeMs);
        String result;
        if (mTempHours > 0) {
            result = String.format(mLargeFormat, mTempHours, mTempMins, mTempSecs);
        } else {
            result = String.format(mSmallFormat, mTempMins, mTempSecs);
        }
        boolean isNegative = elapsedTimeMs < 0;
        return (isNegative ? "-" : "") + result;
    }

    public String formatToTenths(long elapsedTimeMs) {
        updateElapsedTimeValues(elapsedTimeMs);
        String result;
        if (mTempHours > 0) {
            result = String.format(mLargeFormatTenths, mTempHours, mTempMins, mTempSecs,
                    mTempTenthsOfSecs);
        } else {
            result = String.format(mSmallFormatTenths, mTempHours, mTempMins, mTempSecs);
        }
        boolean isNegative = elapsedTimeMs < 0;
        return (isNegative ? "-" : "") + result;
    }

    /**
     * Gets the values of the elapsed time.
     * @param elapsedTimeMs
     * @return An array of {hours, minutes, seconds, tenths of seconds}
     */
    private void updateElapsedTimeValues(long elapsedTimeMs) {
        elapsedTimeMs = Math.abs(elapsedTimeMs);
        mTempHours = getHours(elapsedTimeMs);
        mTempMins = getMins(elapsedTimeMs, mTempHours);
        mTempSecs = getSecs(elapsedTimeMs, mTempHours, mTempMins);
        mTempTenthsOfSecs = getTenthsOfSecs(elapsedTimeMs, mTempHours, mTempMins, mTempSecs);
    }

    private long getHours(long elapsedTime) {
        return elapsedTime / MS_IN_SEC / (SEC_IN_MIN * MIN_IN_HOUR);
    }

    private long getMins(long elapsedTime, long hours) {
        return (elapsedTime / MS_IN_SEC - hours * SEC_IN_MIN * MIN_IN_HOUR) / SEC_IN_MIN;
    }

    private long getSecs(long elapsedTime, long hours, long mins) {
        return elapsedTime / MS_IN_SEC - hours * SEC_IN_MIN * MIN_IN_HOUR - mins * SEC_IN_MIN;
    }

    private long getTenthsOfSecs(long elapsedTime, long hours, long mins, long secs) {
        return elapsedTime * TENTHS_IN_SEC / MS_IN_SEC -
                hours * SEC_IN_MIN * MIN_IN_HOUR * TENTHS_IN_SEC -
                mins * SEC_IN_MIN * TENTHS_IN_SEC - secs * TENTHS_IN_SEC;
    }

}
