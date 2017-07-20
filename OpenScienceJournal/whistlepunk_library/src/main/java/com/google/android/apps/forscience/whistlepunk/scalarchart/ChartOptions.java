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
import android.graphics.Color;

import com.google.android.apps.forscience.whistlepunk.AxisNumberFormat;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

import java.text.NumberFormat;
import java.util.List;

public class ChartOptions {

    public enum ChartPlacementType {
        TYPE_OBSERVE, TYPE_RUN_REVIEW, TYPE_PREVIEW_REVIEW;
    }

    // Factor by which to scale the Y axis range so that all the points fit snugly.
    private static final double BUFFER_SCALE = .09;

    // The minimum spread between the minimum and maximum y values shown on the graph.
    static final double MINIMUM_Y_SPREAD = 1;

    // Don't allow zoom out past this factor times the y range. At this point the line will look
    // basically flat anyway, so there's no need to keep allowing zoom out.
    private static final double MAXIMUM_Y_SPREAD_FACTOR = 100;

    // The fraction of the screen size by which we can zoom at the addition of each new data point.
    static final double SCALE_SCREEN_SIZE_FRACTION = 0.05;

    // For comparing doubles
    private static final double EPSILON = 1E-7;

    private final boolean mCanPanX;
    private final boolean mCanPanY;
    private final boolean mCanZoomX;
    private final boolean mCanZoomY;
    private final boolean mShowLeadingEdge;
    private final ChartPlacementType mChartPlacementType;

    private double mYMinPoint = Double.MAX_VALUE;
    private double mYMaxPoint = Double.MIN_VALUE;
    private boolean mRequestResetZoomInY = false;

    private long mRenderedXMin;
    private long mRenderedXMax;
    private double mRenderedYMin;
    private double mRenderedYMax;
    private boolean mPinnedToNow;
    private NumberFormat mNumberFormat;
    private int mLineColor = Color.BLACK;
    private boolean mShowStatsOverlay;
    private ScalarDisplayOptions mScalarDisplayOptions;

    private boolean mShowOriginalRun = false;
    private long mRecordingStartTime;
    private long mRecordingEndTime;
    private long mOriginalStartTime;
    private long mOriginalEndTime;

    private List<Double> mTriggerValues;

    public ChartOptions(ChartPlacementType chartPlacementType) {
        mChartPlacementType = chartPlacementType;
        switch (mChartPlacementType) {
            case TYPE_OBSERVE:
                mShowLeadingEdge = true;
                mCanPanX = true;
                mCanPanY = false;
                mCanZoomX = false;
                mCanZoomY = true;
                mPinnedToNow = true;
                break;
            case TYPE_RUN_REVIEW:
                mShowLeadingEdge = false;
                mCanPanX = true;
                mCanPanY = true;
                mCanZoomX = true;
                mCanZoomY = true;
                mPinnedToNow = false;
                break;
            case TYPE_PREVIEW_REVIEW:
            default:
                mShowLeadingEdge = false;
                mCanPanX = false;
                mCanPanY = false;
                mCanZoomX = false;
                mCanZoomY = false;
                mPinnedToNow = false;
                break;
        }
        mNumberFormat = new AxisNumberFormat();
        reset();
    }

    public ChartPlacementType getChartPlacementType() {
        return mChartPlacementType;
    }

    public long getRenderedXMin() {
        return mRenderedXMin;
    }

    public long getRenderedXMax() {
        return mRenderedXMax;
    }

    public void setRenderedXRange(long renderedXMin, long renderedXMax) {
        mRenderedXMin = renderedXMin;
        mRenderedXMax = renderedXMax;
    }

    public double getRenderedYMin() {
        return mRenderedYMin;
    }

    public double getRenderedYMax() {
        return mRenderedYMax;
    }

    public double getYMinLimit() {
        return mYMinPoint;
    }

    public double getYMaxLimit() {
        return mYMaxPoint;
    }

    public void requestResetZoomInY() {
        mRenderedYMin = mYMinPoint;
        mRenderedYMax = mYMaxPoint;
        mRequestResetZoomInY = true;
    }

    public boolean getRequestResetZoomInY() {
        return mRequestResetZoomInY;
    }

