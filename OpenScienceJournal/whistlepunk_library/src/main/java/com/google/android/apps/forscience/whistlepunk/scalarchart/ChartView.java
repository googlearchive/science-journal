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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.annotation.VisibleForTesting;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import java.util.ArrayList;
import java.util.List;

public class ChartView extends View {
  private static final String TAG = "ChartView";

  // Every now and then, force a full redraw instead of adding a point to the path.
  // This keeps us from having too many points offscreen.
  // Tweak this to optimize between performance and accuracy. Probably numbers around 500 are OK.
  public static final int DRAWN_POINTS_REDRAW_THRESHOLD = 400;

  // 1 second buffer for loading data, so that zooming and panning have some buffer before
  // they have to do a full reload again. This number can be tweaked for performance.
  private static final long BUFFER_MS = 1000;

  // If the path contains fewer than this number of points, it will be repopulated instead of
  // transformed. This value can be tweaked for performance as needed.
  private static final int MAXIMUM_NUM_POINTS_FOR_POPULATE_PATH = 10;

  // Constants describing the number of Y axis labels to show on a graph. No graph should have
  // more than 6 Y axis labels, or fewer than 3, and 5 is prefered on a new load.
  // If the number of labels is outside of the min/max range, the labeled positions will be
  // recalculated.
  private static final int PREFERRED_NUM_LABELS = 5;
  private static final int MINIMUM_NUM_LABELS = 3;
  private static final int MAXIMUM_NUM_LABELS = 6;

  private List<ExternalAxisController.InteractionListener> listeners = new ArrayList<>();

  private Paint backgroundPaint;

  private Paint pathPaint;
  private Path path;
  private boolean hasPath;
  private Matrix matrix = new Matrix();

  private Paint axisPaint;
  private Paint axisTextPaint;
  private float axisTextHeight;
  private float topPadding;
  private float bottomPadding;
  private float rightPadding;
  private float axisTextStartPadding;

  private List<Double> yAxisPoints = new ArrayList<>();
  // Number formatting takes a lot of allocations, so save formatted labels in a list.
  private ArrayList<String> yAxisPointLabels = new ArrayList<>();

  private Paint leadingEdgePaint;
  private float leadingEdgeRadius;
  private boolean leadingEdgeIsDrawn = false;

  private Paint endpointPaint;
  private float endpointInnerRadius;
  private float endpointOuterRadius;

  private Paint labelFillPaint;
  private Paint labelOutlinePaint;
  private float labelRadius;
  private float labelOutlineRadius;
  private Paint labelLinePaint;

  private ChartOptions chartOptions;
  private ChartData chartData;

  private float width = 1;
  private float height = 1;
  private float chartHeight;
  private float chartWidth;
  private RectF chartRect; // Save this to avoid reallocations.
  private RectF previousChartRect = new RectF();

  // These describe the minimum and maximum values which the path covers, in the coordinates
  // of the chart data. If the path is not being transformed, they should be the same as the
  // the ChartOptions.getRenderedXMin, XMax, YMin and YMax. If a change in rendered values has
  // occured, these values are used to figure out the transformation needed on the path.
  private long xMinForPathCalcs;
  private long xMaxForPathCalcs;
  private double yMinForPathCalcs;
  private double yMaxForPathCalcs;

  // These track how much data is covered in the path, and are only updated when the path is
  // redrawn.
  private long xMinInPath;
  private long xMaxInPath;

  // Whether we were previously pinned to now. This is used to decide whether to add a new data
  // point to the end of the chart, or pull in a lot of new data if there may be a gap.
  private boolean wasPinnedToNow;

  private boolean isDrawn = false;

  // For drawing the recording overlay
  private Paint recordingBackgroundPaint;
  private Paint recordingTimePaint;

  // For drawing stats
  private float statLineWidth;
  private Paint statMinMaxPaint;
  private Paint statAvgPaint;
  private Drawable minDrawable;
  private Drawable maxDrawable;
  private Drawable avgDrawable;
  private int statDrawableWidth;
  private Path statsPath;
  private float startPadding;
  private Drawable triggerDrawable;
  private int backgroundColor;
  private boolean exploreByTouchEnabled;

  public ChartView(Context context) {
    super(context);
    finishConstruction();
  }

  public ChartView(Context context, AttributeSet attrs) {
    super(context, attrs);
    finishConstruction();
  }

