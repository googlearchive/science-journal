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
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

import java.text.NumberFormat;

public class ChartOptions {

    public enum ChartPlacementType {
        TYPE_OBSERVE, TYPE_RUN_REVIEW, TYPE_PREVIEW_REVIEW;
    }

    private static final double MINIMUM_Y_RANGE = 4;

    private final boolean mCanPanX;
    private final boolean mCanPanY;
    private final boolean mCanZoomX;
    private final boolean mCanZoomY;
    private final boolean mShowLeadingEdge;
    private final ChartPlacementType mChartPlacementType;

    private long mRenderedXMin;
    private long mRenderedXMax;
    private double mRenderedYMin;
    private double mRenderedYMax;
    private boolean mPinnedToNow;
    private NumberFormat mNumberFormat;
    private int mLineColor = Color.BLACK;
    private long mRecordingStartTime;
    private boolean mShowStatsOverlay;
    private ScalarDisplayOptions mScalarDisplayOptions;

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
                mCanPanY = false;
                mCanZoomX = true;
                mCanZoomY = false;
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

    public long getRenderedXMin() {
        return mRenderedXMin;
    }

    public long getRenderedXMax() {
        return mRenderedXMax;
    }

    public void setRenderedXRange(long renderedXMin, long renderedXMax) {
        this.mRenderedXMin = renderedXMin;
        this.mRenderedXMax = renderedXMax;
    }

    public double getRenderedYMin() {
        return mRenderedYMin;
    }

    public double getRenderedYMax() {
        return mRenderedYMax;
    }

    public void setRenderedYRange(double renderedYMin, double renderedYMax) {
        if (renderedYMax - renderedYMin < MINIMUM_Y_RANGE) {
            double avg = (renderedYMax + renderedYMin) / 2;
            this.mRenderedYMin = avg - MINIMUM_Y_RANGE / 2;
            this.mRenderedYMax = avg + MINIMUM_Y_RANGE / 2;
        } else {
            this.mRenderedYMin = renderedYMin;
            this.mRenderedYMax = renderedYMax;
        }
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

    public boolean shouldDrawRecordingOverlay() {
        return mRecordingStartTime != RecordingMetadata.NOT_RECORDING &&
                mChartPlacementType == ChartPlacementType.TYPE_OBSERVE;
    }

    public void setShowStatsOverlay(boolean showStatsOverlay) {
        mShowStatsOverlay = showStatsOverlay;
    }

    public boolean shouldShowStatsOverlay() {
        return mShowStatsOverlay && shouldDrawRecordingOverlay();
    }

    // A label is displayable if:
    //   - It is not an "application" label (including recording start/stop), and either:
    //   - It is after the recording start time, and we are recording
    //   - We are not recording
    public boolean isDisplayable(Label label, long recordingStartTime) {
        if (label.getTag() != ApplicationLabel.TAG) {
            if (mChartPlacementType == ChartPlacementType.TYPE_RUN_REVIEW) {
                return true;
            }
            return recordingStartTime != RecordingMetadata.NOT_RECORDING &&
                    label.getTimeStamp() >= recordingStartTime;
        }
        return false;
    }

    public long getRecordingStartTime() {
        return mRecordingStartTime;
    }

    public void setScalarDisplayOptions(ScalarDisplayOptions scalarDisplayOptions) {
        mScalarDisplayOptions = scalarDisplayOptions;
    }

    public ScalarDisplayOptions getScalarDisplayOptions() {
        return mScalarDisplayOptions;
    }
}