    public void setRenderedYRange(double renderedYMin, double renderedYMax) {
        if (renderedYMax - renderedYMin < MINIMUM_Y_SPREAD) {
            // Minimum Y spread keeps us from zooming in too far.
            double avg = (renderedYMax + renderedYMin) / 2;
            mRenderedYMin = avg - MINIMUM_Y_SPREAD / 2;
            mRenderedYMax = avg + MINIMUM_Y_SPREAD / 2;
        } else if (renderedYMax - renderedYMin < getMaxRenderedYRange() ||
                Math.abs(mYMaxPoint - mYMinPoint) < EPSILON) {
            // If the requested points are inside of the max Y range allowed, save them.
            mRenderedYMin = renderedYMin;
            mRenderedYMax = renderedYMax;
        }
    }

    public void updateYMinAndMax(double minYPoint, double maxYPoint) {
        mYMaxPoint = maxYPoint;
        mYMinPoint = minYPoint;
    }

    public double getMaxRenderedYRange() {
        // Minimum range 1, maximum range 10 if we are getting ymin ~= ymax.
        return Math.max(10, (mYMaxPoint - mYMinPoint) * MAXIMUM_Y_SPREAD_FACTOR);
    }

    public void adjustYAxisStep(ChartData.DataPoint latestPoint) {
        if (latestPoint.getY() < mYMinPoint) {
            mYMinPoint = latestPoint.getY();
        }
        if (latestPoint.getY() > mYMaxPoint) {
            mYMaxPoint = latestPoint.getY();
        }
        double buffer = getYBuffer(mYMinPoint, mYMaxPoint);
        double idealYMax = mYMaxPoint + buffer;
        double idealYMin = mYMinPoint - buffer;

        double lastYMin = getRenderedYMin();
        double lastYMax = getRenderedYMax();

        if (lastYMax <= lastYMin) {
            // TODO do we need to do bounds checking?
            mRenderedYMin = idealYMin;
            mRenderedYMax = idealYMax;
            return;
        }

        // Don't zoom too fast
        double maxMove = calculateMaxMove(lastYMin, lastYMax);

        // Only zoom out automatically. Don't zoom in automatically!
        double newYMin = Math.min(lastYMin, calculateMovedValue(lastYMin, idealYMin, maxMove));
        double newYMax = Math.max(lastYMax, calculateMovedValue(lastYMax, idealYMax, maxMove));

        setRenderedYRange(newYMin, newYMax);
    }

