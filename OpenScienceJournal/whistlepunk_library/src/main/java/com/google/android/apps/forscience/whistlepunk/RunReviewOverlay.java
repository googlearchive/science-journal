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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;

import com.google.android.apps.forscience.whistlepunk.review.GraphExploringSeekBar;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;

import java.text.NumberFormat;

/**
 * Draws the value over a RunReview chart.
 */
public class RunReviewOverlay extends View implements ChartController.ChartDataLoadedCallback {
    private long mPreviouslySelectedTimestamp;

    public interface OnTimestampChangeListener {
        void onTimestampChanged(long timestamp);
    }
    private OnTimestampChangeListener mTimestampChangeListener;

    public interface OnSeekbarTouchListener {
        void onTouchStart();
        void onTouchStop();
    }
    private OnSeekbarTouchListener mOnSeekbarTouchListener;

    private static double DENOM = 2 * Math.sqrt(2);
    private static final double SEEKBAR_MAX = 300.0;

    private int mHeight;
    private int mWidth;
    private int mPaddingLeft;
    private int mChartHeight;
    private int mChartMarginLeft;

    private Paint mPaint;
    private Paint mDotBackgroundPaint;
    private Paint mTextPaintActive;
    private Paint mTextPaintInactive;
    private Paint mTimePaintActive;
    private Paint mTimePaintInactive;

    public static final long NO_TIMESTAMP_SELECTED = -1;
    private long mSelectedTimestamp = NO_TIMESTAMP_SELECTED;

    private double mValue;
    private PointF mScreenPoint;
    private ChartController mChartController;
    private int mInactiveCircleDiameter;
    private int mActiveCircleDiameter;
    private GraphExploringSeekBar mSeekbar;
    private ExternalAxisController mExternalAxis;
    private NumberFormat mYAxisNumberFormat;
    private ElapsedTimeAxisFormatter mTimeFormat;
    private float mActiveTextHeight;
    private float mInactiveTextHeight;
    private RectF mTopRect;
    private Path mPath;
    private boolean mIsActive = false;
    private float mDotRadius;
    private float mDotBackgroundRadius;
    private Drawable mThumb;
    private ViewTreeObserver.OnDrawListener mOnDrawListener;

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
    public RunReviewOverlay(Context context, AttributeSet attrs, int defStyleAttr,
                            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        Resources res = getResources();

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.FILL);

        Typeface typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        mTextPaintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaintActive.setColor(res.getColor(R.color.text_color_white));
        mTextPaintActive.setTextSize(res.getDimension(
                R.dimen.run_review_value_label_text_size_large));
        mTextPaintActive.setTypeface(typeface);

        mTextPaintInactive = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaintInactive.setColor(res.getColor(R.color.text_color_white));
        mTextPaintInactive.setTextSize(res.getDimension(
                R.dimen.run_review_value_label_text_size_small));
        mTextPaintInactive.setTypeface(typeface);

        mDotBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDotBackgroundPaint.setColor(res.getColor(R.color.chart_margins_color));
        mDotBackgroundPaint.setStyle(Paint.Style.FILL);

        mTimePaintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTimePaintActive.setTypeface(typeface);
        mTimePaintActive.setTextSize(
                res.getDimension(R.dimen.run_review_seekbar_label_text_size_large));

        mTimePaintInactive = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTimePaintInactive.setTypeface(typeface);
        mTimePaintInactive.setTextSize(
                res.getDimension(R.dimen.run_review_seekbar_label_text_size_small));

        mPath = new Path();
        mTopRect = new RectF();

        mYAxisNumberFormat = new AxisNumberFormat();
        mTimeFormat = ElapsedTimeAxisFormatter.getInstance(getContext());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Resources res = getResources();
        mHeight = getMeasuredHeight();
        mWidth = getMeasuredWidth();
        mPaddingLeft = getPaddingLeft();
        mChartHeight = res.getDimensionPixelSize(R.dimen.run_review_chart_height);
        mActiveCircleDiameter = res.getDimensionPixelSize(R.dimen.run_review_bubble_diameter);
        mInactiveCircleDiameter =
                res.getDimensionPixelSize(R.dimen.run_review_inactive_bubble_radius);

