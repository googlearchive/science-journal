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

    private static double SQRT_2_OVER_2 = Math.sqrt(2) / 2;
    private static final double SEEKBAR_MAX = 300.0;

    private int mHeight;
    private int mWidth;
    private int mPaddingLeft;
    private int mChartHeight;
    private int mChartMarginLeft;

    private int mLabelPadding;
    private int mIntraLabelPadding;
    private int mNotchHeight;
    private int mCornerRadius;

    private Paint mPaint;
    private Paint mDotPaint;
    private Paint mDotBackgroundPaint;
    private Paint mTextPaint;
    private Paint mTimePaint;
    private Paint mLinePaint;
    private Paint mCenterLinePaint;

    public static final long NO_TIMESTAMP_SELECTED = -1;
    private long mSelectedTimestamp = NO_TIMESTAMP_SELECTED;

    private double mValue;
    private PointF mScreenPoint;
    private ChartController mChartController;
    private GraphExploringSeekBar mSeekbar;
    private ExternalAxisController mExternalAxis;
    private String mTextFormat;
    private ElapsedTimeAxisFormatter mTimeFormat;
    private RectF mBoxRect;
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

        mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDotPaint.setStyle(Paint.Style.FILL);

        mDotBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDotBackgroundPaint.setColor(res.getColor(R.color.chart_margins_color));
        mDotBackgroundPaint.setStyle(Paint.Style.FILL);

        Typeface valueTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        Typeface timeTimeface = Typeface.create("sans-serif", Typeface.NORMAL);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTypeface(valueTypeface);
        mTextPaint.setTextSize(res.getDimension(R.dimen.run_review_overlay_label_text_size));
        mTextPaint.setColor(res.getColor(R.color.text_color_white));

        mTimePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTimePaint.setTypeface(timeTimeface);
        mTimePaint.setTextSize(res.getDimension(R.dimen.run_review_overlay_label_text_size));
        mTimePaint.setColor(res.getColor(R.color.text_color_white));

        mCenterLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterLinePaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.chart_grid_line_width));
        mCenterLinePaint.setStyle(Paint.Style.STROKE);
        mCenterLinePaint.setColor(res.getColor(R.color.text_color_white));

        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.recording_overlay_bar_width));
        int dashSize = res.getDimensionPixelSize(R.dimen.run_review_overlay_dash_size);
        mLinePaint.setPathEffect(new DashPathEffect(new float[]{dashSize, dashSize}, dashSize));
        mLinePaint.setColor(res.getColor(R.color.note_overlay_line_color));
        mLinePaint.setStyle(Paint.Style.STROKE);

        mPath = new Path();
        mBoxRect = new RectF();

        mTextFormat = res.getString(R.string.run_review_chart_label_format);
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

        mLabelPadding = res.getDimensionPixelSize(R.dimen.run_review_overlay_label_padding);
        mIntraLabelPadding = res.getDimensionPixelSize(
                R.dimen.run_review_overlay_label_intra_padding);
        mNotchHeight = res.getDimensionPixelSize(R.dimen.run_review_overlay_label_notch_height);
        mCornerRadius = res.getDimensionPixelSize(R.dimen.run_review_overlay_label_corner_radius);

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

        String label = String.format(mTextFormat, mValue);
        mSeekbar.updateValuesForAccessibility(mExternalAxis.formatElapsedTimeForAccessibility(
                mSelectedTimestamp, getContext()), label);

        float nudge = mDotRadius / 2;
        float cx = mPaddingLeft + mScreenPoint.x - mChartMarginLeft - nudge;

        if (cx < mPaddingLeft + mCornerRadius + mNotchHeight * SQRT_2_OVER_2 ||
                cx > mWidth - mCornerRadius - mNotchHeight * SQRT_2_OVER_2) {
            // Then we can't draw the overlay balloon because the point is close to the edge of the
            // screen or offscreen.
            return;
        }

        float cy = mHeight - mChartHeight + mScreenPoint.y - 2 * mDotBackgroundRadius + nudge;

        float labelWidth = mTextPaint.measureText(label);
        String timeLabel = mTimeFormat.formatToTenths(mSelectedTimestamp -
                mExternalAxis.getRecordingStartTime());
        float timeWidth = mTimePaint.measureText(timeLabel);
        float textSize = -1 * mTextPaint.ascent();

        float boxTop = mHeight - mChartHeight - textSize;
        float boxBottom = boxTop + textSize + mLabelPadding * 2 + 5;
        float width = mIntraLabelPadding + 2 * mLabelPadding + timeWidth + labelWidth;
        float boxStart = cx - width / 2;
        float boxEnd = cx + width / 2;
        if (boxStart < mPaddingLeft) {
            boxStart = mPaddingLeft;
            boxEnd = boxStart + width;
        } else if (boxEnd > mWidth) {
            boxEnd = mWidth;
            boxStart = boxEnd - width;
        }

        mPath.reset();
        mPath.moveTo(cx, boxTop);
        mPath.lineTo(cx, cy);
        canvas.drawPath(mPath, mLinePaint);

        mBoxRect.set(boxStart, boxTop, boxEnd, boxBottom);
        canvas.drawRoundRect(mBoxRect, mCornerRadius, mCornerRadius, mPaint);

        mPath.reset();
        mPath.moveTo((int) (cx - mNotchHeight * SQRT_2_OVER_2), boxBottom);
        mPath.lineTo(cx, boxBottom + mNotchHeight);
        mPath.lineTo((int) (cx + mNotchHeight * SQRT_2_OVER_2), boxBottom);
        canvas.drawPath(mPath, mPaint);

        float cySmall = cy + 1.5f * mDotBackgroundRadius;
        canvas.drawCircle(cx, cySmall, mDotBackgroundRadius, mDotBackgroundPaint);
        canvas.drawCircle(cx, cySmall, mDotRadius, mDotPaint);

        float textBase = boxTop + mLabelPadding + textSize;
        canvas.drawText(timeLabel, boxStart + mLabelPadding, textBase, mTimePaint);
        canvas.drawText(label, boxEnd - labelWidth - mLabelPadding, textBase, mTextPaint);

        float center = boxStart + mLabelPadding + timeWidth + mIntraLabelPadding / 2;
        canvas.drawLine(center, boxTop + mLabelPadding, center,
                boxBottom - mLabelPadding, mCenterLinePaint);
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
        mDotPaint.setColor(newColor);
        mPaint.setColor(newColor);
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
