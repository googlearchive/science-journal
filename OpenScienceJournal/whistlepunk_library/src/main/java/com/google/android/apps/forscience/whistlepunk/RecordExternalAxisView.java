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

/**
 * External axis view used for Recording.
 */
public class RecordExternalAxisView extends ExternalAxisView {
    private Paint mRecordingPaint;
    private float mRecordingPointRadius;

    private float mRecordOutlineSize;
    private Paint mRecordOutlinePaint;

    private Paint mAxisLabelPaint;
    private int mAxisLabelRadius;
    private Paint mBackgroundPaint;
    private float mBackgroundHeight;
    private float mTickTopSpacing;
    private float mRecordingBarHeight;
    private CurrentTimeClock mCurrentTimeClock;
    private int mNoteOutlineSize;
    private Paint mNoteOutlinePaint;

    public RecordExternalAxisView(Context context) {
        super(context);
    }

    public RecordExternalAxisView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecordExternalAxisView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RecordExternalAxisView(Context context, AttributeSet attrs, int defStyleAttr,
                                  int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void init() {
        super.init();
        mRecordingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRecordingPaint.setColor(getResources().getColor(R.color.recording_axis_bar_color));
        mRecordingPaint.setStyle(Paint.Style.FILL);

        mRecordOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRecordOutlinePaint.setColor(getResources().getColor(R.color.text_color_white));
        mRecordOutlinePaint.setStyle(Paint.Style.FILL);

        mNoteOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNoteOutlinePaint.setColor(getResources().getColor(R.color.recording_axis_bar_color));
        mNoteOutlinePaint.setStyle(Paint.Style.FILL);

        mAxisLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAxisLabelPaint.setColor(getResources().getColor(R.color.graph_label_fill_color));
        mAxisLabelPaint.setStyle(Paint.Style.FILL);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(getResources().getColor(R.color.observe_external_axis_color));
        mBackgroundPaint.setStyle(Paint.Style.FILL);

        mRecordingStart = RecordingMetadata.NOT_RECORDING;

        mCurrentTimeClock = new CurrentTimeClock();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Resources res = getResources();
        mRecordingPointRadius =
                res.getDimensionPixelSize(R.dimen.external_axis_recording_dot_size) / 2;
        mRecordOutlineSize = res.getDimensionPixelSize(R.dimen.external_axis_point_outline_width);
        mNoteOutlineSize = res.getDimensionPixelSize(R.dimen.external_axis_note_outline_width);
        mAxisLabelRadius = res.getDimensionPixelSize(R.dimen.external_axis_label_dot_size) / 2;
        mBackgroundHeight = res.getDimensionPixelSize(
                R.dimen.record_external_axis_background_height);
        mTickTopSpacing = res.getDimensionPixelSize(R.dimen.record_external_axis_tick_padding_top) +
                mHeight - mBackgroundHeight;
        mRecordingBarHeight = res.getDimensionPixelSize(
                R.dimen.record_external_axis_indicator_width);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, mHeight - mBackgroundHeight, mWidth, mHeight, mBackgroundPaint);

        if (mTimeBetweenTicks == 0 || mFormat == null) {
            return;
        }

        // Put the first tick just before xMin time so that it scrolls gracefully off the view.
        // If we stopped at xMin, the label would disappear suddenly as soon as the tick it
        // corresponded to was scrolled off the edge.
        long firstTickTime;
        boolean labelTick;

        if (mRecordingStart != RecordingMetadata.NOT_RECORDING) {
            // If recording, always label starting with '0' when recording began.
            // To calculate when to start drawing ticks, subtract or add mTimeBetweenTicks from the
            // recording time until we get to just below mXMin.
            long ticksFromStart = (mRecordingStart - mXMin) / mTimeBetweenTicks;
            // Include one more to get below the minimum
            ticksFromStart++;
            firstTickTime = mRecordingStart - ticksFromStart * mTimeBetweenTicks;
            labelTick = ticksFromStart % 2 == 0;
        }  else {
            firstTickTime = mXMin - (mXMin % mTimeBetweenTicks);
            labelTick = firstTickTime % (mTimeBetweenTicks * 2) == 0;
        }

        super.drawTicks(canvas, firstTickTime - mTimeBetweenTicks * 2,
                mXMax + mTimeBetweenTicks * 2, labelTick, mTickTopSpacing);

        float centerY = mHeight - mBackgroundHeight - mRecordingBarHeight / 2;
        if (mRecordingStart != RecordingMetadata.NOT_RECORDING) {
            // Start wherever the recording start time is, or if it is off the screen, then
            // buffer by 3*mRecordOutlineSize to get the dot of (2*outlineSize)
            // to be fully off the screen.
            float startLocation = Math.max(-1 * mRecordOutlineSize * 3,
                    getOffsetForTimestamp(mRecordingStart));
            float endLocation = Math.min(mWidth,
                    getOffsetForTimestamp(Math.max(mCurrentTimeClock.getNow(), mXMax)));
            canvas.drawRect(startLocation, mHeight - mBackgroundHeight - mRecordingBarHeight,
                    endLocation, mHeight - mBackgroundHeight, mRecordingPaint);
            canvas.drawCircle(startLocation, centerY, mRecordingPointRadius + mRecordOutlineSize,
                    mRecordOutlinePaint);
            canvas.drawCircle(startLocation, centerY, mRecordingPointRadius, mRecordingPaint);
        }

        if (mLabels != null) {
            for (Long timestamp : mLabels) {
                float location = getOffsetForTimestamp(timestamp);
                canvas.drawCircle(location, centerY, mAxisLabelRadius + mNoteOutlineSize,
                        mNoteOutlinePaint);
                canvas.drawCircle(location, centerY, mAxisLabelRadius, mAxisLabelPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Don't allow touch events to pass to the views drawn under this axis.
        return true;
    }
}
