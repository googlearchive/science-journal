/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.content.res.Resources;

import com.google.android.apps.forscience.whistlepunk.AxisNumberFormat;
import com.google.android.apps.forscience.whistlepunk.R;

import org.achartengine.chart.PointStyle;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;


public class SensorDataRenderer extends XYMultipleSeriesRenderer {

    // The minimum spread between the minimum and maximum y values shown on the graph.
    static final double MINIMUM_Y_SPREAD = 1;

    // The scale factor for each frame to scale the y axis.
    static final double SCALE_FACTOR = .1;

    private final XYSeriesRenderer mDataRenderer;
    private final XYSeriesRenderer mLabelRenderer;
    private final XYSeriesRenderer mLeadingEdgeRenderer;
    private final XYSeriesRenderer mEndpointsRenderer;
    private boolean mSmoothYAxisAdjustment;
    private boolean mIsObserveMode;
    private int mLineColor; // The color of the main data line.
    private int mLabelFillColor; // The color of the inside of the labels.
    private float mLabelFillSize;
    private float mEndpointsFillSize;
    private int mEndpointsFillColor;
    private double mYMin = Double.MAX_VALUE;
    private double mYMax = -Double.MAX_VALUE;
    private final boolean mAllowInteraction;
    private boolean mAllowZoomIn = false;

    public SensorDataRenderer(boolean smoothYAxisAdjustment, int dataIndex, int labelIndex,
                              int leadingEdgeIndex, int endpointsIndex,
                              boolean isObserveMode, int lineColor, boolean allowInteraction) {
        mSmoothYAxisAdjustment = smoothYAxisAdjustment;
        mDataRenderer = new XYSeriesRenderer();
        addSeriesRenderer(dataIndex, mDataRenderer);
        mLabelRenderer = new XYSeriesRenderer();
        addSeriesRenderer(labelIndex, mLabelRenderer);
        mLeadingEdgeRenderer = new XYSeriesRenderer();
        addSeriesRenderer(leadingEdgeIndex, mLeadingEdgeRenderer);
        mEndpointsRenderer = new XYSeriesRenderer();
        addSeriesRenderer(endpointsIndex, mEndpointsRenderer);
        mLineColor = lineColor;
        mIsObserveMode = isObserveMode;
        mAllowInteraction = allowInteraction;
        setAntialiasing(true);
    }

    public void updateMainColor(int newColorId) {
        mLineColor = newColorId;
        mDataRenderer.setColor(mLineColor);
        mLeadingEdgeRenderer.setColor(mLineColor);
        mEndpointsRenderer.setColor(mLineColor);
        if (mIsObserveMode) {
            mLabelRenderer.setColor(mLineColor);
        }
    }

    public void adjustXAxis(long min, long max) {
        setXAxisMax(max);
        setXAxisMin(min);
    }

    public void resetYAxis() {
        mYMin = Double.MAX_VALUE;
        mYMax = -Double.MAX_VALUE;
        setYAxisMin(mYMin);
        setYAxisMax(mYMax);
    }

    public void setupRendererStyle(Resources res, int marginsColor) {
        setShowLegend(false);
        // Transparent background color
        setBackgroundColor(res.getColor(R.color.chart_background_color));
        setApplyBackgroundColor(true);
        setMarginsColor(marginsColor);
        int marginSize = (int) res.getDimension(R.dimen.chart_margin_size_left);
        setMargins(new int[]{0, marginSize, 0, 0});
        setShowTickMarks(false);
        setShowAxes(false);
        setShowGridX(true);
        setGridColor(res.getColor(R.color.chart_grid_color));
        setGridLineWidth(res.getDimension(R.dimen.chart_grid_line_width));

        // Don't want visible X labels.
        setShowLabels(false, true);
        setLabelsTextSize(res.getDimensionPixelSize(R.dimen.chart_labels_text_size));

        // Always 0, since we're just using the left side for Y values
        setYLabelsColor(0, res.getColor(R.color.text_color_light_grey));
        setYLabelsPadding(res.getDimension(R.dimen.chart_labels_padding));
        setYLabelsVerticalPadding(res.getDimension(R.dimen.chart_labels_vertical_padding));
        setYLabelFormat(new AxisNumberFormat(), 0);

        // Only allow X axis zoom (not Y) in review mode.
        // Only allow Y axis zoom (not X) in live mode.
        setZoomEnabled(!mIsObserveMode && mAllowInteraction, mIsObserveMode && mAllowInteraction);

        // Allow pan in X but not in Y for either mode.
        setPanEnabled(mAllowInteraction, false);

        mLabelRenderer.setPointStyle(PointStyle.CIRCLE);
        mLabelRenderer.setPointStrokeWidth(res.getDimensionPixelSize(R.dimen.label_dot_outline));
        if (mIsObserveMode) {
            mLabelFillColor = res.getColor(R.color.graph_label_fill_color);
        } else {
            mLabelRenderer.setColor(res.getColor(R.color.run_review_background_color));
            mLabelFillColor = res.getColor(R.color.run_review_graph_label_fill_color);
        }
        mLabelFillSize = res.getDimensionPixelSize(R.dimen.label_dot_size);

        mDataRenderer.setLineWidth(res.getDimension(R.dimen.graph_line_width));
        mLeadingEdgeRenderer.setPointStyle(PointStyle.CIRCLE);
        mLeadingEdgeRenderer.setPointStrokeWidth(res.getDimensionPixelSize(
                R.dimen.chart_leading_dot_size));
        mEndpointsRenderer.setPointStyle(PointStyle.CIRCLE);
        mEndpointsRenderer.setPointStrokeWidth(res.getDimensionPixelOffset(
                R.dimen.endpoints_label_dot_outline));
        mEndpointsFillSize = res.getDimensionPixelSize(R.dimen.endpoints_label_fill_size);
        mEndpointsFillColor = res.getColor(R.color.run_review_background_color);

        updateMainColor(mLineColor);
    }

