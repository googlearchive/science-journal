package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.support.annotation.IntDef;

import com.google.android.apps.forscience.whistlepunk.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.NumberFormat;

/**
 * A calculated statistic from a stream of data such as the min / max / avg / duration, etc.
 * Saves the value as a double.
 */
public class StreamStat {

    @IntDef({TYPE_MIN, TYPE_MAX, TYPE_AVERAGE, TYPE_DURATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface StatType {}

    public static final int TYPE_MIN = 0;
    public static final int TYPE_MAX = 1;
    public static final int TYPE_AVERAGE = 2;
    public static final int TYPE_DURATION = 3;

    private @StatType int mType;
    private boolean mDisplayValue = false;
    private NumberFormat mNumberFormat;
    private double mValue;

    public StreamStat(@StatType int type, NumberFormat numberFormat) {
        mType = type;
        mNumberFormat = numberFormat;
    }

    public @StatType int getType() {
        return mType;
    }

    public int getDisplayTypeStringId() {
        switch (mType) {
            case TYPE_MIN:
                return R.string.stat_min;
            case TYPE_MAX:
                return R.string.stat_max;
            case TYPE_AVERAGE:
                return R.string.stat_average;
            case TYPE_DURATION:
                return R.string.stat_duration;
            default:
                return R.string.stat_unknown;
        }
    }

    public String getDisplayValue() {
        if (mDisplayValue) {
            return mNumberFormat.format(mValue);
        } else {
            return "";
        }
    }

    public void setValue(double value) {
        mValue = value;
        mDisplayValue = true;
    }

    public void clear() {
        mDisplayValue = false;
    }

    public double getValue() {
        return mValue;
    }
}
