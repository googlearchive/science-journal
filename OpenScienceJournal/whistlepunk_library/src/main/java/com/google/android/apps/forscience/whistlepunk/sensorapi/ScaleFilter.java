package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig.BleSensorConfig
        .ScaleTransform;

/**
 * Filter that applies a linear function to the incoming function
 */
public class ScaleFilter implements ValueFilter {
    private final double mSourceBottom;
    private final double mDestBottom;
    private final double mSourceRange;
    private final double mDestRange;

    public ScaleFilter(ScaleTransform transform) {
        mSourceBottom = transform.sourceBottom;
        mSourceRange = transform.sourceTop - mSourceBottom;
        mDestBottom = transform.destBottom;
        mDestRange = transform.destTop - mDestBottom;
    }

    @Override
    public double filterValue(long timestamp, double value) {
        double ratio = (value - mSourceBottom) / mSourceRange;
        double transformed = (ratio * mDestRange) + mDestBottom;
        return transformed;
    }

}