        mActiveTextHeight =
                res.getDimensionPixelSize(R.dimen.run_review_value_label_text_size_large);
        mInactiveTextHeight =
                res.getDimensionPixelSize(R.dimen.run_review_value_label_text_size_small);

        mDotRadius = res.getDimensionPixelSize(R.dimen.run_review_value_label_dot_radius);
        mDotBackgroundRadius =
                res.getDimensionPixelSize(R.dimen.run_review_value_label_dot_background_radius);

        mChartMarginLeft = res.getDimensionPixelSize(R.dimen.chart_margin_size_left) +
                res.getDimensionPixelSize(R.dimen.stream_presenter_padding_sides);
    }

    public void onDraw(Canvas canvas) {
        if (mScreenPoint == null) {
            return;
        }

        float circleDiameter = mIsActive ? mActiveCircleDiameter : mInactiveCircleDiameter;
        Paint textPaint = mIsActive ? mTextPaintActive : mTextPaintInactive;
        float textHeight = mIsActive ? mActiveTextHeight : mInactiveTextHeight;

        float diamondHeight = (float) (circleDiameter / DENOM);
        float nudge = mDotRadius / 2;
        float cx = mPaddingLeft + mScreenPoint.x - mChartMarginLeft - nudge;
        float cy = mHeight - mChartHeight + mScreenPoint.y - 2 * diamondHeight -
                2 * mDotBackgroundRadius + nudge;

        // TODO: To optimize, create the path just once and move it around with path.transform.
        mTopRect.set(cx - circleDiameter / 2, cy - circleDiameter / 2, cx + circleDiameter / 2,
                cy + circleDiameter / 2);
        mPath.reset();
        mPath.moveTo(cx, cy);
        mPath.lineTo(cx + diamondHeight, cy + diamondHeight);
        mPath.lineTo(cx, cy + 2 * diamondHeight);
        mPath.lineTo(cx - diamondHeight, cy + diamondHeight);
        mPath.lineTo(cx, cy);
        mPath.arcTo(mTopRect, 135, 270);
        mPath.lineTo(cx, cy);
        canvas.drawPath(mPath, mPaint);

        float cySmall = cy + 2 * diamondHeight + 1.5f * mDotBackgroundRadius;
        canvas.drawCircle(cx, cySmall, mDotBackgroundRadius, mDotBackgroundPaint);
        canvas.drawCircle(cx, cySmall, mDotRadius, mPaint);

        String label = mYAxisNumberFormat.format(mValue);
        float labelWidth = textPaint.measureText(label);
        canvas.drawText(label, cx - labelWidth / 2, cy + textHeight / 2, textPaint);

        String timeLabel = mTimeFormat.formatToTenths(mSelectedTimestamp -
                mExternalAxis.getRecordingStartTime());
        Paint timePaint = mIsActive ? mTimePaintActive : mTimePaintInactive;
        float textWidth = timePaint.measureText(timeLabel);
        // Since there is no spec for where to put this text, use the dot background radius as a
        // reasonable buffer between the slider point and the text.
        float buffer = mIsActive ? mDotBackgroundRadius * 2 : mDotBackgroundRadius;
        if (textWidth + cx + mDotBackgroundRadius > mWidth) {
            canvas.drawText(timeLabel, cx - textWidth - buffer, mHeight - buffer, timePaint);
        } else {
            canvas.drawText(timeLabel, cx + buffer, mHeight - buffer, timePaint);
        }

        mSeekbar.updateValuesForAccessibility(mExternalAxis.formatElapsedTimeForAccessibility(
                mSelectedTimestamp, getContext()), label);
    }

    public void setChartController(ChartController controller) {
        mChartController = controller;
        mChartController.addChartDataLoadedCallback(this);
    }

    public void setSeekbarView(final GraphExploringSeekBar seekbar) {
        mSeekbar = seekbar;
        mThumb = mSeekbar.getThumb();
        mSeekbar.setOnSeekBarChangeListener(new GraphExploringSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                refreshAfterChartLoad(fromUser);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // This is only called after the user starts moving, so we use an OnTouchListener
                // instead to get the requested UX behavior.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mOnSeekbarTouchListener != null) {
                    mOnSeekbarTouchListener.onTouchStop();
                }
                // If the user is as early on the seekbar as they can go, hide the overlay.
                ChartData.DataPoint point = getDataPointAtProgress(seekBar.getProgress());
                if (point == null || !mChartController.hasData() ||
                        point.getX() <= mChartController.getXMin()) {
                    setVisibility(View.INVISIBLE);
                }
                mIsActive = false;
                invalidate();
            }
        });

        // Use an OnTouchListener to activate the views even if the user doesn't move.
        mSeekbar.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mOnSeekbarTouchListener != null) {
                    mOnSeekbarTouchListener.onTouchStart();
                }
                mSeekbar.setThumb(mThumb); // Replace the thumb if it was missing after zoom/pan.
                setVisibility(View.VISIBLE);
                mIsActive = true;
                invalidate();
                return false;
            }
        });
    }

    public void refresh(boolean backUpdateProgressBar) {
        // No need to refresh the view if it is invisible.
        if (getVisibility() == View.INVISIBLE) {
            return;
        }
        // Determine the timestamp at the current seekbar progress.
        int progress = mSeekbar.getProgress();
        ChartData.DataPoint point = getDataPointAtProgress(progress);
        if (point == null) {
            // This happens when the user is dragging the thumb before the chart has loaded data;
            // there is no data loaded at all.
            // The bubble itself has been hidden in this case in RunReviewFragment, which hides
            // the RunReviewOverlay during line graph load and only shows it again once the graph
            // has been loaded successfully.
            return;
        }

        // Update the selected timestamp to one available in the chart data.
        mSelectedTimestamp = point.getX();
        mValue = point.getY();

        redraw(progress, backUpdateProgressBar);
    }

    private void redraw(int progress, boolean backUpdateProgressBar) {
        if (mSelectedTimestamp == NO_TIMESTAMP_SELECTED) {
            return;
        }
        if (backUpdateProgressBar) {
            long axisDuration = mExternalAxis.mXMax - mExternalAxis.mXMin;
            int newProgress = (int) Math.round((
                    SEEKBAR_MAX * (mSelectedTimestamp - mExternalAxis.mXMin)) / axisDuration);
            if (progress != newProgress) {
                mSeekbar.setProgress(newProgress);
            }
        }

        if (mChartController.hasScreenPoints()) {
            mScreenPoint = mChartController.getScreenPoint(mSelectedTimestamp, mValue);
        }
        if (mTimestampChangeListener != null) {
            mTimestampChangeListener.onTimestampChanged(mSelectedTimestamp);
        }
        invalidate();
    }

    private ChartData.DataPoint getDataPointAtProgress(int progress) {
        double percent = progress / SEEKBAR_MAX;
        long axisDuration = mExternalAxis.mXMax - mExternalAxis.mXMin;
        long timestamp = (long) (percent * axisDuration +
                mExternalAxis.mXMin);
        // Get the data point closest to this timestamp.
        return mChartController.getClosestDataPointToTimestamp(timestamp);
    }

    public void setOnTimestampChangeListener(OnTimestampChangeListener listener) {
        mTimestampChangeListener = listener;
    }

    public void setOnSeekbarTouchListener(OnSeekbarTouchListener listener) {
        mOnSeekbarTouchListener = listener;
    }

    public void refreshAfterChartLoad(final boolean backUpdateProgressBar) {
        if (!mChartController.hasDrawnChart()) {
            // Refresh the Run Review Overlay after the line graph presenter's chart
            // has finished drawing itself.
            final ViewTreeObserver observer = mChartController.getChartViewTreeObserver();
            if (observer == null) {
                return;
            }
            observer.removeOnDrawListener(mOnDrawListener);
            mOnDrawListener = new ViewTreeObserver.OnDrawListener() {
                @Override
                public void onDraw() {
                    observer.removeOnDrawListener(mOnDrawListener);
                    mOnDrawListener = null;
                    RunReviewOverlay.this.post(new Runnable() {
                        @Override
                        public void run() {
                            refresh(backUpdateProgressBar);
                        }
                    });

                }
            };
            observer.addOnDrawListener(mOnDrawListener);
        } else {
            refresh(backUpdateProgressBar);
        }
    }

    public void onDestroy() {
        if (mChartController != null) {
            mChartController.removeChartDataLoadedCallback(this);
            if (mOnDrawListener != null) {
                final ViewTreeObserver observer = mChartController.getChartViewTreeObserver();
                if (observer == null) {
                    return;
                }
                observer.removeOnDrawListener(mOnDrawListener);
            }
        }
    }

    // Sets the slider to a particular timestamp. The user did not initiate this action,
    // so mIsActive is false, meaning the bubble is drawn small.
    public void setActiveTimestamp(long timestamp) {
        mSelectedTimestamp = timestamp;
        if (mExternalAxis.mXMin < mSelectedTimestamp  &&
                mSelectedTimestamp < mExternalAxis.mXMax) {
            mSeekbar.setThumb(mThumb);
            double progress = (int) ((SEEKBAR_MAX * (timestamp - mExternalAxis.mXMin)) /
                    (mExternalAxis.mXMax - mExternalAxis.mXMin));
            setVisibility(View.VISIBLE);
            mIsActive = false;
            mSeekbar.setProgress((int) Math.round(progress));
            // Only back-update the seekbar if the selected timestmap is in range.
            refreshAfterChartLoad(true);
        } else {
            if (mChartController.hasDrawnChart()) {
                mSeekbar.setThumb(null);
                redraw(mSeekbar.getProgress(), true);
            }
        }
    }

    public long getTimestamp() {
        return mSelectedTimestamp;
    }

    public GraphExploringSeekBar getSeekbar() {
        return mSeekbar;
    }

    public void setExternalAxisController(ExternalAxisController externalAxisController) {
        mExternalAxis = externalAxisController;
        mExternalAxis.addAxisUpdateListener(new ExternalAxisController.AxisUpdateListener() {

            @Override
            public void onAxisUpdated(long xMin, long xMax, boolean isPinnedToNow) {
                if (!mChartController.hasDrawnChart()) {
                    return;
                }
                if (mSelectedTimestamp != NO_TIMESTAMP_SELECTED) {
                    if (mSelectedTimestamp < xMin || mSelectedTimestamp > xMax) {
                        mSeekbar.setThumb(null);
                    } else {
                        mSeekbar.setThumb(mThumb);
                    }
                    redraw(mSeekbar.getProgress(), true);
                }
            }
        });
    }

    public void setUnits(String units) {
        if (mSeekbar != null) {
            mSeekbar.setUnits(units);
        }
    }

    public void updateColor(int newColor) {
        mPaint.setColor(newColor);
        mTimePaintActive.setColor(newColor);
        mTimePaintInactive.setColor(newColor);
    }

    @Override
    public void onChartDataLoaded(long firstTimestamp, long lastTimestamp) {
        setActiveTimestamp(mPreviouslySelectedTimestamp);
    }

    @Override
    public void onLoadAttemptStarted() {
        mPreviouslySelectedTimestamp = mSelectedTimestamp;
    }
}
