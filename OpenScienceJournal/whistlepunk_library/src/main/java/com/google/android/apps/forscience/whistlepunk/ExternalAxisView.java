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
 * This class can be extended to show an external axis for a graph that
 * can work with ExternalAxisController.
 */
public abstract class ExternalAxisView extends View {

    private static final int NUMBER_OF_TICKS = 10;

    protected long mRecordingStart;
    protected List<Long> mLabels;

    protected long mXMin;
    protected long mXMax;

    private int mMainColor;
    protected Paint mPaint;
    private Paint mTextPaint;

    protected NumberFormat mFormat;
    private float mTextHeight;
    private float mLongTickHeight;
    private float mShortTickHeight;
    protected float mTickPaddingTop;
    private float mTickPaddingBottom;
    private float mTickWidth;
    protected float mWidth;
    protected float mHeight;
    protected float mPaddingTop;

    protected float mDistanceBetweenTicks;
    protected long mTimeBetweenTicks;
    protected float mPaddingLeft;
    protected float mPaddingRight;

    LruCache<String, Float> mCachedLabelMeasurements = new LruCache<>(256);
    LruCache<Long, String> mCachedFormattedLabels = new LruCache<>(256);

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
    public ExternalAxisView(Context context, AttributeSet attrs, int defStyleAttr,
                            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        getAttributes(context, attrs);
        init();
    }

    private void getAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ExternalAxisView,
                0, 0);

        try {
            mMainColor = a.getColor(R.styleable.ExternalAxisView_mainColor,
                    context.getResources().getColor(R.color.text_color_white));
        } finally {
            a.recycle();
        }
    }

    protected void init() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(mMainColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(getResources().getDimension(R.dimen.external_axis_stroke_width));

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(mMainColor);
        mTextPaint.setTextSize(getResources().getDimension(R.dimen.external_axis_text_size));
    }

    public void setNumberFormat(NumberFormat format) {
        mFormat = format;
    }

    public void setRecordingStart(long timestamp) {
        mRecordingStart = timestamp;
        postInvalidateOnAnimation();
    }

    public void updateAxis(long xMin, long xMax) {
        mXMin = xMin;
        mXMax = xMax;
        mTimeBetweenTicks = (xMax - xMin) / NUMBER_OF_TICKS;
        postInvalidateOnAnimation();
    }

    public void setLabels(List<Long> labels) {
        Collections.sort(labels);
        mLabels = labels;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Resources res = getResources();
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();
        mPaddingLeft = getPaddingLeft();
        mPaddingRight = getPaddingRight();
        mPaddingTop = res.getDimensionPixelSize(R.dimen.external_axis_note_dot_size) / 2;
        mTextHeight = res.getDimensionPixelSize(R.dimen.external_axis_text_size);
        mLongTickHeight = res.getDimensionPixelSize(R.dimen.external_axis_long_tick_height);
        mShortTickHeight = res.getDimensionPixelSize(R.dimen.external_axis_short_tick_height);
        mTickPaddingTop = res.getDimensionPixelSize(R.dimen.external_axis_tick_padding_top);
        mTickPaddingBottom = res.getDimensionPixelSize(R.dimen.external_axis_tick_padding_bottom);
        mTickWidth = res.getDimensionPixelSize(R.dimen.external_axis_stroke_width);
        mDistanceBetweenTicks = widthWithoutPadding() / NUMBER_OF_TICKS;
    }

    private float widthWithoutPadding() {
        return mWidth - mPaddingLeft - mPaddingRight;
    }

    protected void drawTicks(Canvas canvas, long firstTickTime, long lastTickTime,
            boolean labelTick, float tickPaddingTop) {
        // Calculate the offset along the X axis for the first tick.
        float xOffset = (float) (getOffsetForTimestamp(firstTickTime) - mTickWidth / 2.0);

        // Draw an initial label before the first tick so it scrolls onto the screen nicely.
        if (!labelTick) {
            drawLabel(firstTickTime - mTimeBetweenTicks, xOffset - mDistanceBetweenTicks,
                    tickPaddingTop, canvas);
        }

        for (long t = firstTickTime; t <= lastTickTime; t += mTimeBetweenTicks) {
            if (labelTick) {
                canvas.drawLine(xOffset, tickPaddingTop, xOffset, tickPaddingTop + mLongTickHeight,
                        mPaint);
                drawLabel(t, xOffset, tickPaddingTop, canvas);
            } else {
                canvas.drawLine(xOffset, tickPaddingTop, xOffset, tickPaddingTop + mShortTickHeight,
                        mPaint);
            }
            // Label every other tick.
            labelTick = !labelTick;
            xOffset += mDistanceBetweenTicks;
        }

        // Draw a final label so it scrolls onto the screen nicely.
        if (labelTick) {
            drawLabel(lastTickTime + mTimeBetweenTicks, xOffset, tickPaddingTop, canvas);
        }
    }

    private void drawLabel(long t, float xOffset, float tickPaddingTop, Canvas canvas) {
        String label = mFormat.format(t);
        float labelWidth = getLabelWidth(label);
        canvas.drawText(label, xOffset - labelWidth/2, (tickPaddingTop + mTextHeight +
                mTickPaddingBottom + mLongTickHeight), mTextPaint);
    }

    private String getLabelText(long timeStampMs) {
        Long cacheKey = timeStampMs/100;
        String cached = mCachedFormattedLabels.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String computed = mFormat.format(timeStampMs);
        mCachedFormattedLabels.put(cacheKey, computed);
        return computed;
    }

    private float getLabelWidth(String label) {
        Float cached = mCachedLabelMeasurements.get(label);
        if (cached != null) {
            return cached;
        }

        float computed = mTextPaint.measureText(label);
        mCachedLabelMeasurements.put(label, computed);
        return computed;
    }

    // Returns how much should be added to X to draw this timestamp, given the current
    // minimum and maximum timestamps and width of the view.
    float getOffsetForTimestamp(long timestamp) {
        return (float) ((timestamp - mXMin) / (mXMax - mXMin * 1.0) * widthWithoutPadding()) +
                mPaddingLeft;
    }

    long getTimestampForOffset(float offset) {
        return mXMin - (long) ((offset - mPaddingLeft) / widthWithoutPadding()) * (mXMax - mXMin);
    }

    // Convenience function for timestamp formatting, so other classes can use the same
    // format as the current ExternalAxisView.
    public String formatTimestamp(long timestamp) {
        if (mFormat == null) {
            return "";
        }
        return mFormat.format(timestamp);
    }

}
