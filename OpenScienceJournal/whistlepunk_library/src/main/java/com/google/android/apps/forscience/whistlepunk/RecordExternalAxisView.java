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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

/** External axis view used for Recording. */
public class RecordExternalAxisView extends ExternalAxisView {
  private Paint recordingPaint;
  private float recordingPointRadius;

  private float recordOutlineSize;
  private Paint recordOutlinePaint;

  private Paint axisLabelPaint;
  private int axisLabelRadius;
  private Paint backgroundPaint;
  private float backgroundHeight;
  private float tickTopSpacing;
  private float recordingBarHeight;
  private CurrentTimeClock currentTimeClock;
  private int noteOutlineSize;
  private Paint noteOutlinePaint;

  public RecordExternalAxisView(Context context) {
    super(context);
  }

  public RecordExternalAxisView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public RecordExternalAxisView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public RecordExternalAxisView(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void init() {
    super.init();
    recordingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    recordingPaint.setColor(getResources().getColor(R.color.recording_axis_bar_color));
    recordingPaint.setStyle(Paint.Style.FILL);

    recordOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    recordOutlinePaint.setColor(getResources().getColor(R.color.text_color_white));
    recordOutlinePaint.setStyle(Paint.Style.FILL);

    noteOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    noteOutlinePaint.setColor(getResources().getColor(R.color.recording_axis_bar_color));
    noteOutlinePaint.setStyle(Paint.Style.FILL);

    axisLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    axisLabelPaint.setColor(getResources().getColor(R.color.graph_label_fill_color));
    axisLabelPaint.setStyle(Paint.Style.FILL);

    backgroundPaint = new Paint();
    backgroundPaint.setColor(getResources().getColor(R.color.observe_external_axis_color));
    backgroundPaint.setStyle(Paint.Style.FILL);

    recordingStart = RecordingMetadata.NOT_RECORDING;

    currentTimeClock = new CurrentTimeClock();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    Resources res = getResources();
    recordingPointRadius = res.getDimensionPixelSize(R.dimen.external_axis_recording_dot_size) / 2;
    recordOutlineSize = res.getDimensionPixelSize(R.dimen.external_axis_point_outline_width);
    noteOutlineSize = res.getDimensionPixelSize(R.dimen.external_axis_note_outline_width);
    axisLabelRadius = res.getDimensionPixelSize(R.dimen.external_axis_label_dot_size) / 2;
    backgroundHeight = res.getDimensionPixelSize(R.dimen.record_external_axis_background_height);
    tickTopSpacing =
        res.getDimensionPixelSize(R.dimen.record_external_axis_tick_padding_top)
            + height
            - backgroundHeight;
    recordingBarHeight = res.getDimensionPixelSize(R.dimen.record_external_axis_indicator_width);
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    canvas.drawRect(0, height - backgroundHeight, width, height, backgroundPaint);

    if (timeBetweenTicks == 0 || format == null) {
      return;
    }

    // Put the first tick just before xMin time so that it scrolls gracefully off the view.
    // If we stopped at xMin, the label would disappear suddenly as soon as the tick it
    // corresponded to was scrolled off the edge.
    long firstTickTime;
    boolean labelTick;

    if (recordingStart != RecordingMetadata.NOT_RECORDING) {
      // If recording, always label starting with '0' when recording began.
      // To calculate when to start drawing ticks, subtract or add mTimeBetweenTicks from the
      // recording time until we get to just below mXMin.
      long ticksFromStart = (recordingStart - xMin) / timeBetweenTicks;
      // Include one more to get below the minimum
      ticksFromStart++;
      firstTickTime = recordingStart - ticksFromStart * timeBetweenTicks;
      labelTick = ticksFromStart % 2 == 0;
    } else {
      firstTickTime = xMin - (xMin % timeBetweenTicks);
      labelTick = firstTickTime % (timeBetweenTicks * 2) == 0;
    }

    super.drawTicks(
        canvas,
        firstTickTime - timeBetweenTicks * 2,
        xMax + timeBetweenTicks * 2,
        labelTick,
        tickTopSpacing);

    float centerY = height - backgroundHeight - recordingBarHeight / 2;
    if (recordingStart != RecordingMetadata.NOT_RECORDING) {
      // Start wherever the recording start time is, or if it is off the screen, then
      // buffer by 3*recordOutlineSize to get the dot of (2*outlineSize)
      // to be fully off the screen.
      float startLocation =
          Math.max(-1 * recordOutlineSize * 3, getOffsetForTimestamp(recordingStart));
      float endLocation =
          Math.min(width, getOffsetForTimestamp(Math.max(currentTimeClock.getNow(), xMax)));
      canvas.drawRect(
          startLocation,
          height - backgroundHeight - recordingBarHeight,
          endLocation,
          height - backgroundHeight,
          recordingPaint);
      canvas.drawCircle(
          startLocation, centerY, recordingPointRadius + recordOutlineSize, recordOutlinePaint);
      canvas.drawCircle(startLocation, centerY, recordingPointRadius, recordingPaint);
    }

    if (labels != null) {
      for (Long timestamp : labels) {
        float location = getOffsetForTimestamp(timestamp);
        canvas.drawCircle(location, centerY, axisLabelRadius + noteOutlineSize, noteOutlinePaint);
        canvas.drawCircle(location, centerY, axisLabelRadius, axisLabelPaint);
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    // Don't allow touch events to pass to the views drawn under this axis.
    return true;
  }
}
