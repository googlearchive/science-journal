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
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import java.text.NumberFormat;
import java.util.List;

public class ChartOptions {

  public enum ChartPlacementType {
    TYPE_OBSERVE,
    TYPE_RUN_REVIEW,
    TYPE_PREVIEW_REVIEW;
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

  private final boolean canPanX;
  private final boolean canPanY;
  private final boolean canZoomX;
  private final boolean canZoomY;
  private final boolean showLeadingEdge;
  private final ChartPlacementType chartPlacementType;

  private double yMinPoint = Double.MAX_VALUE;
  private double yMaxPoint = Double.MIN_VALUE;
  private boolean requestResetZoomInY = false;

  private long renderedXMin;
  private long renderedXMax;
  private double renderedYMin;
  private double renderedYMax;
  private boolean pinnedToNow;
  private NumberFormat numberFormat;
  private int lineColor = Color.BLACK;
  private boolean showStatsOverlay;
  private ScalarDisplayOptions scalarDisplayOptions;

  private boolean showOriginalRun = false;
  private long recordingStartTime;
  private long recordingEndTime;
  private long originalStartTime;
  private long originalEndTime;

  private List<Double> triggerValues;

  public ChartOptions(ChartPlacementType chartPlacementType) {
    this.chartPlacementType = chartPlacementType;
    switch (this.chartPlacementType) {
      case TYPE_OBSERVE:
        showLeadingEdge = true;
        canPanX = true;
        canPanY = false;
        canZoomX = false;
        canZoomY = true;
        pinnedToNow = true;
        break;
      case TYPE_RUN_REVIEW:
        showLeadingEdge = false;
        canPanX = true;
        canPanY = true;
        canZoomX = true;
        canZoomY = true;
        pinnedToNow = false;
        break;
      case TYPE_PREVIEW_REVIEW:
      default:
        showLeadingEdge = false;
        canPanX = false;
        canPanY = false;
        canZoomX = false;
        canZoomY = false;
        pinnedToNow = false;
        break;
    }
    numberFormat = new AxisNumberFormat();
    reset();
  }

  public ChartPlacementType getChartPlacementType() {
    return chartPlacementType;
  }

  public long getRenderedXMin() {
    return renderedXMin;
  }

  public long getRenderedXMax() {
    return renderedXMax;
  }

  public void setRenderedXRange(long renderedXMin, long renderedXMax) {
    this.renderedXMin = renderedXMin;
    this.renderedXMax = renderedXMax;
  }

  public double getRenderedYMin() {
    return renderedYMin;
  }

  public double getRenderedYMax() {
    return renderedYMax;
  }

  public double getYMinLimit() {
    return yMinPoint;
  }

  public double getYMaxLimit() {
    return yMaxPoint;
  }

  public void requestResetZoomInY() {
    renderedYMin = yMinPoint;
    renderedYMax = yMaxPoint;
    requestResetZoomInY = true;
  }

  public boolean getRequestResetZoomInY() {
    return requestResetZoomInY;
  }

  public void setRenderedYRange(double renderedYMin, double renderedYMax) {
    if (renderedYMax - renderedYMin < MINIMUM_Y_SPREAD) {
      // Minimum Y spread keeps us from zooming in too far.
      double avg = (renderedYMax + renderedYMin) / 2;
      this.renderedYMin = avg - MINIMUM_Y_SPREAD / 2;
      this.renderedYMax = avg + MINIMUM_Y_SPREAD / 2;
    } else if (renderedYMax - renderedYMin < getMaxRenderedYRange()
        || Math.abs(yMaxPoint - yMinPoint) < EPSILON) {
      // If the requested points are inside of the max Y range allowed, save them.
      this.renderedYMin = renderedYMin;
      this.renderedYMax = renderedYMax;
    }
  }

  public void updateYMinAndMax(double minYPoint, double maxYPoint) {
    yMaxPoint = maxYPoint;
    yMinPoint = minYPoint;
  }

  public double getMaxRenderedYRange() {
    // Minimum range 1, maximum range 10 if we are getting ymin ~= ymax.
    return Math.max(10, (yMaxPoint - yMinPoint) * MAXIMUM_Y_SPREAD_FACTOR);
  }

