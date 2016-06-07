package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

public class SecondsAgoFormat extends NumberFormat {
    private final ElapsedTimeAxisFormatter mElapsedTimeFormatter;
    private Clock mClock;

    public SecondsAgoFormat(Clock clock, Context context) {
        mClock = clock;
        mElapsedTimeFormatter = ElapsedTimeAxisFormatter.getInstance(context);
    }

    @Override
    public StringBuffer format(double value, StringBuffer buffer, FieldPosition field) {
        return format((long)value, buffer, field);
    }

    @Override
    public StringBuffer format(long value, StringBuffer buffer, FieldPosition field) {
        long timeSeconds = (value - mClock.getNow()) / 1000;
        return buffer.append(mElapsedTimeFormatter.format(timeSeconds));
    }

    @Override
    public Number parse(String string, ParsePosition position) {
        return null;
    }
}
