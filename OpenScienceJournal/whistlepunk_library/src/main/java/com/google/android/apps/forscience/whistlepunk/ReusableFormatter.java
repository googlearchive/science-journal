package com.google.android.apps.forscience.whistlepunk;

import java.util.Formatter;

/**
 * A re-usable formatter class to use as an alternative to String.format, which
 * creates a new Formatter behind the scenes for each use.
 */
public class ReusableFormatter {
    private Formatter mFormatter;
    private StringBuilder mBuilder;

    public ReusableFormatter() {
        mBuilder = new StringBuilder();
        mFormatter = new Formatter(mBuilder);
    }

    public Formatter format(String format, Object... params) {
        mBuilder.setLength(0);
        return mFormatter.format(format, params);
    }
}