  public void adjustYAxisStep(ChartData.DataPoint latestPoint) {
    if (latestPoint.getY() < yMinPoint) {
      yMinPoint = latestPoint.getY();
    }
    if (latestPoint.getY() > yMaxPoint) {
      yMaxPoint = latestPoint.getY();
    }
    double buffer = getYBuffer(yMinPoint, yMaxPoint);
    double idealYMax = yMaxPoint + buffer;
    double idealYMin = yMinPoint - buffer;

    double lastYMin = getRenderedYMin();
    double lastYMax = getRenderedYMax();

    if (lastYMax <= lastYMin) {
      // TODO do we need to do bounds checking?
      renderedYMin = idealYMin;
      renderedYMax = idealYMax;
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
    return showLeadingEdge;
  }

  public boolean isShowEndpoints() {
    return !showLeadingEdge;
  }

  public int getLineColor() {
    return lineColor;
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
    return numberFormat;
  }

  public void reset() {
    renderedXMin = Long.MAX_VALUE;
    renderedXMax = Long.MIN_VALUE;
    renderedYMin = Double.MAX_VALUE;
    renderedYMax = Double.MIN_VALUE;
    yMinPoint = Double.MAX_VALUE;
    yMaxPoint = Double.MIN_VALUE;
    requestResetZoomInY = false;
  }

  public void resetYAxisLimits() {
    yMinPoint = Double.MAX_VALUE;
    yMaxPoint = Double.MIN_VALUE;
  }

  public boolean canPan() {
    return canPanX || canPanY;
  }

  public boolean canPanX() {
    return canPanX;
  }

  public boolean canPanY() {
    return canPanY;
  }

  public boolean canZoom() {
    return canZoomX || canZoomY;
  }

  public boolean canZoomX() {
    return canZoomX;
  }

  public boolean canZoomY() {
    return canZoomY;
  }

  public boolean isPinnedToNow() {
    return pinnedToNow;
  }

  public void setPinnedToNow(boolean pinnedToNow) {
    this.pinnedToNow = pinnedToNow;
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
    return showLeadingEdge ? R.dimen.label_dot_radius : R.dimen.run_review_value_label_dot_radius;
  }

  public int getLabelOutlineRadiusId() {
    return showLeadingEdge
        ? R.dimen.label_dot_outline_radius
        : R.dimen.run_review_value_label_dot_background_radius;
  }

  public int getLabelFillColorId() {
    return showLeadingEdge
        ? R.color.graph_label_fill_color
        : R.color.run_review_graph_label_fill_color;
  }

  public int getLabelOutlineColor(Resources res) {
    return showLeadingEdge ? lineColor : res.getColor(R.color.chart_background_color);
  }

  public void setLineColor(int lineColor) {
    this.lineColor = lineColor;
  }

  public void setRecordingStartTime(long recordingStartTime) {
    this.recordingStartTime = recordingStartTime;
  }

  public long getRecordingStartTime() {
    return showOriginalRun ? originalStartTime : recordingStartTime;
  }

  public void setRecordingTimes(
      long recordingStartTime,
      long recordingEndTime,
      long originalStartTime,
      long originalEndTime) {
    this.recordingStartTime = recordingStartTime;
    this.recordingEndTime = recordingEndTime;
    this.originalStartTime = originalStartTime;
    this.originalEndTime = originalEndTime;
  }

  public long getRecordingEndTime() {
    return showOriginalRun ? originalEndTime : recordingEndTime;
  }

  public void setShowOriginalRun(boolean showOriginalRun) {
    this.showOriginalRun = showOriginalRun;
  }

  public boolean shouldDrawRecordingOverlay() {
    return recordingStartTime != RecordingMetadata.NOT_RECORDING
        && chartPlacementType == ChartPlacementType.TYPE_OBSERVE;
  }

  public void setShowStatsOverlay(boolean showStatsOverlay) {
    this.showStatsOverlay = showStatsOverlay;
  }

  public boolean shouldShowStatsOverlay() {
    if (chartPlacementType == ChartPlacementType.TYPE_RUN_REVIEW) {
      return showStatsOverlay;
    }
    return showStatsOverlay && shouldDrawRecordingOverlay();
  }

  public boolean isDisplayable(Label label, long recordingStartTime) {
    return isDisplayable(label, recordingStartTime, chartPlacementType);
  }

  // A label is displayable if:
  //   - It is after the recording start time, and we are recording
  //   - We are not recording
  public static boolean isDisplayable(
      Label label, long recordingStartTime, ChartPlacementType chartPlacementType) {
    if (chartPlacementType == ChartPlacementType.TYPE_RUN_REVIEW
        || chartPlacementType == ChartPlacementType.TYPE_PREVIEW_REVIEW) {
      return true;
    }
    return recordingStartTime != RecordingMetadata.NOT_RECORDING
        && label.getTimeStamp() >= recordingStartTime;
  }

  public void setScalarDisplayOptions(ScalarDisplayOptions scalarDisplayOptions) {
    this.scalarDisplayOptions = scalarDisplayOptions;
  }

  public ScalarDisplayOptions getScalarDisplayOptions() {
    return scalarDisplayOptions;
  }

  public void setTriggerValues(List<Double> values) {
    triggerValues = values;
  }

  public List<Double> getTriggerValues() {
    return triggerValues;
  }

  public boolean showYGrid() {
    return getChartPlacementType() != ChartOptions.ChartPlacementType.TYPE_PREVIEW_REVIEW;
  }
}
