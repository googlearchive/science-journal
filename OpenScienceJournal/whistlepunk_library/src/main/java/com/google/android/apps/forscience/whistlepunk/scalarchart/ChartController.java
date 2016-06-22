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

import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;

import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

import java.util.ArrayList;
import java.util.List;

public class ChartController {

    // Factor by which to scale the Y axis range so that all the points fit snugly.
    private static final double BUFFER_SCALE = .05;

    private final ChartData mChartData;
    private ChartOptions mChartOptions;
    private ChartView mChartView;
    private ExternalAxisController.InteractionListener mInteractionListener;
    private long mRecordingStartTime = RecordingMetadata.NOT_RECORDING;

    private ProgressBar mProgressView;

    public ChartController(ChartOptions.ChartPlacementType chartPlacementType,
            ScalarDisplayOptions scalarDisplayOptions) {
        mChartData = new ChartData();
        mChartOptions = new ChartOptions(chartPlacementType);
        mChartOptions.setScalarDisplayOptions(scalarDisplayOptions);
    }

    public void setChartView(ChartView view) {
        mChartView = view;
        mChartView.clearInteractionListeners();
        if (mInteractionListener != null) {
            mChartView.addInteractionListener(mInteractionListener);
        }
        mChartView.initialize(mChartOptions, mChartData);
    }

    public void setProgressView(ProgressBar progress) {
        mProgressView = progress;
    }

    // Assume we add the single point to the end of the path.
    public void addPoint(ChartData.DataPoint point) {
        mChartData.addPoint(point);

        if (mChartOptions.isPinnedToNow()) {
            // TODO: When pinned to now, zoom out to show the rendered ymin and ymax across
            // the range, without too much zoom at each step.
            // This currently just sets the rendered range to fit the latest data point, but
            // does not take into account previous data points in the current window.
            mChartOptions.setRenderedYRange(Math.min(point.getY(), mChartOptions.getRenderedYMin()),
                    Math.max(point.getY(), mChartOptions.getRenderedYMax()));
        }

        if (mChartView != null) {
            mChartView.addPointToEndOfPath(point);
        }
    }

    // Assume this is an ordered list.
    // After setting the data, this function zooms to fit
    public void setData(List<ChartData.DataPoint> points) {
        mChartData.setPoints(points);
        mChartOptions.reset();
        mChartOptions.setRenderedXRange(mChartData.getXMin(), mChartData.getXMax());
        zoomToFitRenderedY();
        mChartOptions.setPinnedToNow(false);
        if (mChartView != null) {
            mChartView.redraw();
        }
    }

    public void clearData() {
        mChartData.clear();
        mChartOptions.reset();
        if (mChartView != null) {
            mChartView.redraw();
        }
    }

    public void onDestroy() {
        if (mChartView != null) {
            mChartView.clearInteractionListeners();
            mChartView = null;
        }
    }

    public void setPinnedToNow(boolean isPinnedToNow) {
        mChartOptions.setPinnedToNow(isPinnedToNow);
    }

    public boolean isPinnedToNow() {
        return mChartOptions.isPinnedToNow();
    }

    public void setLabels(List<Label> labels) {
        List<Label> displayableLabels = new ArrayList<>();
        for (Label label : labels) {
            if (mChartOptions.isDisplayable(label, mRecordingStartTime)) {
                displayableLabels.add(label);
            }
        }
        mChartData.setDisplayableLabels(displayableLabels);
    }

    public void addLabel(Label label) {
        mChartData.addLabel(label);
    }

    public List<ChartData.DataPoint> getData() {
        return mChartData.getPoints();
    }

    public void setXAxis(long xMin, long xMax) {
        mChartOptions.setRenderedXRange(xMin, xMax);
        if (mChartView != null) {
            mChartView.onAxisLimitsAdjusted();
        }
    }

    public void setYAxis(double yMin, double yMax) {
        mChartOptions.setRenderedYRange(yMin, yMax);
        if (mChartView != null) {
            mChartView.onAxisLimitsAdjusted();
        }
    }