  public ChartView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    finishConstruction();
  }

  @TargetApi(21)
  public ChartView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    finishConstruction();
  }

  private void finishConstruction() {
    createPaints();
    path = new Path();
    statsPath = new Path();
  }

  private void createPaints() {
    pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    pathPaint.setStyle(Paint.Style.STROKE);
    axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    axisPaint.setStyle(Paint.Style.STROKE);
    axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    axisTextPaint.setTextAlign(Paint.Align.RIGHT);
    leadingEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    leadingEdgePaint.setStyle(Paint.Style.FILL);
    endpointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    endpointPaint.setStyle(Paint.Style.FILL);
    labelFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    labelOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    backgroundPaint = new Paint();

    recordingBackgroundPaint = new Paint();
    recordingBackgroundPaint.setStyle(Paint.Style.FILL);
    recordingTimePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    recordingTimePaint.setStyle(Paint.Style.STROKE);

    labelLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    statMinMaxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    statAvgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  }

  @Override
  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    float prevHeight = height;
    float prevWidth = width;
    height = getMeasuredHeight();
    width = getMeasuredWidth();
    topPadding = getPaddingTop();
    bottomPadding = getPaddingBottom();
    rightPadding = getResources().getDimensionPixelSize(R.dimen.stream_presenter_padding_sides);

    if (chartOptions == null || chartData == null) {
      return;
    }
    // If the height has changed, need to redraw the whole path!
    if (prevHeight != height || prevWidth != width) {
      initialize(chartOptions, chartData);
    }
  }

  private void measure() {
    Resources res = getResources();
    pathPaint.setPathEffect(
        new CornerPathEffect(res.getDimensionPixelSize(chartOptions.getCornerPathRadiusId())));
    pathPaint.setStrokeWidth(res.getDimensionPixelSize(chartOptions.getLineWidthId()));
    axisPaint.setStrokeWidth(res.getDimensionPixelSize(chartOptions.getAxisLabelsLineWidthId()));
    axisTextHeight = res.getDimensionPixelSize(chartOptions.getAxisLabelsTextSizeId());
    axisTextPaint.setTextSize(axisTextHeight);
    float chartStartPadding = res.getDimensionPixelSize(chartOptions.getChartStartPaddingId());
    axisTextStartPadding = res.getDimensionPixelSize(chartOptions.getAxisLabelsStartPaddingId());

    leadingEdgeRadius = res.getDimensionPixelSize(chartOptions.getLeadingEdgeRadiusId());
    endpointInnerRadius = res.getDimensionPixelSize(chartOptions.getEndpointInnerRadiusId());
    endpointOuterRadius = res.getDimensionPixelSize(chartOptions.getEndpointOutderRadiusId());

    labelRadius = res.getDimensionPixelSize(chartOptions.getLabelInnerRadiusId());
    labelOutlineRadius = res.getDimensionPixelSize(chartOptions.getLabelOutlineRadiusId());

    minDrawable = res.getDrawable(R.drawable.ic_data_min_color_12dpm);
    maxDrawable = res.getDrawable(R.drawable.ic_data_max_color_12dpm);
    avgDrawable = res.getDrawable(R.drawable.ic_data_average_color_12dp);
    statDrawableWidth = res.getDimensionPixelSize(R.dimen.small_stat_icon_size);

    statLineWidth = getResources().getDimensionPixelSize(R.dimen.recording_overlay_bar_width);
    recordingBackgroundPaint.setColor(res.getColor(R.color.recording_axis_overlay_color));
    recordingTimePaint.setStrokeWidth(
        getResources().getDimensionPixelSize(R.dimen.recording_overlay_time_line_width));
    recordingTimePaint.setColor(res.getColor(R.color.recording_axis_bar_color));

    float dashSize = res.getDimensionPixelSize(R.dimen.recording_overlay_dash_size);
    makeDashedLinePaint(labelLinePaint, R.color.note_overlay_line_color, statLineWidth, dashSize);
    makeDashedLinePaint(statMinMaxPaint, statLineWidth, dashSize);
    makeDashedLinePaint(statAvgPaint, R.color.stats_average_color, statLineWidth, dashSize);

    // These calculations are done really frequently, so store in member variables to reduce
    // cycles.
    startPadding = chartStartPadding + getPaddingLeft();
    chartHeight = height - bottomPadding - topPadding;
    chartWidth = width - startPadding - rightPadding;

    chartRect =
        new RectF(startPadding, topPadding, chartWidth + startPadding, chartHeight + topPadding);
  }

  private void makeDashedLinePaint(Paint paint, int colorId, float lineWidth, float dashSize) {
    makeDashedLinePaint(paint, lineWidth, dashSize);
    paint.setColor(getResources().getColor(colorId));
  }

  private void makeDashedLinePaint(Paint paint, float lineWidth, float dashSize) {
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(lineWidth);
    paint.setPathEffect(new DashPathEffect(new float[] {dashSize, dashSize}, dashSize));
  }

  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus) {
    super.onWindowFocusChanged(hasWindowFocus);
    if (hasWindowFocus) {
      // The user could have changed the accessibility mode while we were in the background,
      // so just get it when window focus changes.
      exploreByTouchEnabled =
          ((AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE))
              .isTouchExplorationEnabled();
    }
  }

  public void initialize(ChartOptions chartOptions, ChartData chartData) {
    this.chartOptions = chartOptions;
    this.chartData = chartData;
    measure();
    if (width <= 1 || height <= 1) {
      return;
    }
    updateColorOptions();
    populatePath(false);

    final boolean isObserve =
        this.chartOptions.getChartPlacementType() == ChartOptions.ChartPlacementType.TYPE_OBSERVE;

    if (this.chartOptions.canPan() || this.chartOptions.canZoom()) {
      ViewConfiguration vc = ViewConfiguration.get(getContext());
      final float touchSlop = vc.getScaledTouchSlop();
      setOnTouchListener(
          new OnTouchListener() {
            private final long DOUBLE_TAP_MAX_TIME = ViewConfiguration.getDoubleTapTimeout();
            private final long DOUBLE_TAP_MIN_TIME = 40;

            private final float ZOOM_SLOP = touchSlop * 4;

            private float touchX;
            private float touchY;
            private int downIndex;
            private float xZoomSpan;
            private float yZoomSpan;
            private long previousDownTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
              int type = event.getActionMasked();
              int numPointers = event.getPointerCount();
              if (type == MotionEvent.ACTION_DOWN) {
                downIndex = 0;
                updateTouchPoints(event, downIndex);
                long tapDelta = event.getDownTime() - previousDownTime;
                if (!isObserve
                    && DOUBLE_TAP_MIN_TIME < tapDelta
                    && tapDelta < DOUBLE_TAP_MAX_TIME) {
                  resetZoom();
                } else {
                  for (ExternalAxisController.InteractionListener listener : listeners) {
                    listener.onStartInteracting();
                  }
                }
                previousDownTime = event.getDownTime();
                return true;
              } else if (type == MotionEvent.ACTION_POINTER_DOWN) {
                downIndex = event.getActionIndex() == 1 ? 0 : 1;
                updateTouchPoints(event, downIndex);
                this.xZoomSpan = Math.abs(event.getX(0) - event.getX(1));
                this.yZoomSpan = Math.abs(event.getY(0) - event.getY(1));
                return true;
              } else if (type == MotionEvent.ACTION_MOVE) {
                float oldTouchX = touchX;
                float oldTouchY = touchY;
                updateTouchPoints(event, downIndex);

                boolean hasPannedX = false;
                boolean hasPannedY = false;
                if (ChartView.this.chartOptions.canPanX()) {
                  long dPanX = getXValueDeltaFromXScreenDelta(oldTouchX - touchX);
                  ChartView.this.chartOptions.setRenderedXRange(
                      ChartView.this.chartOptions.getRenderedXMin() + dPanX,
                      ChartView.this.chartOptions.getRenderedXMax() + dPanX);
                  hasPannedX = true;
                }
                if (ChartView.this.chartOptions.canPanY()) {
                  double dPanY = getYValueDeltaFromYScreenDelta(touchY - oldTouchY);
                  doYPan(dPanY);
                  hasPannedY = true;
                }

                boolean hasZoomedX = false;
                boolean hasZoomedY = false;
                if (numPointers > 1) {
                  float xZoomSpan = Math.abs(event.getX(0) - event.getX(1));
                  float yZoomSpan = Math.abs(event.getY(0) - event.getY(1));
                  if (ChartView.this.chartOptions.canZoomX() && xZoomSpan > touchSlop) {
                    float scale = this.xZoomSpan / xZoomSpan;
                    long newDiff =
                        (long)
                            ((ChartView.this.chartOptions.getRenderedXMax()
                                    - ChartView.this.chartOptions.getRenderedXMin())
                                * scale);
                    long avg =
                        (ChartView.this.chartOptions.getRenderedXMax()
                                + ChartView.this.chartOptions.getRenderedXMin())
                            / 2;

                    ChartView.this.chartOptions.setRenderedXRange(
                        avg - newDiff / 2, avg + newDiff / 2);
                    this.xZoomSpan = xZoomSpan;
                    hasZoomedX = true;
                  } else {
                    this.xZoomSpan = xZoomSpan;
                  }
                  // Zooming in Y can happen accidentally so make sure the user isn't just
                  // being sloppy but really means to zoom in Y.
                  if (ChartView.this.chartOptions.canZoomY() && yZoomSpan > ZOOM_SLOP) {
                    // Limit the amount of scale we can do in a cycle.
                    float scale = Math.max(0.5f, Math.min(2, this.yZoomSpan / yZoomSpan));
                    double newDiff =
                        Math.min(
                                ChartView.this.chartOptions.getMaxRenderedYRange(),
                                ChartView.this.chartOptions.getRenderedYMax()
                                    - ChartView.this.chartOptions.getRenderedYMin())
                            * scale;
                    double avg =
                        (ChartView.this.chartOptions.getRenderedYMax()
                                + ChartView.this.chartOptions.getRenderedYMin())
                            / 2;
                    double newMin =
                        isObserve
                            ? avg - newDiff / 2
                            : Math.max(
                                avg - newDiff / 2, ChartView.this.chartOptions.getYMinLimit());
                    double newMax =
                        isObserve
                            ? avg + newDiff / 2
                            : Math.min(
                                avg + newDiff / 2, ChartView.this.chartOptions.getYMaxLimit());
                    ChartView.this.chartOptions.setRenderedYRange(newMin, newMax);
                    hasZoomedY = true;
                    this.yZoomSpan = yZoomSpan;
                  } else {
                    this.yZoomSpan = yZoomSpan;
                  }
                }

                // Don't actually update the rendered axis limits here. Instead,
                // let the ExternalAxisController do that on updateAxis via callbacks.
                // This avoids a little shiver that happens when panning / zooming at the
                // limits. However, if only the Y zoom has changed, update that immediately,
                // because there are no callbacks from the external axis or other listeners
                // to update a y zoom.
                if ((hasZoomedY || hasPannedY) && (!hasPannedX && !hasZoomedX)) {
                  transformPath();
                }

                // Now do stuff that impacts the X axis.
                if (hasPannedX || hasZoomedX) {
                  for (ExternalAxisController.InteractionListener listener : listeners) {
                    // No need to zoom and pan, this double-calls listeners.
                    // Try zooming first, then panning.
                    if (hasZoomedX) {
                      listener.onZoom(
                          ChartView.this.chartOptions.getRenderedXMin(),
                          ChartView.this.chartOptions.getRenderedXMax());
                    } else if (hasPannedX) {
                      listener.onPan(
                          ChartView.this.chartOptions.getRenderedXMin(),
                          ChartView.this.chartOptions.getRenderedXMax());
                    }
                  }
                }

                // If we made a chart movement here, don't let the parent steal the event.
                boolean eventUsed =
                    (hasPannedX && significantPan(touchX - oldTouchX))
                        || hasPannedY
                        || hasZoomedX
                        || hasZoomedY;
                if (eventUsed) {
                  getParent().requestDisallowInterceptTouchEvent(true);
                }
                return eventUsed;
              } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                int upIndex = event.getActionIndex();
                // Whichever finger is still down gets to keep panning, so we need to update
                // touchX and touchY.
                int stillDownIndex = upIndex == 1 ? 0 : 1;
                updateTouchPoints(event, stillDownIndex);
                downIndex = 0;
                return true;
              } else if (event.getAction() == MotionEvent.ACTION_UP
                  || event.getAction() == MotionEvent.ACTION_POINTER_UP
                  || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                for (ExternalAxisController.InteractionListener listener : listeners) {
                  listener.onStopInteracting();
                }
                if (event.getAction() == MotionEvent.ACTION_UP && exploreByTouchEnabled) {
                  // A single quick tap when explore by touch is enabled is actually a
                  // double-tap from the user, so we should zoom out.
                  // Single tapping doesn't do anything anyway so this is the correct
                  // behavior.
                  long tapDelta = event.getEventTime() - previousDownTime;
                  if (!isObserve && tapDelta < DOUBLE_TAP_MAX_TIME / 2) {
                    resetZoom();
                  }
                }
                return true;
              }
              getParent().requestDisallowInterceptTouchEvent(false);
              return false;
            }

            // Pans the Y axis by dPanY without passing the limits defined by ChartOptions.
            private void doYPan(double dPanY) {
              double prevMin = ChartView.this.chartOptions.getRenderedYMin();
              double prevMax = ChartView.this.chartOptions.getRenderedYMax();

              double limitMin = ChartView.this.chartOptions.getYMinLimit();
              double limitMax = ChartView.this.chartOptions.getYMaxLimit();

              if (prevMin + dPanY < limitMin) {
                dPanY = limitMin - prevMin;
              }
              if (prevMax + dPanY > limitMax) {
                dPanY = prevMax - limitMax;
              }
              ChartView.this.chartOptions.setRenderedYRange(prevMin + dPanY, prevMax + dPanY);
            }

            private boolean significantPan(float panDistance) {
              return Math.abs(panDistance) >= touchSlop;
            }

            private void updateTouchPoints(MotionEvent event, int panPointerIndex) {
              touchX = event.getX(panPointerIndex);
              touchY = event.getY(panPointerIndex);
            }

            private void resetZoom() {
              // Assume this is a double-tap, reset zoom if we aren't in observe.
              ChartView.this.chartOptions.requestResetZoomInY();
              for (ExternalAxisController.InteractionListener listener : listeners) {
                listener.onStartInteracting();
                listener.requestResetZoom();
              }
            }
          });
    }
  }

  public void updateColorOptions() {
    int chartColor = chartOptions.getLineColor();
    pathPaint.setColor(chartColor);
    Resources res = getResources();
    axisPaint.setColor(res.getColor(chartOptions.getAxisLabelsLineColorId()));
    axisTextPaint.setColor(res.getColor(chartOptions.getLabelsTextColorId()));
    leadingEdgePaint.setColor(chartColor);
    endpointPaint.setColor(chartColor);
    labelFillPaint.setColor(res.getColor(chartOptions.getLabelFillColorId()));
    labelOutlinePaint.setColor(chartOptions.getLabelOutlineColor(res));
    backgroundColor = res.getColor(chartOptions.getChartBackgroundColorId());
    backgroundPaint.setColor(backgroundColor);

    statMinMaxPaint.setColor(chartColor);
    minDrawable = minDrawable.mutate();
    minDrawable.setColorFilter(chartColor, PorterDuff.Mode.SRC_ATOP);
    maxDrawable = maxDrawable.mutate();
    maxDrawable.setColorFilter(chartColor, PorterDuff.Mode.SRC_ATOP);
  }

  private double getYValueDeltaFromYScreenDelta(float screenDiff) {
    double valueDiff = chartOptions.getRenderedYMax() - chartOptions.getRenderedYMin();
    return screenDiff / chartHeight * valueDiff;
  }

  private long getXValueDeltaFromXScreenDelta(float screenDiff) {
    long timeDiff = chartOptions.getRenderedXMax() - chartOptions.getRenderedXMin();
    return (long) (screenDiff / chartWidth * timeDiff);
  }

  public void addInteractionListener(ExternalAxisController.InteractionListener listener) {
    listeners.add(listener);
  }

  public void clearInteractionListeners() {
    listeners.clear();
  }

  public void clear() {
    isDrawn = false;
    redraw();
  }

  public void redraw() {
    yAxisPoints.clear();
    yAxisPointLabels.clear();
    populatePath(false);
    postInvalidateOnAnimation();
  }

  /**
   * Gets the screen x coordinate for a data point x value.
   *
   * @param x The data point timestamp
   * @return The screen x coordinate
   */
  public float getScreenX(long x) {
    long xMax = chartOptions.getRenderedXMax();
    long xMin = chartOptions.getRenderedXMin();
    float result = (1.0f * (x - xMin)) / (xMax - xMin) * chartWidth + startPadding;
    return result;
  }

  /**
   * Gets the screen y coordinate for a data point y value.
   *
   * @param y The data point value
   * @return The screen y coordinate
   */
  public float getScreenY(double y) {
    double yMax = chartOptions.getRenderedYMax();
    double yMin = chartOptions.getRenderedYMin();
    double result = chartHeight * (1 - ((y - yMin) / (yMax - yMin))) + topPadding;
    return (float) result;
  }

  /**
   * Gets the long x value for a screen x coordinate. This is useful when figuring out what point a
   * user is touching, for example.
   *
   * @param x The screen x
   * @return The long value that corresponds to the x value of the point on the chart.
   */
  public long getXFromScreenX(float x) {
    return chartOptions.getRenderedXMin() + getXValueDeltaFromXScreenDelta(x - startPadding);
  }

  /**
   * Gets the long y value for a screen y coordinate. This is useful when figuring out what point a
   * user is touching, for example.
   *
   * @param y The screen y
   * @return The double value that corresponds to the y value of the point on the chart.
   */
  public double getYFromScreenY(float y) {
    return chartOptions.getRenderedYMin()
        + getYValueDeltaFromYScreenDelta(height - bottomPadding - y);
  }

  /** Populates the path from the chart data, from scratch. */
  private void populatePath(boolean optimizePinnedToEnd) {
    int numPoints = chartData.getNumPoints();
    path.reset();

    if (numPoints == 0) {
      return;
    }

    // Just get the points in the range that we want to render, instead of all the points.
    // Adds some buffer to the load in case of scrolling, if those data points are available.
    updatePathCalcs();
    List<ChartData.DataPoint> points;
    if (optimizePinnedToEnd) {
      // This is a slightly more efficient call, so use it when possible.
      points = chartData.getPointsInRangeToEnd(chartOptions.getRenderedXMin() - BUFFER_MS);
    } else {
      points =
          chartData.getPointsInRange(
              chartOptions.getRenderedXMin() - BUFFER_MS,
              chartOptions.getRenderedXMax() + BUFFER_MS);
    }
    int numPlottedPoints = points.size();
    if (numPlottedPoints == 0) {
      return;
    }
    path.moveTo(getPathX(points.get(0).getX()), getPathY(points.get(0).getY()));
    for (int i = 1; i < numPlottedPoints; i++) {
      path.lineTo(getPathX(points.get(i).getX()), getPathY(points.get(i).getY()));
    }
    hasPath = true;

    // Only update these when the path is redrawn. They track how much data the path covers.
    xMinInPath = points.get(0).getX();
    xMaxInPath = points.get(numPlottedPoints - 1).getX();
  }

  /**
   * Efficiently adds data points to a chart view by adding them to the existing path and then
   * transforming the path based on updated renderer values. This reduces the need to recalculate
   * all the points in the path every time a new point is added.
   *
   * @param point The data point to add to the end of the path.
   */
  public void addPointToEndOfPath(ChartData.DataPoint point) {
    int numPoints = chartData.getNumPoints();
    if (!hasPath
        || numPoints < MAXIMUM_NUM_POINTS_FOR_POPULATE_PATH
        || (numPoints % DRAWN_POINTS_REDRAW_THRESHOLD == 0 && chartOptions.isPinnedToNow())) {
      populatePath(true);
      postInvalidateOnAnimation();
    } else {
      if (chartOptions.isPinnedToNow() && !wasPinnedToNow) {
        populatePath(true);
        postInvalidateOnAnimation();
      } else if ((chartOptions.isPinnedToNow())
          || chartOptions.getRenderedXMax() >= point.getX()
          || leadingEdgeIsDrawn) {
        // Add the point to the end only if the end is being rendered.
        // The path is in the previous coordinates, so we can add a point using those
        // mins/maxes.
        path.lineTo(getPathX(point.getX()), getPathY(point.getY()));
        xMaxInPath = point.getX();
      }
    }
    wasPinnedToNow = chartOptions.isPinnedToNow();
  }

  /** Transform the path by stretching and translating it to meet the new rendered size. */
  public void transformPath() {
    // The path needs to be scaled in X and Y based on the range of the new data points.
    matrix.reset();
    previousChartRect.set(
        getScreenX(xMinForPathCalcs),
        getScreenY(yMaxForPathCalcs),
        getScreenX(xMaxForPathCalcs),
        getScreenY(yMinForPathCalcs));
    matrix.setRectToRect(chartRect, previousChartRect, Matrix.ScaleToFit.FILL);
    path.transform(matrix);

    updatePathCalcs();
    postInvalidateOnAnimation();
  }

  private void updatePathCalcs() {
    xMaxForPathCalcs = chartOptions.getRenderedXMax();
    xMinForPathCalcs = chartOptions.getRenderedXMin();
    yMinForPathCalcs = chartOptions.getRenderedYMin();
    yMaxForPathCalcs = chartOptions.getRenderedYMax();
  }

  // Gets the X coordinate of a point in the current path coordinates, which may be different
  // from the rendered min/max coordinates if the path has not yet been transformed and drawn.
  // This should just be used when drawing the path.
  private float getPathX(long x) {
    return (1.0f * (x - xMinForPathCalcs)) / (xMaxForPathCalcs - xMinForPathCalcs) * chartWidth
        + startPadding;
  }

  // Gets the Y coordinate of a point in the current path coordinates, which may be different
  // from the rendered min/max coordinates if the path has not yet been transformed and drawn.
  // This should just be used when drawing the path.
  private float getPathY(double y) {
    return (float)
        (chartHeight * (1 - ((y - yMinForPathCalcs) / (yMaxForPathCalcs - yMinForPathCalcs)))
            + topPadding);
  }

  @Override
  public void onDraw(Canvas canvas) {
    canvas.drawColor(backgroundColor);

    if (chartData == null || chartData.getNumPoints() == 0) {
      return;
    }

    if (chartOptions.showYGrid()) {
      updateYAxisPoints();
    }

    // Draw the Y label lines under the path.
    drawYAxis(canvas);
    canvas.drawPath(path, pathPaint);
    // Try drawing the endpoints, if they are needed.
    tryDrawingEndpoints(canvas);

    // Draw the labels.
    drawLabels(canvas);

    // Draw the stats and recording overlay if needed.
    if (chartOptions.shouldDrawRecordingOverlay()) {
      drawRecordingOverlay(canvas);
    }

    // Draw the stats if needed
    if (chartOptions.shouldShowStatsOverlay()) {
      drawStats(canvas);
    }

    // White out the margins
    canvas.drawRect(0, 0, startPadding, height, backgroundPaint);
    canvas.drawRect(0, 0, width, topPadding, backgroundPaint);
    canvas.drawRect(0, height - bottomPadding, width, height, backgroundPaint);
    canvas.drawRect(width - rightPadding, 0, width, height, backgroundPaint);

    drawTriggers(canvas);

    if (chartOptions.showYGrid()) {
      // Draw the Y label text above the rect that whites out the Y label axis.
      drawYAxisText(canvas);
    }

    isDrawn = true;
  }

  public boolean isDrawn() {
    return isDrawn;
  }

  private void drawTriggers(Canvas canvas) {
    List<Double> triggerValues = chartOptions.getTriggerValues();
    if (triggerValues == null || triggerValues.size() == 0) {
      return;
    }
    Drawable drawable = getTriggerDrawable();
    int width = drawable.getIntrinsicWidth();
    int height = drawable.getIntrinsicHeight();
    for (double value : triggerValues) {
      float y = getScreenY(value);
      if (y + height < 0 || y - height > this.height) {
        continue;
      }
      drawable.setBounds(
          (int) (startPadding - width - axisTextStartPadding),
          (int) y - height / 2,
          (int) (startPadding - axisTextStartPadding),
          (int) y + height / 2);
      drawable.draw(canvas);
    }
  }

  private Drawable getTriggerDrawable() {
    // Lazy init, as most charts probably won't need triggers.
    if (triggerDrawable == null) {
      Resources res = getResources();
      triggerDrawable = res.getDrawable(R.drawable.ic_label_black_24dp);
      triggerDrawable
          .mutate()
          .setColorFilter(res.getColor(R.color.color_accent), PorterDuff.Mode.SRC_IN);
      triggerDrawable.setAlpha(res.getInteger(R.integer.trigger_drawable_alpha));
    }
    return triggerDrawable;
  }

  private void drawLabels(Canvas canvas) {
    List<ChartData.DataPoint> labels = chartData.getLabelPoints();
    for (ChartData.DataPoint label : labels) {
      if (label.getX() < xMinInPath || label.getX() > xMaxInPath) {
        continue;
      }
      float x = getScreenX(label.getX());
      float y = getScreenY(label.getY());
      if (chartOptions.shouldDrawRecordingOverlay()) {
        statsPath.reset();
        statsPath.moveTo(x, 0);
        statsPath.lineTo(x, height);
        canvas.drawPath(statsPath, labelLinePaint);
      }
      canvas.drawCircle(x, y, labelOutlineRadius, labelOutlinePaint);
      canvas.drawCircle(x, y, labelRadius, labelFillPaint);
    }
  }

  private void tryDrawingEndpoints(Canvas canvas) {
    if (chartOptions.isShowLeadingEdge()) {
      ChartData.DataPoint point = chartData.getPoints().get(chartData.getNumPoints() - 1);
      if (point.getX() == xMaxInPath && xMaxInPath <= xMaxForPathCalcs) {
        leadingEdgeIsDrawn = true;
        canvas.drawCircle(
            getScreenX(point.getX()),
            getScreenY(point.getY()),
            leadingEdgeRadius,
            leadingEdgePaint);
      } else {
        leadingEdgeIsDrawn = false;
      }
    } else if (chartOptions.isShowEndpoints()) {
      // Only try to draw the endpoints if the range shown contains the recording
      // start and/or end times.
      if (chartOptions.getRenderedXMin() < chartOptions.getRecordingStartTime()
          && chartOptions.getRecordingStartTime() < chartOptions.getRenderedXMax()) {
        ChartData.DataPoint start = chartData.getPoints().get(0);
        if (start.getX() >= xMinForPathCalcs) {
          float screenX = getScreenX(start.getX());
          float screenY = getScreenY(start.getY());
          canvas.drawCircle(screenX, screenY, endpointOuterRadius, endpointPaint);
          canvas.drawCircle(screenX, screenY, endpointInnerRadius, backgroundPaint);
        }
      }
      if (chartOptions.getRenderedXMin() < chartOptions.getRecordingEndTime()
          && chartOptions.getRecordingEndTime() < chartOptions.getRenderedXMax()) {
        ChartData.DataPoint end = chartData.getPoints().get(chartData.getNumPoints() - 1);
        if (end.getX() <= xMaxForPathCalcs) {
          float screenX = getScreenX(end.getX());
          float screenY = getScreenY(end.getY());
          canvas.drawCircle(screenX, screenY, endpointOuterRadius, endpointPaint);
          canvas.drawCircle(screenX, screenY, endpointInnerRadius, backgroundPaint);
        }
      }
    }
  }

  private void drawYAxis(Canvas canvas) {
    // Now go through the points to label and draw the horizontal label line.
    for (int i = 0; i < yAxisPoints.size(); i++) {
      float yValue = getScreenY(yAxisPoints.get(i));
      canvas.drawLine(startPadding, yValue, width, yValue, axisPaint);
    }
  }

  private void drawYAxisText(Canvas canvas) {
    // Now go through the points to label and draw the label text
    for (int i = 0; i < yAxisPoints.size(); i++) {
      double pointValue = yAxisPoints.get(i);
      float yValue = getScreenY(pointValue);
      if (textWillBeCropped(yValue, axisTextHeight)) {
        continue;
      }
      canvas.drawText(
          yAxisPointLabels.get(i),
          startPadding - axisTextStartPadding,
          yValue + axisTextHeight / 3,
          axisTextPaint);
    }
  }

  // Don't draw labels if they will be cropped.
  private boolean textWillBeCropped(float y, float textHeight) {
    return y + textHeight > height || y - textHeight < 0;
  }

  private void drawRecordingOverlay(Canvas canvas) {
    float xRecordingStart = getScreenX(chartOptions.getRecordingStartTime());
    if (xRecordingStart < 0) {
      canvas.drawRect(
          0, topPadding, width - rightPadding, height - bottomPadding, recordingBackgroundPaint);
    } else {
      canvas.drawRect(
          xRecordingStart,
          topPadding,
          width - rightPadding,
          height - bottomPadding,
          recordingBackgroundPaint);
      canvas.drawLine(
          xRecordingStart, topPadding, xRecordingStart, height - bottomPadding, recordingTimePaint);
    }
  }

  private void drawStats(Canvas canvas) {
    for (StreamStat stat : chartData.getStats()) {
      float yValue = getScreenY(stat.getValue());
      statsPath.reset();
      statsPath.moveTo(statDrawableWidth + startPadding, yValue);
      statsPath.lineTo(width, yValue);
      canvas.drawPath(statsPath, getStatPaint(stat.getType()));
      drawStatDrawable((int) yValue, canvas, stat.getType());
    }
  }

  private Paint getStatPaint(int type) {
    switch (type) {
      case StreamStat.TYPE_MIN:
      case StreamStat.TYPE_MAX:
        return statMinMaxPaint;
      case StreamStat.TYPE_AVERAGE:
        return statAvgPaint;
      default:
        return statAvgPaint;
    }
  }

  private void drawStatDrawable(int yOffset, Canvas canvas, int type) {
    Drawable toDraw;
    int startPadding = (int) this.startPadding;
    switch (type) {
      case StreamStat.TYPE_MIN:
        toDraw = minDrawable;
        toDraw.setBounds(
            startPadding,
            yOffset - statDrawableWidth + ((int) statLineWidth) * 2,
            statDrawableWidth + startPadding,
            yOffset + ((int) statLineWidth) * 2);
        break;
      case StreamStat.TYPE_MAX:
        toDraw = maxDrawable;
        toDraw.setBounds(
            startPadding,
            yOffset - ((int) statLineWidth) * 2,
            statDrawableWidth + startPadding,
            yOffset + statDrawableWidth - ((int) statLineWidth) * 2);
        break;
      case StreamStat.TYPE_AVERAGE:
        toDraw = avgDrawable;
        toDraw.setBounds(
            startPadding,
            yOffset - statDrawableWidth / 2,
            statDrawableWidth + startPadding,
            yOffset + statDrawableWidth / 2);
        break;
      default:
        return;
    }
    toDraw.draw(canvas);
  }

  private void updateYAxisPoints() {
    // Calculate how many labels and figure out where they should go
    double yMin = chartOptions.getRenderedYMin();
    double yMax = chartOptions.getRenderedYMax();
    if (yMax < yMin) {
      return;
    }
    int sizeShown = calculateSizeShownNext(yAxisPoints, yMin, yMax);
    if (sizeShown < MINIMUM_NUM_LABELS || sizeShown > MAXIMUM_NUM_LABELS) {
      double range = yMax - yMin;
      if (Double.isNaN(range) || range <= 0) {
        range = ChartOptions.MINIMUM_Y_SPREAD;
      }
      int increment = (int) Math.ceil(range / (PREFERRED_NUM_LABELS * 1.0));
      int labelStart = increment * ((int) yMin / increment);
      yAxisPoints.clear();
      yAxisPointLabels.clear();

      int count = 0;
      for (int i = labelStart; i < yMax + increment; i += increment) {
        yAxisPoints.add(count++, i * 1.0);
        yAxisPointLabels.add(chartOptions.getAxisNumberFormat().format(i * 1.0));
      }
    } else {
      // Figure out if any can be added or removed at the same increment value.
      double increment = yAxisPoints.get(1) - yAxisPoints.get(0);

      double nextSmallerLabel = yAxisPoints.get(0) - increment;
      while (nextSmallerLabel > yMin) {
        yAxisPoints.add(0, nextSmallerLabel);
        yAxisPointLabels.add(0, chartOptions.getAxisNumberFormat().format(nextSmallerLabel));
        nextSmallerLabel -= increment;
      }
      double nextLargerLabel = yAxisPoints.get(yAxisPoints.size() - 1) + increment;
      while (nextLargerLabel < yMax) {
        yAxisPoints.add(nextLargerLabel);
        yAxisPointLabels.add(chartOptions.getAxisNumberFormat().format(nextLargerLabel));
        nextLargerLabel += increment;
      }
    }
  }

  /**
   * Calculates the number of y axis points shown if the same points are used for labeling the
   * updated range shown. If the possible points list is 0 or 1, there is no way to calculate the
   * difference between y axis labels, so this method returns 0 to prompt a label recalculation.
   *
   * @param yAxisPoints
   * @param yMinShown
   * @param yMaxShown
   * @return
   */
  @VisibleForTesting
  static int calculateSizeShownNext(List<Double> yAxisPoints, double yMinShown, double yMaxShown) {
    // If there are 1 or 0 points, we need to re-show anyway, so just return 0.
    if (yAxisPoints.size() < 2) {
      return 0;
    }
    // If we are already labeling points on the Y axis, count how many labels would be
    // drawn if we keep the same points labeled but used the new range. This tells us if we
    // need to recalculate labeled points or not.
    double increment = (yAxisPoints.get(1) - yAxisPoints.get(0));
    int startIndex = (int) Math.floor((yMinShown - yAxisPoints.get(0)) / increment + 1);
    int endIndex =
        (int) Math.ceil((yMaxShown - yAxisPoints.get(yAxisPoints.size() - 1)) / increment)
            + yAxisPoints.size()
            - 1;
    return endIndex - startIndex;
  }

  public void onAxisLimitsAdjusted() {
    // Uses transformPath() instead of populatePath() when possible, i.e. when
    // the range loaded (xMinInPath to xMaxInPath) is within the rendered
    // range desired (getRenderedXMax and getRenderedXMin).
    if (chartData.isEmpty()) {
      return;
    }
    boolean newRangeOutsideOfPathRange =
        (chartOptions.getRenderedXMax() > xMaxInPath && xMaxInPath < chartData.getXMax())
            || (chartOptions.getRenderedXMin() < xMinInPath && xMinInPath > chartData.getXMin());
    boolean newRangeTooLarge = getScreenX(xMaxInPath) - getScreenX(xMinInPath) > width * 2;
    if (newRangeOutsideOfPathRange || newRangeTooLarge) {
      populatePath(false);
      postInvalidateOnAnimation();
    } else {
      transformPath();
    }
  }
}

