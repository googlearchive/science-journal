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

package com.google.android.apps.forscience.whistlepunk;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;
import com.google.android.apps.forscience.whistlepunk.metadata.CropHelper;
import com.google.android.apps.forscience.whistlepunk.review.CoordinatedSeekbarViewGroup;
import com.google.android.apps.forscience.whistlepunk.review.CropSeekBar;
import com.google.android.apps.forscience.whistlepunk.review.GraphExploringSeekBar;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;

/** Draws the value over a RunReview chart. */
public class RunReviewOverlay extends View implements ChartController.ChartDataLoadedCallback {

  private static final String TAG = "RunReviewOverlay";

  // To avoid getting into an infinite loop, only try to refresh if the chart viewtreeobserver is
  // not alive so many times.
  private static final int MAX_REFRESH_ATTEMPTS = 5;

  public interface OnTimestampChangeListener {
    void onTimestampChanged(long timestamp);
  }

  private OnTimestampChangeListener timestampChangeListener;

  public interface OnSeekbarTouchListener {
    void onTouchStart();

    void onTouchStop();
  }

  private OnSeekbarTouchListener onSeekbarTouchListener;

  public interface OnLabelClickListener {
    void onValueLabelClicked();

    void onCropStartLabelClicked();

    void onCropEndLabelClicked();
  }

  private OnLabelClickListener onLabelClickListener;

  // Class to track the measurements of a RunReview overlay flag, which is bounded by
  // boxStart/End/Top/Bottom, and has a notch below it down to a certain height.
  private static class FlagMeasurements {
    public float boxStart;
    public float boxEnd;
    public float boxTop;
    public float boxBottom;
    public float notchBottom;
  }
  // Save allocations by just keeping one of these measurements around.
  private FlagMeasurements flagMeasurements = new FlagMeasurements();

  private static double SQRT_2_OVER_2 = Math.sqrt(2) / 2;

  // A constant used to denote that a coordinate in screen coordinates is offscreen.
  private static float OFFSCREEN = -1f;

  private int height;
  private int width;
  private int paddingBottom;
  private int chartPaddingTop;
  private int chartHeight;
  private int chartMarginLeft;
  private int chartMarginRight;

  // Flag text padding.
  private int labelPadding;

  // Space between the time label and value label in a flag.
  private int intraLabelPadding;

  // Height of the triangle notch at the bottom of a flag.
  private int notchHeight;

  // Radius of the corner of the flag.
  private int cornerRadius;

  // Amount of buffer a flag must keep between itself and the body of the flag after it.
  private int cropFlagBufferX;

  private Paint paint;
  private Paint dotPaint;
  private Paint dotBackgroundPaint;
  private Paint textPaint;
  private Paint timePaint;
  private Paint linePaint;
  private Paint centerLinePaint;
  private Paint cropBackgroundPaint;
  private Paint cropVerticalLinePaint;

  public static final long NO_TIMESTAMP_SELECTED = -1;

  private long previouslySelectedTimestamp;
  private long previousCropStartTimestamp;
  private long previousCropEndTimestamp;
  private boolean chartIsLoading = false;

  // Represents the data associated with a seekbar point tracked in RunReviewOverlay.
  private static class OverlayPointData {
    // The currently selected timestamp for this seekbar.
    public long timestamp = NO_TIMESTAMP_SELECTED;

    // The value for the currently selected timestamp.
    public double value;

    // The screen point of the currently selected value.
    public PointF screenPoint;

    // The string representing the current chart value for the standard overlay label.
    public String label;

    // The rect representing where the label was drawn.
    public RectF labelRect = new RectF(OFFSCREEN, OFFSCREEN, OFFSCREEN, OFFSCREEN);
  }

  private OverlayPointData pointData = new OverlayPointData();
  private GraphExploringSeekBar seekbar;

  // TODO: Consider moving crop fields and logic into a CropController class.
  private OverlayPointData cropStartData = new OverlayPointData();
  private OverlayPointData cropEndData = new OverlayPointData();
  private CoordinatedSeekbarViewGroup cropSeekbarGroup;

  // When one of the crop seekbars' progress is changed we sometimes need to update the progress
  // bars again in order to match the closest point to the location on the seekbar.
  // Because the crop seekbars' positions may interact with each other (they cannot be closer
  // than 1 second or 5% of their length), both seekbars' updated values are calculated at
  // the same time.
  // This variable tracks whether we are still waiting for a progress update from the second
  // crop seekbar, and is used to decide whether to refresh the data or wait until the second
  // seekbar's update comes in.
  // This prevents refreshing from happening too frequently or before the second seekbar has
  // a chance to have its progress updated.
  // TODO: Is there a cleaner way to do this?
  private boolean ignoreNextSeekbarProgressUpdate;

  private ChartController chartController;
  private ExternalAxisController externalAxis;
  private String textFormat;
  private ElapsedTimeAxisFormatter timeFormat;
  private Path path;
  private float dotRadius;
  private float dotBackgroundRadius;
  private Drawable thumb;
  private ViewTreeObserver.OnDrawListener onDrawListener;

  private boolean isCropping;

  public RunReviewOverlay(Context context) {
    super(context);
    init();
  }

  public RunReviewOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public RunReviewOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  @TargetApi(21)
  public RunReviewOverlay(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
  }