    public int getLabelFillColor() {
        return mLabelFillColor;
    }

    public int getEndpointsFillColor() {
        return mEndpointsFillColor;
    }

    public float getLabelFillSize() {
        return mLabelFillSize;
    }

    public float getEndpointsFillSize() {
        return mEndpointsFillSize;
    }

    public boolean isYAxisInitialized() {
        return mYMax >= mYMin;
    }

    public void adjustYAxisStep() {
        adjustYAxis(false);
    }

    public void adjustYAxisImmediately() {
        adjustYAxis(true);
    }

    private void adjustYAxis(boolean adjustImmediately) {
        // Create a buffer on either side to make sure that data isn't at the very
        // top or bottom of the view.
        double buffer = getYBuffer(mYMin, mYMax);
        double idealYMax = mYMax + buffer;
        double idealYMin = mYMin - buffer;

        if (adjustImmediately) {
            setYAxisMin(idealYMin);
            setYAxisMax(idealYMax);
            return;
        }

        // Adjust the axes as necessary.
        final double lastYMin = getYAxisMin();
        final double lastYMax = getYAxisMax();
        if (lastYMax == -Double.MAX_VALUE) {
            // When a new graph is created, lastYMax and lastYMin may be equal.
            // Add 50% on either side to create an initial buffer.
            setYAxisMin(idealYMin - Math.abs(0.50 * idealYMin));
            setYAxisMax(idealYMax + Math.abs(0.50 * idealYMax));
        } else {
            double maxMove = calculateMaxMove(lastYMin, lastYMax);

            if (mAllowZoomIn) {
                setYAxisMin(calculateMovedValue(lastYMin, idealYMin, maxMove));
                setYAxisMax(calculateMovedValue(lastYMax, idealYMax, maxMove));
            } else {
                // Only zoom out automatically. Don't zoom in automatically!
                setYAxisMin(Math.min(lastYMin, calculateMovedValue(lastYMin, idealYMin, maxMove)));
                setYAxisMax(Math.max(lastYMax, calculateMovedValue(lastYMax, idealYMax, maxMove)));
            }
        }
    }

    public static double getYBuffer(double yMin, double yMax) {
        return Math.max(MINIMUM_Y_SPREAD, Math.abs(yMax - yMin) * SCALE_FACTOR);
    }

    private double calculateMaxMove(double lastYMin, double lastYMax) {
        if (!mSmoothYAxisAdjustment) {
            return Double.MAX_VALUE;
        }

        // To prevent jumpiness, only move by 5% of the current screen size
        double maxMove = (0.05 * (lastYMax - lastYMin));
        if (maxMove == 0) {
            // If we never allow it to move, we will never display any data. So, put in a min
            // amount of move here.
            maxMove = 1;
        }
        return maxMove;
    }

    private double calculateMovedValue(double current, double target, double maxMove) {
        if (Math.abs(current - target) < maxMove) {
            return target;
        }
        if (target > current) {
            return current + maxMove;
        }

        return current - maxMove;
    }

    public void updatePinnedYAxisLimits(double yMin, double yMax) {
        mYMin = yMin;
        mYMax = yMax;
    }

    public void adjustYAxis(double yMin, double yMax) {
        setYAxisMin(yMin);
        setYAxisMax(yMax);
    }

    public void setAllowZoomInOnY(boolean allowZoomIn) {
        mAllowZoomIn = allowZoomIn;
    }
}
