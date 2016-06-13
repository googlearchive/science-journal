package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

/**
 * Formats a timestamp based on how far away it was from a known 0 time.
 */
public class RelativeTimeFormat extends NumberFormat {
    private final ElapsedTimeAxisFormatter mElapsedTimeFormatter;
    private final long mZeroTimestamp;

    public RelativeTimeFormat(long zeroTimestamp, Context context) {
        mZeroTimestamp = zeroTimestamp;
        mElapsedTimeFormatter = ElapsedTimeAxisFormatter.getInstance(context);
    }

    @Override
    public StringBuffer format(double value, StringBuffer buffer, FieldPosition field) {
        return format((long)value, buffer, field);
    }

    @Override
    public StringBuffer format(long value, StringBuffer buffer, FieldPosition field) {
        return buffer.append(mElapsedTimeFormatter.format(value - mZeroTimestamp));
    }

    @Override
    public Number parse(String string, ParsePosition position) {
        return null;
    }
}
