package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;

/**
 * Encapsulates information that a SensorChoice needs in order to choose how to graphically
 * display the data it has gathered.
 */
public class DataViewOptions {
    private final int mGraphColor;
    private final ScalarDisplayOptions mOptions;

    /**
     * @param graphColor "theme color" for this sensor data view, as built by android.graphics
     *                   .Color.  For example, if this is a line graph, this is the color in
     *                   which the line should be drawn. (This is currently customized per-graph)
     * @param options    settings that affect other aspects of how the line should be drawn,
     *                   especially its shape.
     *                   (This is currently shared between all graphs)
     */
    public DataViewOptions(int graphColor, ScalarDisplayOptions options) {
        mGraphColor = graphColor;
        mOptions = options;
    }

    public int getGraphColor() {
        return mGraphColor;
    }

    public ScalarDisplayOptions getLineGraphOptions() {
        return mOptions;
    }
}