  private void init() {
    Resources res = getResources();

    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setStyle(Paint.Style.FILL);

    dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dotPaint.setStyle(Paint.Style.FILL);

    dotBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dotBackgroundPaint.setColor(res.getColor(R.color.chart_margins_color));
    dotBackgroundPaint.setStyle(Paint.Style.FILL);

    Typeface valueTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    Typeface timeTimeface = Typeface.create("sans-serif", Typeface.NORMAL);

    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setTypeface(valueTypeface);
    textPaint.setTextSize(res.getDimension(R.dimen.run_review_overlay_label_text_size));
    textPaint.setColor(res.getColor(R.color.text_color_white));

    timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    timePaint.setTypeface(timeTimeface);
    timePaint.setTextSize(res.getDimension(R.dimen.run_review_overlay_label_text_size));
    timePaint.setColor(res.getColor(R.color.text_color_white));

    centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    centerLinePaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.chart_grid_line_width));
    centerLinePaint.setStyle(Paint.Style.STROKE);
    centerLinePaint.setColor(res.getColor(R.color.text_color_white));

    linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    linePaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.recording_overlay_bar_width));
    int dashSize = res.getDimensionPixelSize(R.dimen.run_review_overlay_dash_size);
    linePaint.setPathEffect(new DashPathEffect(new float[] {dashSize, dashSize}, dashSize));
    linePaint.setColor(res.getColor(R.color.note_overlay_line_color));
    linePaint.setStyle(Paint.Style.STROKE);

    path = new Path();

    // TODO: Need to make sure this is at least as detailed as the SensorAppearance number
    // format!
    textFormat = res.getString(R.string.run_review_chart_label_format);
    timeFormat = ElapsedTimeAxisFormatter.getInstance(getContext());

    cropBackgroundPaint = new Paint();
    cropBackgroundPaint.setStyle(Paint.Style.FILL);
    cropBackgroundPaint.setColor(res.getColor(R.color.text_color_black));
    cropBackgroundPaint.setAlpha(40);

    cropVerticalLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    cropVerticalLinePaint.setStyle(Paint.Style.STROKE);
    cropVerticalLinePaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.chart_grid_line_width));
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    Resources res = getResources();
    height = getMeasuredHeight();
    width = getMeasuredWidth();
    paddingBottom = getPaddingBottom();
    chartPaddingTop = res.getDimensionPixelSize(R.dimen.run_review_section_margin);
    chartHeight = res.getDimensionPixelSize(R.dimen.run_review_chart_height);

    labelPadding = res.getDimensionPixelSize(R.dimen.run_review_overlay_label_padding);
    intraLabelPadding = res.getDimensionPixelSize(R.dimen.run_review_overlay_label_intra_padding);
    notchHeight = res.getDimensionPixelSize(R.dimen.run_review_overlay_label_notch_height);
    cornerRadius = res.getDimensionPixelSize(R.dimen.run_review_overlay_label_corner_radius);
    cropFlagBufferX = labelPadding;

    dotRadius = res.getDimensionPixelSize(R.dimen.run_review_value_label_dot_radius);
    dotBackgroundRadius =
        res.getDimensionPixelSize(R.dimen.run_review_value_label_dot_background_radius);

    chartMarginLeft =
        res.getDimensionPixelSize(R.dimen.chart_margin_size_left)
            + res.getDimensionPixelSize(R.dimen.stream_presenter_padding_sides);
    chartMarginRight = res.getDimensionPixelSize(R.dimen.stream_presenter_padding_sides);
  }

  public void onDraw(Canvas canvas) {
    if (isCropping) {
      boolean cropStartOnChart = isXScreenPointInChart(cropStartData.screenPoint);
      boolean cropEndOnChart = isXScreenPointInChart(cropEndData.screenPoint);

      // Draw grey overlays first, behind everything
      if (cropStartOnChart) {
        canvas.drawRect(
            chartMarginLeft,
            height - chartHeight - paddingBottom,
            cropStartData.screenPoint.x,
            height,
            cropBackgroundPaint);
        // We can handle crop seekbar visibility in onDraw because the Seekbar Group is
        // in charge of receiving touch events, and that is always visible during cropping.
        cropSeekbarGroup.getStartSeekBar().showThumb();
      } else {
        cropSeekbarGroup.getStartSeekBar().hideThumb();
      }
      if (cropEndOnChart) {
        canvas.drawRect(
            cropEndData.screenPoint.x,
            height - chartHeight - paddingBottom,
            width - chartMarginRight,
            height,
            cropBackgroundPaint);
        cropSeekbarGroup.getEndSeekBar().showThumb();
      } else {
        cropSeekbarGroup.getEndSeekBar().hideThumb();
      }

      // Draw the flags themselves
      if (cropStartOnChart) {
        // Drawing the start flag sets flagMeasurements to have the start flag's bounding
        // box. This will allow us to place the end flag appropriately.
        drawFlag(canvas, cropStartData, flagMeasurements, true);
      } else {
        // Clear flag measurements when the left hand flag is offscreen, so that
        // drawFlagAfter does not see another flag to avoid.
        // This means pushing the expected previous flag measurements, stored in
        // flagMeasurements, off screen by at least the amount of the flag buffer, which
        // allows the next flag to start drawing at 0.
        // In drawFlagAfter we will use flagMeasurements.boxEnd to determine what to
        // avoid.
        flagMeasurements.boxEnd = -cropFlagBufferX;
        cropStartData.labelRect.set(OFFSCREEN, OFFSCREEN, OFFSCREEN, OFFSCREEN);
      }
      if (cropEndOnChart) {
        drawFlagAfter(canvas, cropEndData, flagMeasurements, flagMeasurements.boxEnd, true);
      } else {
        cropEndData.labelRect.set(OFFSCREEN, OFFSCREEN, OFFSCREEN, OFFSCREEN);
      }

      // Neither flag can be drawn, but we might be in a region that can be cropped out
      // so the whole thing should be colored grey.
      if (!cropEndOnChart && !cropStartOnChart) {
        if (cropStartData.timestamp > externalAxis.xMax
            || cropEndData.timestamp < externalAxis.xMin) {
          canvas.drawRect(
              chartMarginLeft,
              height - chartHeight - paddingBottom,
              width - chartMarginRight,
              height,
              cropBackgroundPaint);
        }
      }
    } else {
      boolean xOnChart = isXScreenPointInChart(pointData.screenPoint);
      boolean yOnChart = isYScreenPointInChart(pointData.screenPoint);
      if (xOnChart && yOnChart) {
        // We are not cropping. Draw a standard flag.
        drawFlag(canvas, pointData, flagMeasurements, false);

        // Draw the vertical line from the point to the bottom of the flag
        float nudge = dotRadius / 2;
        float cy =
            height
                - chartHeight
                - paddingBottom
                + pointData.screenPoint.y
                - 2 * dotBackgroundRadius
                + nudge;
        path.reset();
        path.moveTo(pointData.screenPoint.x, flagMeasurements.notchBottom);
        path.lineTo(pointData.screenPoint.x, cy);
        canvas.drawPath(path, linePaint);

        // Draw the selected point
        float cySmall = cy + 1.5f * dotBackgroundRadius;
        canvas.drawCircle(
            pointData.screenPoint.x, cySmall, dotBackgroundRadius, dotBackgroundPaint);
        canvas.drawCircle(pointData.screenPoint.x, cySmall, dotRadius, dotPaint);
      } else {
        pointData.labelRect.set(OFFSCREEN, OFFSCREEN, OFFSCREEN, OFFSCREEN);
      }
    }
  }

  /**
   * Determines if a screen point is inside of the chart.
   *
   * @param screenPoint The point to test
   * @return true if the screen point is on the graph in the X axis
   */
  private boolean isXScreenPointInChart(PointF screenPoint) {
    if (screenPoint == null) {
      return false;
    }
    // Check if we can't draw the overlay balloon because the point is close to the edge of
    // the graph or offscreen.
    return screenPoint.x >= chartMarginLeft && screenPoint.x <= width - chartMarginRight;
  }

  /**
   * Determines if a screen point is inside of the chart.
   *
   * @param screenPoint The point to test
   * @return true if the screen point is on the graph in the Y axis
   */
  private boolean isYScreenPointInChart(PointF screenPoint) {
    if (screenPoint == null) {
      return false;
    }
    return screenPoint.y <= chartHeight && screenPoint.y >= chartPaddingTop;
  }

  /**
   * Draw a flag above a specific timestamp with a given value, but make sure the flag starts after
   * the given flagXToDrawAfter or that the flag is raised up to avoid intersecting it.
   *
   * @param canvas The canvas to use
   * @param pointData The point data to use
   * @param flagMeasurements This set of measurements will be updated in-place to hold the bounds of
   *     the flag.
   * @param flagXToDrawAfter The x position past which the flag may not draw. If the flag needs this
   *     space, it must draw itself higher.
   */
  private void drawFlagAfter(
      Canvas canvas,
      OverlayPointData pointData,
      FlagMeasurements flagMeasurements,
      float flagXToDrawAfter,
      boolean drawStem) {
    if (pointData.label == null) {
      pointData.label = "";
    }
    float labelWidth = textPaint.measureText(pointData.label);
    String timeLabel =
        timeFormat.format(pointData.timestamp - externalAxis.getRecordingStartTime(), true);
    float timeWidth = timePaint.measureText(timeLabel);

    // Ascent returns the distance above (negative) the baseline (ascent). Since it is negative,
    // negate it again to get the text height.
    float textSize = -1 * textPaint.ascent();

    flagMeasurements.boxTop = height - chartHeight - paddingBottom - textSize;
    flagMeasurements.boxBottom = flagMeasurements.boxTop + textSize + labelPadding * 2 + 5;
    float width = intraLabelPadding + 2 * labelPadding + timeWidth + labelWidth;
    // Ideal box layout
    flagMeasurements.boxStart = pointData.screenPoint.x - width / 2;
    flagMeasurements.boxEnd = pointData.screenPoint.x + width / 2;

    // Adjust it if the ideal doesn't work
    boolean isRaised = false;
    if (flagMeasurements.boxStart < flagXToDrawAfter + cropFlagBufferX) {
      // See if we can simply offset the flag, if it doesn't cause the notch to be drawn
      // off the edge of the flag.
      if (flagXToDrawAfter + cropFlagBufferX
          < pointData.screenPoint.x - notchHeight * SQRT_2_OVER_2 - cornerRadius) {
        flagMeasurements.boxStart = flagXToDrawAfter + cropFlagBufferX;
        flagMeasurements.boxEnd = flagMeasurements.boxStart + width;
      } else {
        // We need to move the flag up!
        moveUpToAvoid(flagMeasurements, textSize);
        isRaised = true;
      }
    }
    if (flagMeasurements.boxEnd > this.width) {
      flagMeasurements.boxEnd = this.width;
      flagMeasurements.boxStart = flagMeasurements.boxEnd - width;
      if (!isRaised && flagXToDrawAfter + cropFlagBufferX > flagMeasurements.boxStart) {
        // We need to move the flag up!
        moveUpToAvoid(flagMeasurements, textSize);
        isRaised = true;
      }
    }
    flagMeasurements.notchBottom = flagMeasurements.boxBottom + notchHeight;

    pointData.labelRect.set(
        flagMeasurements.boxStart,
        flagMeasurements.boxTop,
        flagMeasurements.boxEnd,
        flagMeasurements.boxBottom);
    canvas.drawRoundRect(pointData.labelRect, cornerRadius, cornerRadius, paint);

    path.reset();
    path.moveTo(
        (int) (pointData.screenPoint.x - notchHeight * SQRT_2_OVER_2), flagMeasurements.boxBottom);
    path.lineTo(pointData.screenPoint.x, flagMeasurements.boxBottom + notchHeight);
    path.lineTo(
        (int) (pointData.screenPoint.x + notchHeight * SQRT_2_OVER_2), flagMeasurements.boxBottom);
    canvas.drawPath(path, paint);

    float textBase = flagMeasurements.boxTop + labelPadding + textSize;
    canvas.drawText(timeLabel, flagMeasurements.boxStart + labelPadding, textBase, timePaint);
    canvas.drawText(
        pointData.label, flagMeasurements.boxEnd - labelWidth - labelPadding, textBase, textPaint);

    float center = flagMeasurements.boxStart + labelPadding + timeWidth + intraLabelPadding / 2;
    canvas.drawLine(
        center,
        flagMeasurements.boxTop + labelPadding,
        center,
        flagMeasurements.boxBottom - labelPadding,
        centerLinePaint);

    if (drawStem) {
      // Draws a vertical line to the flag notch from the base.
      // If there is a flag to draw after, does not overlap that flag.
      if (pointData.screenPoint.x < flagXToDrawAfter) {
        canvas.drawLine(
            pointData.screenPoint.x,
            height,
            pointData.screenPoint.x,
            this.flagMeasurements.boxBottom - 5 + textSize + 3 * labelPadding,
            cropVerticalLinePaint);
      } else {
        canvas.drawLine(
            pointData.screenPoint.x,
            height,
            pointData.screenPoint.x,
            this.flagMeasurements.notchBottom - 5,
            cropVerticalLinePaint);
      }
    }
  }

  private void moveUpToAvoid(FlagMeasurements flagMeasurements, float textSize) {
    // We need to move the flag up! Use 3 times padding to cover the two
    // paddings within the other flag and one more padding value above the other flag.
    flagMeasurements.boxBottom -= textSize + 3 * labelPadding;
    flagMeasurements.boxTop -= textSize + 3 * labelPadding;
  }

  /**
   * Draw a flag above a specific timestamp with a given value.
   *
   * @param canvas The canvas to use.
   * @param pointData The point data to use for the flag, including the timetstamp and X position.
   * @param flagMeasurements This set of measurements will be updated in-place to hold the bounds of
   *     the flag.
   */
  private void drawFlag(
      Canvas canvas,
      OverlayPointData pointData,
      FlagMeasurements flagMeasurements,
      boolean drawStem) {
    drawFlagAfter(canvas, pointData, flagMeasurements, -cropFlagBufferX, drawStem);
  }

  public void setChartController(ChartController controller) {
    chartController = controller;
    chartController.addChartDataLoadedCallback(this);
  }

  public void setGraphSeekBar(final GraphExploringSeekBar seekbar) {
    this.seekbar = seekbar;
    // Seekbar thumb is always blue, no matter the color of the grpah.
    int color = getResources().getColor(R.color.color_accent);
    this.seekbar.getThumb().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    this.seekbar.setVisibility(View.VISIBLE);

    thumb = this.seekbar.getThumb();
    this.seekbar.setOnSeekBarChangeListener(
        new GraphExploringSeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            ((GraphExploringSeekBar) seekBar).updateFullProgress(progress);
            refreshAfterChartLoad(fromUser);
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {
            // This is only called after the user starts moving, so we use an OnTouchListener
            // instead to get the requested UX behavior.
          }

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            if (onSeekbarTouchListener != null) {
              onSeekbarTouchListener.onTouchStop();
            }
            // If the user is as early on the seekbar as they can go, hide the overlay.
            ChartData.DataPoint point =
                chartController.getClosestDataPointToTimestamp(
                    getTimestampAtProgress(seekbar.getProgress()));
            if (point == null
                || !shouldShowSeekbars()
                || point.getX() <= chartController.getXMin()) {
              setVisibility(View.INVISIBLE);
            }
            invalidate();
          }
        });

    // Use an OnTouchListener to activate the views even if the user doesn't move.
    this.seekbar.setOnTouchListener(
        new OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            if (!shouldShowSeekbars()) {
              RunReviewOverlay.this.seekbar.setThumb(null);
              setVisibility(View.INVISIBLE);
              return false;
            }
            if (onSeekbarTouchListener != null) {
              onSeekbarTouchListener.onTouchStart();
            }
            setVisibility(View.VISIBLE);
            showThumb(); // Replace the thumb if it was missing after zoom/pan.
            invalidate();
            return false;
          }
        });
  }

  // Only show seekbars if there is data in the chart.
  private boolean shouldShowSeekbars() {
    return chartController.hasData();
  }

  public void setCropSeekBarGroup(CoordinatedSeekbarViewGroup cropGroup) {
    cropSeekbarGroup = cropGroup;
    GraphExploringSeekBar.OnSeekBarChangeListener listener =
        new GraphExploringSeekBar.OnSeekBarChangeListener() {

          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // Note that for crop seekbars, the full progress is updated in a change listener
            // on the crop bar, so we don't need to do that here.
            if (ignoreNextSeekbarProgressUpdate) {
              // Don't refresh if we are still waiting for another seekbar to update,
              // because this refresh would otherwise overwrite that update.
              ignoreNextSeekbarProgressUpdate = false;
            } else {
              refreshAfterChartLoad(fromUser);
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {
            if (shouldShowSeekbars()) {
              seekBar.setVisibility(View.VISIBLE);
            }
          }

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            // Unused
          }
        };
    cropSeekbarGroup.getStartSeekBar().addOnSeekBarChangeListener(listener);
    cropSeekbarGroup.getEndSeekBar().addOnSeekBarChangeListener(listener);
  }

  /**
   * Refreshes the selected timestamp and value based on the seekbar progress value. Can optionally
   * take the value found and calculate the progress the seekbar should have, and update the seekbar
   * again to get it more perfectly in sync. This is useful when data is sparce or zoomed in, to
   * keep the seekbar and drawn point vertically aligned. Note that updating the seekbar's progress
   * with backUpdateProgressBar true causes this function to be called again with
   * backUpdateProgressBar set to false.
   *
   * @param backUpdateProgressBar If true, updates the seekbar progress based on the found point.
   */
  public void refresh(boolean backUpdateProgressBar) {
    if (isCropping) {
      refreshFromCropSeekbar(cropSeekbarGroup.getStartSeekBar(), cropStartData);
      refreshFromCropSeekbar(cropSeekbarGroup.getEndSeekBar(), cropEndData);
      redrawCrop(backUpdateProgressBar);
    } else {
      refreshFromSeekbar(seekbar, pointData);
      redrawFromSeekbar(seekbar, pointData, backUpdateProgressBar);
    }
  }

  /** Refreshes the OverlayPointData for a Crop seekbar's progress. */
  private void refreshFromCropSeekbar(CropSeekBar seekbar, OverlayPointData pointData) {
    // Determine the timestamp at the current seekbar progress, using the other seekbar as
    // a buffer.
    ChartData.DataPoint point;
    long startTimestamp =
        getTimestampAtProgress(cropSeekbarGroup.getStartSeekBar().getFullProgress());
    long endTimestamp = getTimestampAtProgress(cropSeekbarGroup.getEndSeekBar().getFullProgress());
    if (seekbar.getType() == CropSeekBar.TYPE_START) {
      point =
          chartController.getClosestDataPointToTimestampBelow(
              startTimestamp, endTimestamp - CropHelper.MINIMUM_CROP_MILLIS);
    } else {
      point =
          chartController.getClosestDataPointToTimestampAbove(
              endTimestamp, startTimestamp + CropHelper.MINIMUM_CROP_MILLIS);
    }
    populatePointData(seekbar, pointData, point);
  }

  /** Refreshes the OverlayPointData for a particular Seekbar's progress. */
  private void refreshFromSeekbar(GraphExploringSeekBar seekbar, OverlayPointData pointData) {
    // Determine the timestamp at the current seekbar progress.
    int progress = seekbar.getFullProgress();
    ChartData.DataPoint point =
        chartController.getClosestDataPointToTimestamp(getTimestampAtProgress(progress));
    populatePointData(seekbar, pointData, point);
  }

  private void populatePointData(
      GraphExploringSeekBar seekbar, OverlayPointData pointData, ChartData.DataPoint point) {
    if (point == null) {
      // This happens when the user is dragging the thumb before the chart has loaded
      // data; there is no data loaded at all.
      // The bubble itself has been hidden in this case in RunReviewFragment, which hides
      // the RunReviewOverlay during line graph load and only shows it again once the
      // graph has been loaded successfully.
      return;
    }
    // Update the selected timestamp to one available in the chart data.
    pointData.timestamp = point.getX();
    pointData.value = point.getY();
    pointData.label = String.format(textFormat, pointData.value);
    seekbar.updateValuesForAccessibility(
        externalAxis.formatElapsedTimeForAccessibility(pointData.timestamp, getContext()),
        pointData.label);
  }

  /**
   * Redraws the RunReview overlay. See description of refresh() above for more on
   * backUpdateProgressBar.
   *
   * @param backUpdateProgressBar If true, updates the seekbar progress based on the found point.
   */
  private void redrawFromSeekbar(
      GraphExploringSeekBar seekbar, OverlayPointData pointData, boolean backUpdateProgressBar) {
    if (pointData.timestamp == NO_TIMESTAMP_SELECTED) {
      return;
    }
    if (backUpdateProgressBar) {
      long axisDuration = externalAxis.xMax - externalAxis.xMin;
      int newProgress =
          (int)
              Math.round(
                  (GraphExploringSeekBar.SEEKBAR_MAX * (pointData.timestamp - externalAxis.xMin))
                      / axisDuration);
      if (seekbar.getFullProgress() != newProgress) {
        seekbar.setFullProgress(newProgress);
      }
    }

    if (chartController.hasScreenPoints()) {
      pointData.screenPoint = chartController.getScreenPoint(pointData.timestamp, pointData.value);
    }
    if (timestampChangeListener != null) {
      timestampChangeListener.onTimestampChanged(pointData.timestamp);
    }
    postInvalidateOnAnimation();
  }

  /**
   * Redraws the RunReview overlay for the crop seekbars. See description of refresh() above for
   * more on backUpdateProgressBars.
   *
   * @param backUpdateProgressBars If true, updates the seekbars progress based on the found point
   */
  private void redrawCrop(boolean backUpdateProgressBars) {
    if (cropStartData.timestamp == NO_TIMESTAMP_SELECTED
        || cropEndData.timestamp == NO_TIMESTAMP_SELECTED) {
      return;
    }
    if (backUpdateProgressBars) {
      long axisDuration = externalAxis.xMax - externalAxis.xMin;
      int oldStartProgress = cropSeekbarGroup.getStartSeekBar().getFullProgress();
      int oldEndProgress = cropSeekbarGroup.getEndSeekBar().getFullProgress();
      int newStartProgress =
          (int)
              Math.round(
                  (GraphExploringSeekBar.SEEKBAR_MAX
                          * (cropStartData.timestamp - externalAxis.xMin))
                      / axisDuration);
      int newEndProgress =
          (int)
              Math.round(
                  (GraphExploringSeekBar.SEEKBAR_MAX * (cropEndData.timestamp - externalAxis.xMin))
                      / axisDuration);
      boolean startNeedsProgressUpdate = oldStartProgress != newStartProgress;
      boolean endNeedsProgressUpdate = oldEndProgress != newEndProgress;

      if (startNeedsProgressUpdate && endNeedsProgressUpdate) {
        ignoreNextSeekbarProgressUpdate = true;
        // Need to set these in an order that doesn't cause them to push each other.
        // So if the start increases, the end needs to increase first.
        // If the end decreases, the start needs to decrease first.
        // Otherwise they may shift when CropSeekBar trys to keep the buffer.
        if (oldStartProgress < newStartProgress) {
          cropSeekbarGroup.getEndSeekBar().setFullProgress(newEndProgress);
          cropSeekbarGroup.getStartSeekBar().setFullProgress(newStartProgress);
        } else {
          cropSeekbarGroup.getStartSeekBar().setFullProgress(newStartProgress);
          cropSeekbarGroup.getEndSeekBar().setFullProgress(newEndProgress);
        }

      } else if (startNeedsProgressUpdate) {
        cropSeekbarGroup.getStartSeekBar().setFullProgress(newStartProgress);
      } else if (endNeedsProgressUpdate) {
        cropSeekbarGroup.getEndSeekBar().setFullProgress(newEndProgress);
      }
    }
    if (chartController.hasScreenPoints()) {
      cropStartData.screenPoint =
          chartController.getScreenPoint(cropStartData.timestamp, cropStartData.value);
      cropEndData.screenPoint =
          chartController.getScreenPoint(cropEndData.timestamp, cropEndData.value);
    }
    invalidate();
  }

  private static int clipToSeekbarRange(double value) {
    return (int) (Math.min(Math.max(value, 0), GraphExploringSeekBar.SEEKBAR_MAX));
  }

  private long getTimestampAtProgress(int progress) {
    double percent = progress / GraphExploringSeekBar.SEEKBAR_MAX;
    long axisDuration = externalAxis.xMax - externalAxis.xMin;
    return (long) (percent * axisDuration + externalAxis.xMin);
  }

  /** For the graph exploring seekbar only (not crop) */
  public void setOnTimestampChangeListener(OnTimestampChangeListener listener) {
    timestampChangeListener = listener;
  }

  /** For the graph exploring seekbar only (not crop) */
  public void setOnSeekbarTouchListener(OnSeekbarTouchListener listener) {
    onSeekbarTouchListener = listener;
  }

  public void setOnLabelClickListener(OnLabelClickListener onLabelClickListener) {
    this.onLabelClickListener = onLabelClickListener;
    this.setOnTouchListener(
        new OnTouchListener() {
          private PointF downPoint = new PointF();

          @Override
          public boolean onTouch(View v, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
              if ((!isCropping && eventOnFlag(event, RunReviewOverlay.this.pointData))
                  || isCropping
                      && (eventOnFlag(event, cropStartData) || eventOnFlag(event, cropEndData))) {
                downPoint.set(event.getX(), event.getY());
                return true;
              }
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
              // See if the click is ending in any of the label boxes
              if (!isCropping
                  && eventOnFlag(event, RunReviewOverlay.this.pointData)
                  && RunReviewOverlay.this.pointData.labelRect.contains(downPoint.x, downPoint.y)) {
                RunReviewOverlay.this.onLabelClickListener.onValueLabelClicked();
                return true;
              } else if (isCropping
                  && eventOnFlag(event, cropStartData)
                  && cropStartData.labelRect.contains(downPoint.x, downPoint.y)) {
                RunReviewOverlay.this.onLabelClickListener.onCropStartLabelClicked();
                return true;
              } else if (isCropping
                  && eventOnFlag(event, cropEndData)
                  && cropEndData.labelRect.contains(downPoint.x, downPoint.y)) {
                RunReviewOverlay.this.onLabelClickListener.onCropEndLabelClicked();
                return true;
              }
            }
            return false;
          }

          private boolean eventOnFlag(MotionEvent event, OverlayPointData pointData) {
            return pointData.labelRect.contains(event.getX(), event.getY());
          }
        });
    // TODO: P3: Could add an ExploreByTouchHelper to make these discoverable for a11y users.
    // Since the same functionality is accessible from the menu options, this is not high
    // priority.
    // https://developer.android.com/reference/android/support/v4/widget/ExploreByTouchHelper.html
  }

  public void refreshAfterChartLoad(final boolean backUpdateProgressBar) {
    refreshAfterChartLoad(backUpdateProgressBar, 0);
  }

  public void refreshAfterChartLoad(final boolean backUpdateProgressBar, final int numAttempts) {
    if (!chartController.hasDrawnChart()) {
      // Refresh the Run Review Overlay after the line graph presenter's chart
      // has finished drawing itself.
      final ViewTreeObserver observer = chartController.getChartViewTreeObserver();
      if (observer == null) {
        return;
      }
      observer.removeOnDrawListener(onDrawListener);
      onDrawListener =
          new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
              RunReviewOverlay.this.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (!observer.isAlive()) {
                        if (numAttempts < MAX_REFRESH_ATTEMPTS) {
                          // Just try again, maybe it will come alive.
                          refreshAfterChartLoad(backUpdateProgressBar, numAttempts + 1);
                        }
                        return;
                      }
                      // The ViewTreeObserver calls its listeners without an iterator,
                      // so we need to remove the listener outside the flow or we risk
                      // an index-out-of-bounds crash in the case of multiple listeners.
                      observer.removeOnDrawListener(onDrawListener);
                      onDrawListener = null;
                      refresh(backUpdateProgressBar);
                    }
                  });
            }
          };
      observer.addOnDrawListener(onDrawListener);
    } else {
      refresh(backUpdateProgressBar);
    }
  }

  /**
   * When the Y axis changes, the data points may change position in Y. Need to "refresh" to
   * re-calculate the points associated with the timestamps, if the timestamps are within the
   * external axis range.
   */
  public void onYAxisAdjusted() {
    if (isCropping) {
      // TODO: Does this work if just one is offscreen on rotate?
      if (externalAxis.containsTimestamp(cropStartData.timestamp)
          || externalAxis.containsTimestamp(cropEndData.timestamp)) {
        refresh(false);
      }
    } else {
      if (externalAxis.containsTimestamp(pointData.timestamp)) {
        refresh(false);
      }
    }
  }

  public void onDestroy() {
    if (chartController != null) {
      chartController.removeChartDataLoadedCallback(this);
      if (onDrawListener != null) {
        final ViewTreeObserver observer = chartController.getChartViewTreeObserver();
        if (observer != null) {
          observer.removeOnDrawListener(onDrawListener);
        }
        onDrawListener = null;
      }
      onLabelClickListener = null;
      timestampChangeListener = null;
      onSeekbarTouchListener = null;
    }
  }

  /** Sets the slider to a particular timestamp. The user did not initiate this action. */
  public void setActiveTimestamp(long timestamp) {
    if (updateActiveTimestamp(timestamp)) {
      refreshAfterChartLoad(true);
    }
    ;
  }

  /**
   * Updates the active timestamp and the seekbar's progress based on the timestamp. Returns true if
   * the update means that a visual refresh is needed.
   *
   * @return true if the timestamp is within the range of the external axis and a refresh is needed.
   */
  private boolean updateActiveTimestamp(long timestamp) {
    if (chartIsLoading) {
      previouslySelectedTimestamp = timestamp;
      return false;
    }
    pointData.timestamp = timestamp;
    if (externalAxis.containsTimestamp(pointData.timestamp) && shouldShowSeekbars()) {
      double progress =
          (int)
              ((GraphExploringSeekBar.SEEKBAR_MAX * (timestamp - externalAxis.xMin))
                  / (externalAxis.xMax - externalAxis.xMin));
      setVisibility(View.VISIBLE);
      seekbar.setFullProgress((int) Math.round(progress));
      showThumb();
      // Only back-update the seekbar if the selected timestamp is in range.
      return true;
    } else {
      seekbar.setThumb(null);
      if (chartController.hasDrawnChart()) {
        redrawFromSeekbar(seekbar, pointData, true);
      }
      return false;
    }
  }

  public void setCropTimestamps(long startTimestamp, long endTimestamp) {
    if (updateCropTimestamps(startTimestamp, endTimestamp)) {
      refreshAfterChartLoad(true);
    }
  }

  /**
   * Updates the active timestamp and the seekbar's progress based on the timestamp, for the crop
   * seekbars. Returns true if the update means that a visual refresh is needed.
   *
   * @return true if at least one of the timestamps is within the range of the external axis and a
   *     refresh is needed.
   */
  private boolean updateCropTimestamps(long startTimestamp, long endTimestamp) {
    if (chartIsLoading) {
      previousCropStartTimestamp = startTimestamp;
      previousCropEndTimestamp = endTimestamp;
      return false;
    }
    cropStartData.timestamp = startTimestamp;
    cropEndData.timestamp = endTimestamp;
    boolean hasSeekbarInRange = false;
    boolean endSeekbarNeedsProgressUpdate = externalAxis.containsTimestamp(cropEndData.timestamp);
    if (externalAxis.containsTimestamp(cropStartData.timestamp)) {
      double progress =
          (int)
              ((GraphExploringSeekBar.SEEKBAR_MAX * (cropStartData.timestamp - externalAxis.xMin))
                  / (externalAxis.xMax - externalAxis.xMin));
      ignoreNextSeekbarProgressUpdate = endSeekbarNeedsProgressUpdate;
      cropSeekbarGroup.getStartSeekBar().setFullProgress((int) Math.round(progress));
      hasSeekbarInRange = true;
    }
    if (endSeekbarNeedsProgressUpdate) {
      double progress =
          (int)
              ((GraphExploringSeekBar.SEEKBAR_MAX * (cropEndData.timestamp - externalAxis.xMin))
                  / (externalAxis.xMax - externalAxis.xMin));
      if (hasSeekbarInRange
          && cropSeekbarGroup.getEndSeekBar().getFullProgress() != Math.round(progress)) {
        ignoreNextSeekbarProgressUpdate = true;
      }
      cropSeekbarGroup.getEndSeekBar().setFullProgress((int) Math.round(progress));
      hasSeekbarInRange = true;
    }
    if (hasSeekbarInRange && shouldShowSeekbars()) {
      setVisibility(View.VISIBLE);
      // Only back-update the seekbar if the selected timestamp is in range.
      return true;
    } else if (chartController.hasDrawnChart()) {
      redrawCrop(true);
    }
    return false;
  }

  /**
   * Set the timestamps for all three seekbars at once. This keeps us from doing extra redraw work
   * by only calling redraw once.
   */
  public void setAllTimestamps(long timestamp, long cropStartTimestamp, long cropEndTimestamp) {
    boolean activeTimestampUpdated = updateActiveTimestamp(timestamp);
    boolean cropTimestampsUpdated = updateCropTimestamps(cropStartTimestamp, cropEndTimestamp);
    if (activeTimestampUpdated || cropTimestampsUpdated) {
      refreshAfterChartLoad(true);
    }
  }

  public long getTimestamp() {
    return pointData.timestamp;
  }

  public long getCropStartTimestamp() {
    return cropStartData.timestamp;
  }

  public long getCropEndTimestamp() {
    return cropEndData.timestamp;
  }

  public boolean isValidCropTimestamp(long timestamp, boolean isStartCrop) {
    if (isStartCrop) {
      return timestamp < cropEndData.timestamp - CropHelper.MINIMUM_CROP_MILLIS;
    } else {
      return timestamp > cropStartData.timestamp + CropHelper.MINIMUM_CROP_MILLIS;
    }
  }

  public void setExternalAxisController(ExternalAxisController externalAxisController) {
    externalAxis = externalAxisController;
    externalAxis.addAxisUpdateListener(
        new ExternalAxisController.AxisUpdateListener() {

          @Override
          public void onAxisUpdated(long xMin, long xMax, boolean isPinnedToNow) {
            cropSeekbarGroup.setMillisecondsInRange(xMax - xMin);
            if (!chartController.hasDrawnChart()) {
              return;
            }
            if (isCropping
                && cropStartData.timestamp != NO_TIMESTAMP_SELECTED
                && cropEndData.timestamp != NO_TIMESTAMP_SELECTED) {
              redrawCrop(true);
            } else if (pointData.timestamp != NO_TIMESTAMP_SELECTED) {
              if (pointData.timestamp < xMin || pointData.timestamp > xMax) {
                seekbar.setThumb(null);
              } else {
                showThumb();
              }
              redrawFromSeekbar(seekbar, pointData, true);
            }
          }
        });
  }

  public void setUnits(String units) {
    if (seekbar != null) {
      seekbar.setUnits(units);
    }
  }

  public void updateColor(int newColor) {
    dotPaint.setColor(newColor);
    paint.setColor(newColor);
    cropVerticalLinePaint.setColor(newColor);
  }

  public void setCropModeOn(boolean isCropping) {
    this.isCropping = isCropping;
    seekbar.setVisibility(this.isCropping ? View.INVISIBLE : View.VISIBLE);
    if (isCropping) {
      cropSeekbarGroup.setVisibility(!shouldShowSeekbars() ? View.INVISIBLE : View.VISIBLE);
      setCropTimestamps(cropStartData.timestamp, cropEndData.timestamp);
    } else {
      cropSeekbarGroup.setVisibility(View.INVISIBLE);
    }
    refreshAfterChartLoad(true);
  }

  /**
   * Sets the crop timestamps to be at 10% and 90% of the current X axis, but no closer than the
   * minimum crop size.
   */
  public void resetCropTimestamps() {
    long newStartTimestamp = externalAxis.timestampAtAxisFraction(.1);
    long newEndTimestamp = externalAxis.timestampAtAxisFraction(.9);
    if (newEndTimestamp - newStartTimestamp < CropHelper.MINIMUM_CROP_MILLIS) {
      long diff = CropHelper.MINIMUM_CROP_MILLIS - (newEndTimestamp - newStartTimestamp);
      newStartTimestamp -= diff / 2 + 1;
      newEndTimestamp += diff / 2 + 1;
    }
    cropStartData.timestamp = newStartTimestamp;
    cropEndData.timestamp = newEndTimestamp;
  }

  public boolean getIsCropping() {
    return isCropping;
  }

  @Override
  public void onChartDataLoaded(long firstTimestamp, long lastTimestamp) {
    if (!shouldShowSeekbars()) {
      setVisibility(View.INVISIBLE);
      seekbar.setThumb(null);
      if (isCropping) {
        cropSeekbarGroup.setVisibility(View.INVISIBLE);
      }
    } else {
      setVisibility(View.VISIBLE);
      if (externalAxis.containsTimestamp(previouslySelectedTimestamp)) {
        showThumb();
      }
      if (isCropping) {
        cropSeekbarGroup.setVisibility(View.VISIBLE);
      }
    }
    if (chartIsLoading) {
      chartIsLoading = false;
      setAllTimestamps(
          previouslySelectedTimestamp, previousCropStartTimestamp, previousCropEndTimestamp);
    }
  }

  @Override
  public void onLoadAttemptStarted(boolean chartHiddenForLoad) {
    previouslySelectedTimestamp = pointData.timestamp;
    previousCropStartTimestamp = cropStartData.timestamp;
    previousCropEndTimestamp = cropEndData.timestamp;
    chartIsLoading = true;
  }

  private void showThumb() {
    if (seekbar.getThumb() == null) {
      seekbar.setThumb(thumb);
      thumb.setBounds(
          thumb.getBounds().left, 0, thumb.getBounds().right, thumb.getIntrinsicHeight());
    }
  }
}
