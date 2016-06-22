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
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * The overlay of a line graph, with visual indicators of min/max/average, recording start,
 * the recording period, and notes that can be synchronized with a line graph using updateAxis.
 */
public class LineGraphOverlay extends View {

    // The minimum timestamp currently being displayed on the X axis of the related line graph.
    private long mXMin;

    // The maximum timestamp currently being displayed on the X axis of the related line graph.
    private long mXMax;

    // The minimum y value in the range of the Y axis of the associated line graph.
    private double mYMin;

    // The maximum y value in the range of the Y axis of the associated line graph.
    private double mYMax;

    // A list of labels which should be visually indicated by this overlay.
    private List<Long> mLabels = new ArrayList();

    // A list of stream stats which could be visually displayed by this overlay if
    // recording and mShowStats is true.
    private List<StreamStat> mStats = new ArrayList<>();

    // The recording start time for this overlay.
    private long mRecordingStart = RecordingMetadata.NOT_RECORDING;

    // Whether the min/max/average stats should be displayed by this overlay.
    private boolean mShowStats = false;

    private float mDashSize;
    private float mLineWidth;
    private Paint mBackgroundPaint;
    private Paint mRecordingTimePaint;
    private Paint mLabelLinePaint;
    private Paint mStatMaxPaint;
    private Paint mStatMinPaint;
    private Paint mStatAvgPaint;
    private Drawable mMinDrawable;
    private Drawable mMaxDrawable;
    private Drawable mAvgDrawable;
    private int mStatDrawableWidth;

    public LineGraphOverlay(Context context) {
        super(context);
        init();
    }

    public LineGraphOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineGraphOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(21)
    public LineGraphOverlay(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        Resources res = getResources();
        mLineWidth = getResources().getDimension(R.dimen.recording_overlay_bar_width);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setColor(res.getColor(R.color.recording_axis_overlay_color));

        mRecordingTimePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRecordingTimePaint.setStyle(Paint.Style.STROKE);
        mRecordingTimePaint.setStrokeWidth(mLineWidth);
        mRecordingTimePaint.setColor(res.getColor(R.color.recording_axis_bar_color));

        mDashSize = res.getDimensionPixelSize(R.dimen.recording_overlay_dash_size);
        mLabelLinePaint = dashedLinePaint(R.color.note_overlay_line_color);
        mStatMaxPaint = dashedLinePaint(R.color.stats_max_color);
        mStatMinPaint = dashedLinePaint(R.color.stats_min_color);
        mStatAvgPaint = dashedLinePaint(R.color.stats_average_color);

        mMinDrawable = res.getDrawable(R.drawable.ic_data_min_color_12dpm);
        mMaxDrawable = res.getDrawable(R.drawable.ic_data_max_color_12dpm);
        mAvgDrawable = res.getDrawable(R.drawable.ic_data_average_color_12dp);
        mStatDrawableWidth = res.getDimensionPixelSize(R.dimen.small_stat_icon_size);
    }

    private Paint dashedLinePaint(int colorId) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(mLineWidth);
        paint.setPathEffect(new DashPathEffect(
                new float[]{mDashSize, mDashSize}, mDashSize));
        paint.setColor(getResources().getColor(colorId));
        return paint;
    }

    public void setLabels(List<? extends Label> labels) {
        mLabels.clear();
        for (Label label : labels) {
            mLabels.add(label.getTimeStamp());
        }
    }

    public void setRecordingStart(long recordingStart) {
        mRecordingStart = recordingStart;
        invalidate();
    }

    public void setShowStats(boolean showStats) {
        mShowStats = showStats;
    }

    public void updateStats(List<StreamStat> stats) {
        mStats = stats;
    }

    public void updateAxis(long xMin, long xMax, double yMin, double yMax) {
        mXMin = xMin;
        mXMax = xMax;
        mYMin = yMin;
        mYMax = yMax;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mRecordingStart == RecordingMetadata.NOT_RECORDING) {
            return;
        }
        float width = getMeasuredWidth();
        float height = getMeasuredHeight();
        float start = getOffsetForTimestamp(mRecordingStart, width);
        if (start < 0) {
            canvas.drawRect(0, 0, width, height, mBackgroundPaint);
        } else {
            canvas.drawRect(start, 0, width, height, mBackgroundPaint);
            canvas.drawLine(start, 0, start, height, mRecordingTimePaint);
        }
        for (long label : mLabels) {
            if (label <= mRecordingStart) {
                continue;
            }
            float offset = getOffsetForTimestamp(label, width);
            Path path = new Path();
            path.moveTo(offset, 0);
            path.lineTo(offset, height);
            canvas.drawPath(path, mLabelLinePaint);
        }
        if (mShowStats) {
            for (StreamStat stat : mStats) {
                float yOffset = height - getOffsetForValue(stat.getValue(), height);
                Path path = new Path();
                path.moveTo(mStatDrawableWidth, yOffset);
                path.lineTo(width, yOffset);
                canvas.drawPath(path, getStatPaint(stat.getType()));
                drawStatDrawable((int) yOffset, canvas, stat.getType());
            }
        }
    }

    private Paint getStatPaint(int type) {
        switch (type) {
            case StreamStat.TYPE_MIN:
                return mStatMinPaint;
            case StreamStat.TYPE_MAX:
                return mStatMaxPaint;
            case StreamStat.TYPE_AVERAGE:
                return mStatAvgPaint;
            default:
                return mLabelLinePaint;
        }
    }

    private void drawStatDrawable(int yOffset, Canvas canvas, int type) {
        Drawable toDraw;
        switch (type) {
            case StreamStat.TYPE_MIN:
                toDraw = mMinDrawable;
                toDraw.setBounds(0, yOffset - mStatDrawableWidth + (int) mLineWidth * 2,
                        mStatDrawableWidth, yOffset + (int) mLineWidth * 2);
                break;
            case StreamStat.TYPE_MAX:
                toDraw = mMaxDrawable;
                toDraw.setBounds(0, yOffset - (int) mLineWidth * 2, mStatDrawableWidth,
                        yOffset + mStatDrawableWidth - (int) mLineWidth * 2);
                break;
            case StreamStat.TYPE_AVERAGE:
                toDraw = mAvgDrawable;
                toDraw.setBounds(0, yOffset - mStatDrawableWidth / 2, mStatDrawableWidth,
                        yOffset + mStatDrawableWidth / 2);
                break;
            default:
                return;
        }
        toDraw.draw(canvas);
    }

    private float getOffsetForTimestamp(long timestamp, float width) {
        return (float) ((mXMin - timestamp) / (mXMin - mXMax * 1.0) * width);
    }

    private float getOffsetForValue(double value, float height) {
        return (float) ((mYMin - value) / (mYMin - mYMax) * height);
    }
}
