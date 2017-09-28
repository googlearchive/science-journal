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
import android.support.annotation.VisibleForTesting;
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

    private List<ExternalAxisController.InteractionListener> mListeners = new ArrayList<>();

    private Paint mBackgroundPaint;

    private Paint mPathPaint;
    private Path mPath;
    private boolean mHasPath;
    private Matrix mMatrix = new Matrix();

    private Paint mAxisPaint;
    private Paint mAxisTextPaint;
    private float mAxisTextHeight;
    private float mTopPadding;
    private float mBottomPadding;
    private float mRightPadding;
    private float mAxisTextStartPadding;

    private List<Double> mYAxisPoints = new ArrayList<>();
    // Number formatting takes a lot of allocations, so save formatted labels in a list.
    private ArrayList<String> mYAxisPointLabels = new ArrayList<>();

    private Paint mLeadingEdgePaint;
    private float mLeadingEdgeRadius;
    private boolean mLeadingEdgeIsDrawn = false;

    private Paint mEndpointPaint;
    private float mEndpointInnerRadius;
    private float mEndpointOuterRadius;

    private Paint mLabelFillPaint;
    private Paint mLabelOutlinePaint;
    private float mLabelRadius;
    private float mLabelOutlineRadius;
    private Paint mLabelLinePaint;

    private ChartOptions mChartOptions;
    private ChartData mChartData;

    private float mWidth = 1;
    private float mHeight = 1;
    private float mChartHeight;
    private float mChartWidth;
    private RectF mChartRect; // Save this to avoid reallocations.
    private RectF mPreviousChartRect = new RectF();

    // These describe the minimum and maximum values which the path covers, in the coordinates
    // of the chart data. If the path is not being transformed, they should be the same as the
    // the ChartOptions.getRenderedXMin, XMax, YMin and YMax. If a change in rendered values has
    // occured, these values are used to figure out the transformation needed on the path.
    private long mXMinForPathCalcs;
    private long mXMaxForPathCalcs;
    private double mYMinForPathCalcs;
    private double mYMaxForPathCalcs;

    // These track how much data is covered in the path, and are only updated when the path is
    // redrawn.
    private long mXMinInPath;
    private long mXMaxInPath;

    // Whether we were previously pinned to now. This is used to decide whether to add a new data
    // point to the end of the chart, or pull in a lot of new data if there may be a gap.
    private boolean mWasPinnedToNow;

    private boolean mIsDrawn = false;

    // For drawing the recording overlay
    private Paint mRecordingBackgroundPaint;
    private Paint mRecordingTimePaint;

    // For drawing stats
    private float mStatLineWidth;
    private Paint mStatMinMaxPaint;
    private Paint mStatAvgPaint;
    private Drawable mMinDrawable;
    private Drawable mMaxDrawable;
    private Drawable mAvgDrawable;
    private int mStatDrawableWidth;
    private Path mStatsPath;
    private float mStartPadding;
    private Drawable mTriggerDrawable;
    private int mBackgroundColor;
    private boolean mExploreByTouchEnabled;

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
        mPath = new Path();
        mStatsPath = new Path();
    }

    private void createPaints() {
        mPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mAxisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAxisPaint.setStyle(Paint.Style.STROKE);
        mAxisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAxisTextPaint.setTextAlign(Paint.Align.RIGHT);
        mLeadingEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLeadingEdgePaint.setStyle(Paint.Style.FILL);
        mEndpointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEndpointPaint.setStyle(Paint.Style.FILL);
        mLabelFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLabelOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackgroundPaint = new Paint();

        mRecordingBackgroundPaint = new Paint();
        mRecordingBackgroundPaint.setStyle(Paint.Style.FILL);
        mRecordingTimePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRecordingTimePaint.setStyle(Paint.Style.STROKE);

        mLabelLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStatMinMaxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStatAvgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        float prevHeight = mHeight;
        float prevWidth = mWidth;
        mHeight = getMeasuredHeight();
        mWidth = getMeasuredWidth();
        mTopPadding = getPaddingTop();
        mBottomPadding = getPaddingBottom();
        mRightPadding = getResources().getDimensionPixelSize(
                R.dimen.stream_presenter_padding_sides);

        if (mChartOptions == null || mChartData == null) {
            return;
        }
        // If the height has changed, need to redraw the whole path!
        if (prevHeight != mHeight || prevWidth != mWidth) {
            initialize(mChartOptions, mChartData);
        }
    }

    private void measure() {
        Resources res = getResources();
        mPathPaint.setPathEffect(new CornerPathEffect(
                res.getDimensionPixelSize(mChartOptions.getCornerPathRadiusId())));
        mPathPaint.setStrokeWidth(res.getDimensionPixelSize(mChartOptions.getLineWidthId()));
        mAxisPaint.setStrokeWidth(res.getDimensionPixelSize(
                mChartOptions.getAxisLabelsLineWidthId()));
        mAxisTextHeight = res.getDimensionPixelSize(mChartOptions.getAxisLabelsTextSizeId());
        mAxisTextPaint.setTextSize(mAxisTextHeight);
        float chartStartPadding = res.getDimensionPixelSize(mChartOptions.getChartStartPaddingId());
        mAxisTextStartPadding = res.getDimensionPixelSize(
                mChartOptions.getAxisLabelsStartPaddingId());

        mLeadingEdgeRadius = res.getDimensionPixelSize(mChartOptions.getLeadingEdgeRadiusId());
        mEndpointInnerRadius = res.getDimensionPixelSize(mChartOptions.getEndpointInnerRadiusId());
        mEndpointOuterRadius = res.getDimensionPixelSize(mChartOptions.getEndpointOutderRadiusId());

        mLabelRadius = res.getDimensionPixelSize(mChartOptions.getLabelInnerRadiusId());
        mLabelOutlineRadius = res.getDimensionPixelSize(mChartOptions.getLabelOutlineRadiusId());

        mMinDrawable = res.getDrawable(R.drawable.ic_data_min_color_12dpm);
        mMaxDrawable = res.getDrawable(R.drawable.ic_data_max_color_12dpm);
        mAvgDrawable = res.getDrawable(R.drawable.ic_data_average_color_12dp);
        mStatDrawableWidth = res.getDimensionPixelSize(R.dimen.small_stat_icon_size);

        mStatLineWidth = getResources().getDimensionPixelSize(R.dimen.recording_overlay_bar_width);
        mRecordingBackgroundPaint.setColor(res.getColor(R.color.recording_axis_overlay_color));
        mRecordingTimePaint.setStrokeWidth(getResources().getDimensionPixelSize(
                R.dimen.recording_overlay_time_line_width));
        mRecordingTimePaint.setColor(res.getColor(R.color.recording_axis_bar_color));

        float dashSize = res.getDimensionPixelSize(R.dimen.recording_overlay_dash_size);
        makeDashedLinePaint(mLabelLinePaint, R.color.note_overlay_line_color, mStatLineWidth,
                dashSize);
        makeDashedLinePaint(mStatMinMaxPaint, mStatLineWidth, dashSize);
        makeDashedLinePaint(mStatAvgPaint, R.color.stats_average_color, mStatLineWidth, dashSize);

        // These calculations are done really frequently, so store in member variables to reduce
        // cycles.
        mStartPadding = chartStartPadding + getPaddingLeft();
        mChartHeight = mHeight - mBottomPadding - mTopPadding;
        mChartWidth = mWidth - mStartPadding - mRightPadding;

        mChartRect = new RectF(mStartPadding, mTopPadding, mChartWidth + mStartPadding,
                mChartHeight + mTopPadding);
    }

    private void makeDashedLinePaint(Paint paint, int colorId, float lineWidth, float dashSize) {
        makeDashedLinePaint(paint, lineWidth, dashSize);
        paint.setColor(getResources().getColor(colorId));
    }

    private void makeDashedLinePaint(Paint paint, float lineWidth, float dashSize) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(lineWidth);
        paint.setPathEffect(new DashPathEffect(new float[]{dashSize, dashSize}, dashSize));
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            // The user could have changed the accessibility mode while we were in the background,
            // so just get it when window focus changes.
            mExploreByTouchEnabled = ((AccessibilityManager) getContext().getSystemService(
                    Context.ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled();
        }
    }

    public void initialize(ChartOptions chartOptions, ChartData chartData) {
        mChartOptions = chartOptions;
        mChartData = chartData;
        measure();
        if (mWidth <= 1 || mHeight <= 1) {
            return;
        }
        updateColorOptions();
        populatePath(false);

        final boolean isObserve = mChartOptions.getChartPlacementType() ==
                ChartOptions.ChartPlacementType.TYPE_OBSERVE;

        if (mChartOptions.canPan() || mChartOptions.canZoom()) {
            ViewConfiguration vc = ViewConfiguration.get(getContext());
            final float touchSlop = vc.getScaledTouchSlop();
            setOnTouchListener(new OnTouchListener() {
                private final long DOUBLE_TAP_MAX_TIME = ViewConfiguration.getDoubleTapTimeout();
                private final long DOUBLE_TAP_MIN_TIME = 40;

                private final float ZOOM_SLOP = touchSlop * 4;

                private float mTouchX;
                private float mTouchY;
                private int mDownIndex;
                private float mXZoomSpan;
                private float mYZoomSpan;
                private long mPreviousDownTime = 0;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    int type = event.getActionMasked();
                    int numPointers = event.getPointerCount();
                    if (type == MotionEvent.ACTION_DOWN) {
                        mDownIndex = 0;
                        updateTouchPoints(event, mDownIndex);
                        long tapDelta = event.getDownTime() - mPreviousDownTime;
                        if (!isObserve && DOUBLE_TAP_MIN_TIME < tapDelta &&
                                tapDelta < DOUBLE_TAP_MAX_TIME) {
                            resetZoom();
                        } else {
                            for (ExternalAxisController.InteractionListener listener : mListeners) {
                                listener.onStartInteracting();
                            }
                        }
                        mPreviousDownTime = event.getDownTime();
                        return true;
                    } else if (type == MotionEvent.ACTION_POINTER_DOWN) {
                        mDownIndex = event.getActionIndex() == 1 ? 0 : 1;
                        updateTouchPoints(event, mDownIndex);
                        mXZoomSpan = Math.abs(event.getX(0) - event.getX(1));
                        mYZoomSpan = Math.abs(event.getY(0) - event.getY(1));
                        return true;
                    } else if (type == MotionEvent.ACTION_MOVE) {
                        float oldTouchX = mTouchX;
                        float oldTouchY = mTouchY;
                        updateTouchPoints(event, mDownIndex);

                        boolean hasPannedX = false;
                        boolean hasPannedY = false;
                        if (mChartOptions.canPanX()) {
                            long dPanX = getXValueDeltaFromXScreenDelta(oldTouchX - mTouchX);
                            mChartOptions.setRenderedXRange(mChartOptions.getRenderedXMin() + dPanX,
                                    mChartOptions.getRenderedXMax() + dPanX);
                            hasPannedX = true;
                        }
                        if (mChartOptions.canPanY()) {
                            double dPanY = getYValueDeltaFromYScreenDelta(mTouchY - oldTouchY);
                            doYPan(dPanY);
                            hasPannedY = true;
                        }

                        boolean hasZoomedX = false;
                        boolean hasZoomedY = false;
                        if (numPointers > 1) {
                            float xZoomSpan = Math.abs(event.getX(0) - event.getX(1));
                            float yZoomSpan = Math.abs(event.getY(0) - event.getY(1));
                            if (mChartOptions.canZoomX() && xZoomSpan > touchSlop) {
                                float scale = mXZoomSpan / xZoomSpan;
                                long newDiff = (long) ((mChartOptions.getRenderedXMax() -
                                        mChartOptions.getRenderedXMin()) * scale);
                                long avg = (mChartOptions.getRenderedXMax() +
                                        mChartOptions.getRenderedXMin()) / 2;

                                mChartOptions.setRenderedXRange(avg - newDiff / 2,
                                        avg + newDiff / 2);
                                mXZoomSpan = xZoomSpan;
                                hasZoomedX = true;
                            } else {
                                mXZoomSpan = xZoomSpan;
                            }
                            // Zooming in Y can happen accidentally so make sure the user isn't just
                            // being sloppy but really means to zoom in Y.
                            if (mChartOptions.canZoomY() && yZoomSpan > ZOOM_SLOP) {
                                // Limit the amount of scale we can do in a cycle.
                                float scale = Math.max(0.5f, Math.min(2, mYZoomSpan / yZoomSpan));
                                double newDiff = Math.min(mChartOptions.getMaxRenderedYRange(),
                                        mChartOptions.getRenderedYMax() -
                                                mChartOptions.getRenderedYMin()) * scale;
                                double avg = (mChartOptions.getRenderedYMax() +
                                        mChartOptions.getRenderedYMin()) / 2;
                                double newMin = isObserve ? avg - newDiff / 2 :
                                        Math.max(avg - newDiff / 2, mChartOptions.getYMinLimit());
                                double newMax = isObserve ? avg + newDiff / 2 :
                                        Math.min(avg + newDiff / 2, mChartOptions.getYMaxLimit());
                                mChartOptions.setRenderedYRange(newMin, newMax);
                                hasZoomedY = true;
                                mYZoomSpan = yZoomSpan;
                            } else {
                                mYZoomSpan = yZoomSpan;
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
                            for (ExternalAxisController.InteractionListener listener : mListeners) {
                                // No need to zoom and pan, this double-calls listeners.
                                // Try zooming first, then panning.
                                if (hasZoomedX) {
                                    listener.onZoom(mChartOptions.getRenderedXMin(),
                                            mChartOptions.getRenderedXMax());
                                } else if (hasPannedX) {
                                    listener.onPan(mChartOptions.getRenderedXMin(),
                                            mChartOptions.getRenderedXMax());
                                }
                            }
                        }

                        // If we made a chart movement here, don't let the parent steal the event.
                        boolean eventUsed = (hasPannedX && significantPan(mTouchX - oldTouchX)) ||
                                hasPannedY || hasZoomedX || hasZoomedY;
                        if (eventUsed) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return eventUsed;
                    } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                        int upIndex = event.getActionIndex();
                        // Whichever finger is still down gets to keep panning, so we need to update
                        // mTouchX and mTouchY.
                        int stillDownIndex = upIndex == 1 ? 0 : 1;
                        updateTouchPoints(event, stillDownIndex);
                        mDownIndex = 0;
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP ||
                            event.getAction() == MotionEvent.ACTION_POINTER_UP ||
                            event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        for (ExternalAxisController.InteractionListener listener : mListeners) {
                            listener.onStopInteracting();
                        }
                        if (event.getAction() == MotionEvent.ACTION_UP && mExploreByTouchEnabled) {
                            // A single quick tap when explore by touch is enabled is actually a
                            // double-tap from the user, so we should zoom out.
                            // Single tapping doesn't do anything anyway so this is the correct
                            // behavior.
                            long tapDelta = event.getEventTime() - mPreviousDownTime;
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
                    double prevMin = mChartOptions.getRenderedYMin();
                    double prevMax = mChartOptions.getRenderedYMax();

                    double limitMin = mChartOptions.getYMinLimit();
                    double limitMax = mChartOptions.getYMaxLimit();

                    if (prevMin + dPanY < limitMin) {
                        dPanY = limitMin - prevMin;
                    }
                    if (prevMax + dPanY > limitMax) {
                        dPanY = prevMax - limitMax;
                    }
                    mChartOptions.setRenderedYRange(prevMin + dPanY, prevMax + dPanY);
                }

                private boolean significantPan(float panDistance) {
                    return Math.abs(panDistance) >= touchSlop;
                }

                private void updateTouchPoints(MotionEvent event, int panPointerIndex) {
                    mTouchX = event.getX(panPointerIndex);
                    mTouchY = event.getY(panPointerIndex);
                }

                private void resetZoom() {
                    // Assume this is a double-tap, reset zoom if we aren't in observe.
                    mChartOptions.requestResetZoomInY();
                    for (ExternalAxisController.InteractionListener listener : mListeners) {
                        listener.onStartInteracting();
                        listener.requestResetZoom();
                    }
                }
            });
        }
    }

    public void updateColorOptions() {
        int chartColor = mChartOptions.getLineColor();
        mPathPaint.setColor(chartColor);
        Resources res = getResources();
        mAxisPaint.setColor(res.getColor(mChartOptions.getAxisLabelsLineColorId()));
        mAxisTextPaint.setColor(res.getColor(mChartOptions.getLabelsTextColorId()));
        mLeadingEdgePaint.setColor(chartColor);
        mEndpointPaint.setColor(chartColor);
        mLabelFillPaint.setColor(res.getColor(mChartOptions.getLabelFillColorId()));
        mLabelOutlinePaint.setColor(mChartOptions.getLabelOutlineColor(res));
        mBackgroundColor = res.getColor(mChartOptions.getChartBackgroundColorId());
        mBackgroundPaint.setColor(mBackgroundColor);

        mStatMinMaxPaint.setColor(chartColor);
        mMinDrawable = mMinDrawable.mutate();
        mMinDrawable.setColorFilter(chartColor, PorterDuff.Mode.SRC_ATOP);
        mMaxDrawable = mMaxDrawable.mutate();
        mMaxDrawable.setColorFilter(chartColor, PorterDuff.Mode.SRC_ATOP);
    }

    private double getYValueDeltaFromYScreenDelta(float screenDiff) {
        double valueDiff = mChartOptions.getRenderedYMax() - mChartOptions.getRenderedYMin();
        return screenDiff / mChartHeight * valueDiff;
    }

    private long getXValueDeltaFromXScreenDelta(float screenDiff) {
        long timeDiff = mChartOptions.getRenderedXMax() - mChartOptions.getRenderedXMin();
        return (long) (screenDiff / mChartWidth * timeDiff);
    }

    public void addInteractionListener(ExternalAxisController.InteractionListener listener) {
        mListeners.add(listener);
    }

    public void clearInteractionListeners() {
        mListeners.clear();
    }

    public void clear() {
        mIsDrawn = false;
        redraw();
    }

    public void redraw() {
        mYAxisPoints.clear();
        mYAxisPointLabels.clear();
        populatePath(false);
        postInvalidateOnAnimation();
    }

    /**
     * Gets the screen x coordinate for a data point x value.
     * @param x The data point timestamp
     * @return The screen x coordinate
     */
    public float getScreenX(long x) {
        long xMax = mChartOptions.getRenderedXMax();
        long xMin = mChartOptions.getRenderedXMin();
        float result = (1.0f * (x - xMin)) / (xMax - xMin) * mChartWidth + mStartPadding;
        return result;
    }

    /**
     * Gets the screen y coordinate for a data point y value.
     * @param y The data point value
     * @return The screen y coordinate
     */
    public float getScreenY(double y) {
        double yMax = mChartOptions.getRenderedYMax();
        double yMin = mChartOptions.getRenderedYMin();
        double result = mChartHeight * (1 - ((y - yMin) / (yMax - yMin))) + mTopPadding;
        return (float) result;
    }

    /**
     * Gets the long x value for a screen x coordinate. This is useful when figuring out what
     * point a user is touching, for example.
     * @param x The screen x
     * @return The long value that corresponds to the x value of the point on the chart.
     */
    public long getXFromScreenX(float x) {
        return mChartOptions.getRenderedXMin() + getXValueDeltaFromXScreenDelta(x - mStartPadding);
    }

    /**
     * Gets the long y value for a screen y coordinate. This is useful when figuring out what
     * point a user is touching, for example.
     * @param y The screen y
     * @return The double value that corresponds to the y value of the point on the chart.
     */
    public double getYFromScreenY(float y) {
        return mChartOptions.getRenderedYMin() + getYValueDeltaFromYScreenDelta(
                mHeight - mBottomPadding - y);
    }

    /**
     * Populates the path from the chart data, from scratch.
     */
    private void populatePath(boolean optimizePinnedToEnd) {
        int numPoints = mChartData.getNumPoints();
        mPath.reset();

        if (numPoints == 0) {
            return;
        }

        // Just get the points in the range that we want to render, instead of all the points.
        // Adds some buffer to the load in case of scrolling, if those data points are available.
        updatePathCalcs();
        List<ChartData.DataPoint> points;
        if (optimizePinnedToEnd) {
            // This is a slightly more efficient call, so use it when possible.
            points = mChartData.getPointsInRangeToEnd(mChartOptions.getRenderedXMin() - BUFFER_MS);
        } else {
            points = mChartData.getPointsInRange(mChartOptions.getRenderedXMin() - BUFFER_MS,
                    mChartOptions.getRenderedXMax() + BUFFER_MS);
        }
        int numPlottedPoints = points.size();
        if (numPlottedPoints == 0) {
            return;
        }
        mPath.moveTo(getPathX(points.get(0).getX()), getPathY(points.get(0).getY()));
        for (int i = 1; i < numPlottedPoints; i++) {
            mPath.lineTo(getPathX(points.get(i).getX()), getPathY(points.get(i).getY()));
        }
        mHasPath = true;

        // Only update these when the path is redrawn. They track how much data the path covers.
        mXMinInPath = points.get(0).getX();
        mXMaxInPath = points.get(numPlottedPoints - 1).getX();
    }

    /**
     * Efficiently adds data points to a chart view by adding them to the existing path and then
     * transforming the path based on updated renderer values.
     * This reduces the need to recalculate all the points in the path every time a new point is
     * added.
     * @param point The data point to add to the end of the path.
     */
    public void addPointToEndOfPath(ChartData.DataPoint point) {
        int numPoints = mChartData.getNumPoints();
        if (!mHasPath || numPoints < MAXIMUM_NUM_POINTS_FOR_POPULATE_PATH ||
                (numPoints % DRAWN_POINTS_REDRAW_THRESHOLD == 0 && mChartOptions.isPinnedToNow())) {
            populatePath(true);
            postInvalidateOnAnimation();
        } else {
            if (mChartOptions.isPinnedToNow() && !mWasPinnedToNow) {
                populatePath(true);
                postInvalidateOnAnimation();
            } else if ((mChartOptions.isPinnedToNow()) ||
                    mChartOptions.getRenderedXMax() >= point.getX() || mLeadingEdgeIsDrawn) {
                // Add the point to the end only if the end is being rendered.
                // The path is in the previous coordinates, so we can add a point using those
                // mins/maxes.
                mPath.lineTo(getPathX(point.getX()), getPathY(point.getY()));
                mXMaxInPath = point.getX();
            }
        }
        mWasPinnedToNow = mChartOptions.isPinnedToNow();
    }

    /**
     * Transform the path by stretching and translating it to meet the new rendered size.
     */
    public void transformPath() {
        // The path needs to be scaled in X and Y based on the range of the new data points.
        mMatrix.reset();
        mPreviousChartRect.set(getScreenX(mXMinForPathCalcs), getScreenY(mYMaxForPathCalcs),
                getScreenX(mXMaxForPathCalcs), getScreenY(mYMinForPathCalcs));
        mMatrix.setRectToRect(mChartRect, mPreviousChartRect, Matrix.ScaleToFit.FILL);
        mPath.transform(mMatrix);

        updatePathCalcs();
        postInvalidateOnAnimation();
    }

    private void updatePathCalcs() {
        mXMaxForPathCalcs = mChartOptions.getRenderedXMax();
        mXMinForPathCalcs = mChartOptions.getRenderedXMin();
        mYMinForPathCalcs = mChartOptions.getRenderedYMin();
        mYMaxForPathCalcs = mChartOptions.getRenderedYMax();
    }

    // Gets the X coordinate of a point in the current path coordinates, which may be different
    // from the rendered min/max coordinates if the path has not yet been transformed and drawn.
    // This should just be used when drawing the path.
    private float getPathX(long x) {
        return (1.0f * (x - mXMinForPathCalcs)) / (mXMaxForPathCalcs - mXMinForPathCalcs) *
                mChartWidth + mStartPadding;
    }

    // Gets the Y coordinate of a point in the current path coordinates, which may be different
    // from the rendered min/max coordinates if the path has not yet been transformed and drawn.
    // This should just be used when drawing the path.
    private float getPathY(double y) {
        return (float) (mChartHeight * (1 - ((y - mYMinForPathCalcs) /
                (mYMaxForPathCalcs - mYMinForPathCalcs))) + mTopPadding);
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawColor(mBackgroundColor);

        if (mChartData == null || mChartData.getNumPoints() == 0) {
            return;
        }

        if (mChartOptions.showYGrid()) {
            updateYAxisPoints();
        }

        // Draw the Y label lines under the path.
        drawYAxis(canvas);
        canvas.drawPath(mPath, mPathPaint);
        // Try drawing the endpoints, if they are needed.
        tryDrawingEndpoints(canvas);

        // Draw the labels.
        drawLabels(canvas);

        // Draw the stats and recording overlay if needed.
        if (mChartOptions.shouldDrawRecordingOverlay()) {
            drawRecordingOverlay(canvas);
        }

        // Draw the stats if needed
        if (mChartOptions.shouldShowStatsOverlay()) {
            drawStats(canvas);
        }

        // White out the margins
        canvas.drawRect(0, 0, mStartPadding, mHeight, mBackgroundPaint);
        canvas.drawRect(0, 0, mWidth, mTopPadding, mBackgroundPaint);
        canvas.drawRect(0, mHeight - mBottomPadding, mWidth, mHeight, mBackgroundPaint);
        canvas.drawRect(mWidth - mRightPadding, 0, mWidth, mHeight,
                mBackgroundPaint);

        drawTriggers(canvas);

        if (mChartOptions.showYGrid()) {
            // Draw the Y label text above the rect that whites out the Y label axis.
            drawYAxisText(canvas);
        }

        mIsDrawn = true;
    }

    public boolean isDrawn() {
        return mIsDrawn;
    }

    private void drawTriggers(Canvas canvas) {
        List<Double> triggerValues = mChartOptions.getTriggerValues();
        if (triggerValues == null || triggerValues.size() == 0) {
            return;
        }
        Drawable drawable = getTriggerDrawable();
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        for (double value : triggerValues) {
            float y = getScreenY(value);
            if (y + height < 0 || y  - height > mHeight) {
                continue;
            }
            drawable.setBounds((int) (mStartPadding - width - mAxisTextStartPadding),
                    (int) y - height / 2, (int) (mStartPadding - mAxisTextStartPadding),
                    (int) y + height / 2);
            drawable.draw(canvas);
        }
    }

    private Drawable getTriggerDrawable() {
        // Lazy init, as most charts probably won't need triggers.
        if (mTriggerDrawable == null) {
            Resources res = getResources();
            mTriggerDrawable = res.getDrawable(R.drawable.ic_label_black_24dp);
            mTriggerDrawable.mutate().setColorFilter(res.getColor(R.color.color_accent),
                    PorterDuff.Mode.SRC_IN);
            mTriggerDrawable.setAlpha(res.getInteger(R.integer.trigger_drawable_alpha));
        }
        return mTriggerDrawable;
    }

    private void drawLabels(Canvas canvas) {
        List<ChartData.DataPoint> labels = mChartData.getLabelPoints();
        for (ChartData.DataPoint label : labels) {
            if (label.getX() < mXMinInPath || label.getX() > mXMaxInPath) {
                continue;
            }
            float x = getScreenX(label.getX());
            float y = getScreenY(label.getY());
            if (mChartOptions.shouldDrawRecordingOverlay()) {
                mStatsPath.reset();
                mStatsPath.moveTo(x, 0);
                mStatsPath.lineTo(x, mHeight);
                canvas.drawPath(mStatsPath, mLabelLinePaint);
            }
            canvas.drawCircle(x, y, mLabelOutlineRadius, mLabelOutlinePaint);
            canvas.drawCircle(x, y, mLabelRadius, mLabelFillPaint);
        }
    }

    private void tryDrawingEndpoints(Canvas canvas) {
        if (mChartOptions.isShowLeadingEdge()) {
            ChartData.DataPoint point = mChartData.getPoints().get(mChartData.getNumPoints() - 1);
            if (point.getX() == mXMaxInPath && mXMaxInPath <= mXMaxForPathCalcs) {
                mLeadingEdgeIsDrawn = true;
                canvas.drawCircle(getScreenX(point.getX()), getScreenY(point.getY()),
                        mLeadingEdgeRadius, mLeadingEdgePaint);
            } else {
                mLeadingEdgeIsDrawn = false;
            }
        } else if (mChartOptions.isShowEndpoints()) {
            // Only try to draw the endpoints if the range shown contains the recording
            // start and/or end times.
            if (mChartOptions.getRenderedXMin() < mChartOptions.getRecordingStartTime() &&
                    mChartOptions.getRecordingStartTime() < mChartOptions.getRenderedXMax()) {
                ChartData.DataPoint start = mChartData.getPoints().get(0);
                if (start.getX() >= mXMinForPathCalcs) {
                    float screenX = getScreenX(start.getX());
                    float screenY = getScreenY(start.getY());
                    canvas.drawCircle(screenX, screenY, mEndpointOuterRadius, mEndpointPaint);
                    canvas.drawCircle(screenX, screenY, mEndpointInnerRadius, mBackgroundPaint);
                }
            }
            if (mChartOptions.getRenderedXMin() < mChartOptions.getRecordingEndTime() &&
                    mChartOptions.getRecordingEndTime() < mChartOptions.getRenderedXMax()) {
                ChartData.DataPoint end = mChartData.getPoints().get(mChartData.getNumPoints() - 1);
                if (end.getX() <= mXMaxForPathCalcs) {
                    float screenX = getScreenX(end.getX());
                    float screenY = getScreenY(end.getY());
                    canvas.drawCircle(screenX, screenY, mEndpointOuterRadius, mEndpointPaint);
                    canvas.drawCircle(screenX, screenY, mEndpointInnerRadius, mBackgroundPaint);
                }
            }
        }
    }

    private void drawYAxis(Canvas canvas) {
        // Now go through the points to label and draw the horizontal label line.
        for (int i = 0; i < mYAxisPoints.size(); i++) {
            float yValue = getScreenY(mYAxisPoints.get(i));
            canvas.drawLine(mStartPadding, yValue, mWidth, yValue, mAxisPaint);
        }
    }

    private void drawYAxisText(Canvas canvas) {
        // Now go through the points to label and draw the label text
        for (int i = 0; i < mYAxisPoints.size(); i++) {
            double pointValue = mYAxisPoints.get(i);
            float yValue = getScreenY(pointValue);
            if (textWillBeCropped(yValue, mAxisTextHeight)) {
                continue;
            }
            canvas.drawText(mYAxisPointLabels.get(i), mStartPadding - mAxisTextStartPadding,
                    yValue + mAxisTextHeight / 3, mAxisTextPaint);
        }
    }

    // Don't draw labels if they will be cropped.
    private boolean textWillBeCropped(float y, float textHeight) {
        return y + textHeight > mHeight || y - textHeight < 0;
    }

    private void drawRecordingOverlay(Canvas canvas) {
        float xRecordingStart = getScreenX(mChartOptions.getRecordingStartTime());
        if (xRecordingStart < 0) {
            canvas.drawRect(0, mTopPadding, mWidth - mRightPadding, mHeight - mBottomPadding,
                    mRecordingBackgroundPaint);
        } else {
            canvas.drawRect(xRecordingStart, mTopPadding, mWidth - mRightPadding,
                    mHeight - mBottomPadding, mRecordingBackgroundPaint);
            canvas.drawLine(xRecordingStart, mTopPadding, xRecordingStart, mHeight - mBottomPadding,
                    mRecordingTimePaint);
        }
    }

    private void drawStats(Canvas canvas) {
        for (StreamStat stat : mChartData.getStats()) {
            float yValue = getScreenY(stat.getValue());
            mStatsPath.reset();
            mStatsPath.moveTo(mStatDrawableWidth + mStartPadding, yValue);
            mStatsPath.lineTo(mWidth, yValue);
            canvas.drawPath(mStatsPath, getStatPaint(stat.getType()));
            drawStatDrawable((int) yValue, canvas, stat.getType());
        }
    }

    private Paint getStatPaint(int type) {
        switch (type) {
            case StreamStat.TYPE_MIN:
            case StreamStat.TYPE_MAX:
                return mStatMinMaxPaint;
            case StreamStat.TYPE_AVERAGE:
                return mStatAvgPaint;
            default:
                return mStatAvgPaint;
        }
    }

    private void drawStatDrawable(int yOffset, Canvas canvas, int type) {
        Drawable toDraw;
        int startPadding = (int) mStartPadding;
        switch (type) {
            case StreamStat.TYPE_MIN:
                toDraw = mMinDrawable;
                toDraw.setBounds(startPadding,
                        yOffset - mStatDrawableWidth + (int) mStatLineWidth * 2,
                        mStatDrawableWidth + startPadding, yOffset + (int) mStatLineWidth * 2);
                break;
            case StreamStat.TYPE_MAX:
                toDraw = mMaxDrawable;
                toDraw.setBounds(startPadding,
                        yOffset - (int) mStatLineWidth * 2, mStatDrawableWidth + startPadding,
                        yOffset + mStatDrawableWidth - (int) mStatLineWidth * 2);
                break;
            case StreamStat.TYPE_AVERAGE:
                toDraw = mAvgDrawable;
                toDraw.setBounds(startPadding, yOffset - mStatDrawableWidth / 2,
                        mStatDrawableWidth + startPadding, yOffset + mStatDrawableWidth / 2);
                break;
            default:
                return;
        }
        toDraw.draw(canvas);
    }

    private void updateYAxisPoints() {
        // Calculate how many labels and figure out where they should go
        double yMin = mChartOptions.getRenderedYMin();
        double yMax = mChartOptions.getRenderedYMax();
        if (yMax < yMin) {
            return;
        }
        int sizeShown = calculateSizeShownNext(mYAxisPoints, yMin, yMax);
        if (sizeShown < MINIMUM_NUM_LABELS || sizeShown > MAXIMUM_NUM_LABELS) {
            double range = yMax - yMin;
            if (Double.isNaN(range) || range <= 0) {
                range = ChartOptions.MINIMUM_Y_SPREAD;
            }
            int increment = (int) Math.ceil(range / (PREFERRED_NUM_LABELS * 1.0));
            int labelStart = increment * ((int) yMin / increment);
            mYAxisPoints.clear();
            mYAxisPointLabels.clear();

            int count = 0;
            for (int i = labelStart; i < yMax + increment;  i += increment) {
                mYAxisPoints.add(count++, i * 1.0);
                mYAxisPointLabels.add(mChartOptions.getAxisNumberFormat().format(i * 1.0));
            }
        } else {
            // Figure out if any can be added or removed at the same increment value.
            double increment = mYAxisPoints.get(1) - mYAxisPoints.get(0);

            double nextSmallerLabel = mYAxisPoints.get(0) - increment;
            while (nextSmallerLabel > yMin) {
                mYAxisPoints.add(0, nextSmallerLabel);
                mYAxisPointLabels.add(0, mChartOptions.getAxisNumberFormat().format(
                        nextSmallerLabel));
                nextSmallerLabel -= increment;
            }
            double nextLargerLabel = mYAxisPoints.get(mYAxisPoints.size() - 1) + increment;
            while (nextLargerLabel < yMax) {
                mYAxisPoints.add(nextLargerLabel);
                mYAxisPointLabels.add(mChartOptions.getAxisNumberFormat().format(nextLargerLabel));
                nextLargerLabel += increment;
            }
        }
    }

    /**
     * Calculates the number of y axis points shown if the same points are used for labeling the
     * updated range shown.
     * If the possible points list is 0 or 1, there is no way to calculate the difference between
     * y axis labels, so this method returns 0 to prompt a label recalculation.
     * @param yAxisPoints
     * @param yMinShown
     * @param yMaxShown
     * @return
     */
    @VisibleForTesting
    static int calculateSizeShownNext(List<Double> yAxisPoints, double yMinShown,
            double yMaxShown) {
        // If there are 1 or 0 points, we need to re-show anyway, so just return 0.
        if (yAxisPoints.size() < 2) {
            return 0;
        }
        // If we are already labeling points on the Y axis, count how many labels would be
        // drawn if we keep the same points labeled but used the new range. This tells us if we
        // need to recalculate labeled points or not.
        double increment = (yAxisPoints.get(1) - yAxisPoints.get(0));
        int startIndex = (int) Math.floor((yMinShown - yAxisPoints.get(0)) / increment + 1);
        int endIndex = (int) Math.ceil(
                (yMaxShown - yAxisPoints.get(yAxisPoints.size() - 1)) / increment) +
                yAxisPoints.size() - 1;
        return endIndex - startIndex;
    }

    public void onAxisLimitsAdjusted() {
        // Uses transformPath() instead of populatePath() when possible, i.e. when
        // the range loaded (mXMinInPath to mXMaxInPath) is within the rendered
        // range desired (getRenderedXMax and getRenderedXMin).
        if (mChartData.isEmpty()) {
            return;
        }
        boolean newRangeOutsideOfPathRange =
                (mChartOptions.getRenderedXMax() > mXMaxInPath &&
                        mXMaxInPath < mChartData.getXMax()) ||
                        (mChartOptions.getRenderedXMin() < mXMinInPath &&
                                mXMinInPath > mChartData.getXMin());
        boolean newRangeTooLarge = getScreenX(mXMaxInPath) - getScreenX(mXMinInPath) > mWidth * 2;
        if (newRangeOutsideOfPathRange || newRangeTooLarge) {
            populatePath(false);
            postInvalidateOnAnimation();
        } else {
            transformPath();
        }
    }
}