    private double calculateMaxMove(double lastYMin, double lastYMax) {
        // To prevent jumpiness, only move by a small percent of the current screen size
        double maxMove = (SCALE_SCREEN_SIZE_FRACTION * (lastYMax - lastYMin));
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

    public static double getYBuffer(double yMin, double yMax) {
        return Math.max(MINIMUM_Y_SPREAD, Math.abs(yMax - yMin) * BUFFER_SCALE);
    }

    public boolean isShowLeadingEdge() {
        return mShowLeadingEdge;
    }

    public boolean isShowEndpoints() {
        return !mShowLeadingEdge;
    }

    public int getLineColor() {
        return mLineColor;
    }

    public int getLineWidthId() {
        return R.dimen.graph_line_width;
    }

    // TODO this should come from ScalarDisplayOptions.
    public int getCornerPathRadiusId() {
        return R.dimen.path_corner_radius;
    }

    public int getAxisLabelsLineColorId() {
        return R.color.chart_grid_color;
    }

    public int getAxisLabelsLineWidthId() {
        return R.dimen.chart_grid_line_width;
    }

    public int getLabelsTextColorId() {
        return R.color.text_color_light_grey;
    }

    public NumberFormat getAxisNumberFormat() {
        return mNumberFormat;
    }

    public void reset() {
        mRenderedXMin = Long.MAX_VALUE;
        mRenderedXMax = Long.MIN_VALUE;
        mRenderedYMin = Double.MAX_VALUE;
        mRenderedYMax = Double.MIN_VALUE;
        mYMinPoint = Double.MAX_VALUE;
        mYMaxPoint = Double.MIN_VALUE;
        mRequestResetZoomInY = false;
    }

    public void resetYAxisLimits() {
        mYMinPoint = Double.MAX_VALUE;
        mYMaxPoint = Double.MIN_VALUE;
    }

    public boolean canPan() {
        return mCanPanX || mCanPanY;
    }

    public boolean canPanX() {
        return mCanPanX;
    }

    public boolean canPanY() {
        return mCanPanY;
    }

    public boolean canZoom() {
        return mCanZoomX || mCanZoomY;
    }

    public boolean canZoomX() {
        return mCanZoomX;
    }

    public boolean canZoomY() {
        return mCanZoomY;
    }

    public boolean isPinnedToNow() {
        return mPinnedToNow;
    }

    public void setPinnedToNow(boolean pinnedToNow) {
        mPinnedToNow = pinnedToNow;
    }

    public int getAxisLabelsStartPaddingId() {
        return R.dimen.chart_labels_padding;
    }

    public int getAxisLabelsTextSizeId() {
        return R.dimen.chart_labels_text_size;
    }

    public int getChartBackgroundColorId() {
        return R.color.chart_background_color;
    }

    public int getChartStartPaddingId() {
        return R.dimen.chart_margin_size_left;
    }

    public int getLeadingEdgeRadiusId() {
        return R.dimen.chart_leading_dot_size;
    }

    public int getEndpointInnerRadiusId() {
        return R.dimen.endpoints_label_fill_size;
    }

    public int getEndpointOutderRadiusId() {
        return R.dimen.endpoints_label_dot_outline;
    }

    public int getLabelInnerRadiusId() {
        return mShowLeadingEdge ? R.dimen.label_dot_radius :
                R.dimen.run_review_value_label_dot_radius;
    }

    public int getLabelOutlineRadiusId() {
        return mShowLeadingEdge ? R.dimen.label_dot_outline_radius :
                R.dimen.run_review_value_label_dot_background_radius;
    }

    public int getLabelFillColorId() {
        return mShowLeadingEdge ? R.color.graph_label_fill_color :
                R.color.run_review_graph_label_fill_color;
    }

    public int getLabelOutlineColor(Resources res) {
        return mShowLeadingEdge ? mLineColor : res.getColor(R.color.chart_background_color);
    }

    public void setLineColor(int lineColor) {
        mLineColor = lineColor;
    }

    public void setRecordingStartTime(long recordingStartTime) {
        mRecordingStartTime = recordingStartTime;
    }

    public long getRecordingStartTime() {
        return mShowOriginalRun ? mOriginalStartTime : mRecordingStartTime;
    }

    public void setRecordingTimes(long recordingStartTime, long recordingEndTime,
            long originalStartTime, long originalEndTime) {
        mRecordingStartTime = recordingStartTime;
        mRecordingEndTime = recordingEndTime;
        mOriginalStartTime = originalStartTime;
        mOriginalEndTime = originalEndTime;
    }

    public long getRecordingEndTime() {
        return mShowOriginalRun ? mOriginalEndTime : mRecordingEndTime;
    }

    public void setShowOriginalRun(boolean showOriginalRun) {
        mShowOriginalRun = showOriginalRun;
    }

    public boolean shouldDrawRecordingOverlay() {
        return mRecordingStartTime != RecordingMetadata.NOT_RECORDING &&
                mChartPlacementType == ChartPlacementType.TYPE_OBSERVE;
    }

    public void setShowStatsOverlay(boolean showStatsOverlay) {
        mShowStatsOverlay = showStatsOverlay;
    }

    public boolean shouldShowStatsOverlay() {
        if (mChartPlacementType == ChartPlacementType.TYPE_RUN_REVIEW) {
            return mShowStatsOverlay;
        }
        return mShowStatsOverlay && shouldDrawRecordingOverlay();
    }

    public boolean isDisplayable(Label label, long recordingStartTime) {
        return isDisplayable(label, recordingStartTime, mChartPlacementType);
    }

    // A label is displayable if:
    //   - It is after the recording start time, and we are recording
    //   - We are not recording
    public static boolean isDisplayable(Label label, long recordingStartTime,
            ChartPlacementType chartPlacementType) {
        if (chartPlacementType == ChartPlacementType.TYPE_RUN_REVIEW ||
                chartPlacementType == ChartPlacementType.TYPE_PREVIEW_REVIEW) {
            return true;
        }
        return recordingStartTime != RecordingMetadata.NOT_RECORDING &&
                label.getTimeStamp() >= recordingStartTime;
    }

    public void setScalarDisplayOptions(ScalarDisplayOptions scalarDisplayOptions) {
        mScalarDisplayOptions = scalarDisplayOptions;
    }

    public ScalarDisplayOptions getScalarDisplayOptions() {
        return mScalarDisplayOptions;
    }

    public void setTriggerValues(List<Double> values) {
        mTriggerValues = values;
    }

    public List<Double> getTriggerValues() {
        return mTriggerValues;
    }

    public boolean showYGrid() {
        return getChartPlacementType() != ChartOptions.ChartPlacementType.TYPE_PREVIEW_REVIEW;
    }
}