    // Zooms in or out to fit the Ys in the rendered X axis range.
    public void zoomToFitRenderedY() {
        List<ChartData.DataPoint> points = mChartData.getPointsInRange(
                mChartOptions.getRenderedXMin(), mChartOptions.getRenderedXMax());
        double value;
        double min = mChartOptions.getRenderedYMin();
        double max = mChartOptions.getRenderedYMax();
        for (int i = 0; i < points.size(); i++) {
            value = points.get(i).getY();
            if (value > max) {
                max = value;
            }
            if (value < min) {
                min = value;
            }
        }
        setYAxisWithBuffer(min, max);
    }

    public void setYAxisWithBuffer(double min, double max) {
        double buffer = (max - min) * BUFFER_SCALE;
        setYAxis(min - buffer, max + buffer);
        refreshChartView();
    }

    public boolean hasData() {
        return !mChartData.isEmpty();
    }

    public long getXMin() {
        return mChartData.getXMin();
    }

    public long getXMax() {
        return mChartData.getXMax();
    }

    public long getRenderedXMin() {
        return mChartOptions.getRenderedXMin();
    }

    public long getRenderedXMax() {
        return mChartOptions.getRenderedXMax();
    }

    public double getRenderedYMin() {
        return mChartOptions.getRenderedYMin();
    }

    public double getRenderedYMax() {
        return mChartOptions.getRenderedYMax();
    }

    public void refreshChartView() {
        if (mChartView != null) {
            mChartView.redraw();
        }
    }

    public ChartData.DataPoint getClosestDataPointToTimestamp(long timestamp) {
        return mChartData.getClosestDataPointToTimestamp(timestamp);
    }

    public int getClosestIndexToTimestamp(long timestamp) {
        return mChartData.getClosestIndexToTimestamp(timestamp);
    }

    public boolean hasDrawnChart() {
        return mChartView != null && mChartView.isDrawn();
    }

    public boolean hasScreenPoints() {
        return mChartView.isDrawn() && !mChartData.isEmpty();
    }

    public PointF getScreenPoint(long timestamp, double value) {
        return new PointF(mChartView.getScreenX(timestamp), mChartView.getScreenY(value));
    }

    public ViewTreeObserver getChartViewTreeObserver() {
        if (mChartView == null) {
            return null;
        }
        return mChartView.getViewTreeObserver();
    }

    public void updateColor(int color) {
        mChartOptions.setLineColor(color);
        if (mChartView != null) {
            mChartView.updateColorOptions();
        }
    }

    public void setShowProgress(boolean showProgress) {
        if (mChartView != null) {
            mChartView.setVisibility(showProgress ? View.GONE: View.VISIBLE);
        }
        if (mProgressView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mProgressView.setIndeterminateTintList(ColorStateList.valueOf(
                        mChartOptions.getLineColor()));
            }
            mProgressView.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        }
    }

    public void setInteractionListener(
            ExternalAxisController.InteractionListener interactionListener) {
        mInteractionListener = interactionListener;
        if (mChartView != null) {
            mChartView.addInteractionListener(interactionListener);
        }
    }

    public void updateOptions(int graphColor, ScalarDisplayOptions scalarDisplayOptions,
            ExternalAxisController.InteractionListener interactionListener) {
        mInteractionListener = interactionListener;
        mChartOptions.setLineColor(graphColor);
        mChartOptions.setScalarDisplayOptions(scalarDisplayOptions);
        if (mChartView != null) {
            mChartView.clearInteractionListeners();
            mChartView.addInteractionListener(interactionListener);
            mChartView.redraw(); // Full redraw in case the options caused computational changes.
        }
    }

    public void setRecordingStartTime(long recordingStartTime) {
        mRecordingStartTime = recordingStartTime;
        mChartOptions.setRecordingStartTime(recordingStartTime);
        if (mChartView != null) {
            mChartView.invalidate();
        }
    }

    public void updateStats(List<StreamStat> stats) {
        mChartData.updateStats(stats);
        if (mChartView != null) {
            mChartView.invalidate();
        }
    }

    public void setShowStatsOverlay(boolean showStatsOverlay) {
        mChartOptions.setShowStatsOverlay(showStatsOverlay);
        if (mChartView != null) {
            mChartView.invalidate();
        }
    }

}
