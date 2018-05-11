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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;

/**
 * This class can be extended to show an external axis for a graph that can work with
 * ExternalAxisController.
 */
public abstract class ExternalAxisView extends View {

  private static final int NUMBER_OF_TICKS = 10;

  protected long recordingStart;
  protected List<Long> labels;

  protected long xMin;
  protected long xMax;

  private int mainColor;
  protected Paint paint;
  private Paint textPaint;

  protected NumberFormat format;
  private float textHeight;
  private float longTickHeight;
  private float shortTickHeight;
  protected float tickPaddingTop;
  private float tickPaddingBottom;
  private float tickWidth;
  protected float width;
  protected float height;
  protected float paddingTop;

  protected float distanceBetweenTicks;
  protected long timeBetweenTicks;
  protected float paddingLeft;
  protected float paddingRight;

  LruCache<String, Float> cachedLabelMeasurements = new LruCache<>(256);
  LruCache<Long, String> cachedFormattedLabels = new LruCache<>(256);

  public ExternalAxisView(Context context) {
    super(context);
    init();
  }

  public ExternalAxisView(Context context, AttributeSet attrs) {
    super(context, attrs);
    getAttributes(context, attrs);
    init();
  }

  public ExternalAxisView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    getAttributes(context, attrs);
    init();
  }

  @TargetApi(21)
  public ExternalAxisView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    getAttributes(context, attrs);
    init();
  }

  private void getAttributes(Context context, AttributeSet attrs) {
    TypedArray a =
        context.getTheme().obtainStyledAttributes(attrs, R.styleable.ExternalAxisView, 0, 0);

    try {
      mainColor =
          a.getColor(
              R.styleable.ExternalAxisView_mainColor,
              context.getResources().getColor(R.color.text_color_white));
    } finally {
      a.recycle();
    }
  }

  protected void init() {
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(mainColor);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(getResources().getDimension(R.dimen.external_axis_stroke_width));

    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setColor(mainColor);
    textPaint.setTextSize(getResources().getDimension(R.dimen.external_axis_text_size));
  }

  public void setNumberFormat(NumberFormat format) {
    this.format = format;
  }

  public void setRecordingStart(long timestamp) {
    recordingStart = timestamp;
    postInvalidateOnAnimation();
  }

  public void updateAxis(long xMin, long xMax) {
    this.xMin = xMin;
    this.xMax = xMax;
    timeBetweenTicks = (xMax - xMin) / NUMBER_OF_TICKS;
    postInvalidateOnAnimation();
  }

  public void setLabels(List<Long> labels) {
    Collections.sort(labels);
    this.labels = labels;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    Resources res = getResources();
    width = getMeasuredWidth();
    height = getMeasuredHeight();
    paddingLeft = getPaddingLeft();
    paddingRight = getPaddingRight();
    paddingTop = res.getDimensionPixelSize(R.dimen.external_axis_note_dot_size) / 2;
    textHeight = res.getDimensionPixelSize(R.dimen.external_axis_text_size);
    longTickHeight = res.getDimensionPixelSize(R.dimen.external_axis_long_tick_height);
    shortTickHeight = res.getDimensionPixelSize(R.dimen.external_axis_short_tick_height);
    tickPaddingTop = res.getDimensionPixelSize(R.dimen.external_axis_tick_padding_top);
    tickPaddingBottom = res.getDimensionPixelSize(R.dimen.external_axis_tick_padding_bottom);
    tickWidth = res.getDimensionPixelSize(R.dimen.external_axis_stroke_width);
    distanceBetweenTicks = widthWithoutPadding() / NUMBER_OF_TICKS;
  }

  private float widthWithoutPadding() {
    return width - paddingLeft - paddingRight;
  }

  protected void drawTicks(
      Canvas canvas,
      long firstTickTime,
      long lastTickTime,
      boolean labelTick,
      float tickPaddingTop) {
    // Calculate the offset along the X axis for the first tick.
    float xOffset = (float) (getOffsetForTimestamp(firstTickTime) - tickWidth / 2.0);

    // Draw an initial label before the first tick so it scrolls onto the screen nicely.
    if (!labelTick) {
      drawLabel(
          firstTickTime - timeBetweenTicks, xOffset - distanceBetweenTicks, tickPaddingTop, canvas);
    }

    for (long t = firstTickTime; t <= lastTickTime; t += timeBetweenTicks) {
      if (labelTick) {
        canvas.drawLine(xOffset, tickPaddingTop, xOffset, tickPaddingTop + longTickHeight, paint);
        drawLabel(t, xOffset, tickPaddingTop, canvas);
      } else {
        canvas.drawLine(xOffset, tickPaddingTop, xOffset, tickPaddingTop + shortTickHeight, paint);
      }
      // Label every other tick.
      labelTick = !labelTick;
      xOffset += distanceBetweenTicks;
    }

    // Draw a final label so it scrolls onto the screen nicely.
    if (labelTick) {
      drawLabel(lastTickTime + timeBetweenTicks, xOffset, tickPaddingTop, canvas);
    }
  }

  private void drawLabel(long t, float xOffset, float tickPaddingTop, Canvas canvas) {
    String label = format.format(t);
    float labelWidth = getLabelWidth(label);
    canvas.drawText(
        label,
        xOffset - labelWidth / 2,
        (tickPaddingTop + textHeight + tickPaddingBottom + longTickHeight),
        textPaint);
  }

  private String getLabelText(long timeStampMs) {
    Long cacheKey = timeStampMs / 100;
    String cached = cachedFormattedLabels.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    String computed = format.format(timeStampMs);
    cachedFormattedLabels.put(cacheKey, computed);
    return computed;
  }

  private float getLabelWidth(String label) {
    Float cached = cachedLabelMeasurements.get(label);
    if (cached != null) {
      return cached;
    }

    float computed = textPaint.measureText(label);
    cachedLabelMeasurements.put(label, computed);
    return computed;
  }

  // Returns how much should be added to X to draw this timestamp, given the current
  // minimum and maximum timestamps and width of the view.
  float getOffsetForTimestamp(long timestamp) {
    return (float) ((timestamp - xMin) / (xMax - xMin * 1.0) * widthWithoutPadding()) + paddingLeft;
  }

  long getTimestampForOffset(float offset) {
    return xMin - ((long) ((offset - paddingLeft) / widthWithoutPadding())) * (xMax - xMin);
  }

  // Convenience function for timestamp formatting, so other classes can use the same
  // format as the current ExternalAxisView.
  public String formatTimestamp(long timestamp) {
    if (format == null) {
      return "";
    }
    return format.format(timestamp);
  }
}

