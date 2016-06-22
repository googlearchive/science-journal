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
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * A View which draws a ruler based on DisplayMetrics and user defined calibration.
 */
public class RulerView extends View {

    private static final String KEY_RULER_SCALE_FACTOR = "key_ruler_scale_factor";

    private static final int INVALID_POINTER_ID = -1;

    private static final float MIN_SCALE_FACTOR = .25f;
    private static final float MAX_SCALE_FACTOR = 1f;

    // See attrs_ruler_view.xml for enum values: 0 = metric, 1 = imperial.
    @IntDef({UNITS_METRIC, UNITS_IMPERIAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Units {}
    public static final int UNITS_METRIC = 0;
    public static final int UNITS_IMPERIAL = 1;

    /**
     * Units of the ruler.
     * NOTE: can't use @Units here or else parsing fails.
     */
    private int mUnits;

    /**
     * Foreground color used to draw the hash lines on the ruler and the hash labels.
     */
    private int mRulerColor = Color.BLACK;

    /**
     * Amount of space between each hash line.
     */
    private int mSpacingPx;

    /**
     * Base length of the hash mark (1 in every 10 marks is doubled in length)
     */
    private int mHashSizePx;
    // Members controlling the ruler markings and hash labels.
    private float mRulerTextSize;
    private TextPaint mRulerTextPaint;

    private float mRulerTextHeight;

    private int mActivePointerId = INVALID_POINTER_ID;

    /**
     * Minimum X value for touch label coordinates.
     * <p>This is a multiple of {@link #mHashSizePx}.</p>
     */
    private float mMinTouchLabelXPx;

    /**
     * Additional offset for the X value in touch label coordinates.
     * <p>Used to prevent the label from appearing under the finger.</p>
     */
    private float mTouchLabelOffsetPx;

    // Members controlling the touch labels.
    private int mTouchLabelColor;
    private float mTouchLabelTextSize;
    private TextPaint mTouchLabelTextPaint;
    private float mTouchLabelTextHeight;

    private Paint mCalibratePaint;

    /**
     * Stores the last known points, keyed by pointer ID (@link MotionEvent#getPointerId).
     */
    private SparseArray<Point> mTouches;

    /**
     * If {@code true}, we are in calibration mode, not measuring mode.
     */
    private boolean mCalibrationMode;

    /**
     * A scaling factor to calibrate the ruler for different screens.
     */
    private float mScaleFactor = 1.0f;

    private SharedPreferences mPreferences;

    /**
     * Current Y position during user calibration.
     */
    private float mCalibrationY = -1.0f;

    /**
     * Calibration target for calibration mode.
     * <p>The target defines the real value that the user should be touching in calibration mode.
     * </p>
     */
    private float mCalibrationTarget;

    public RulerView(Context context) {
        super(context);
        init(null, 0);
    }

    public RulerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public RulerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        mScaleFactor = mPreferences.getFloat(KEY_RULER_SCALE_FACTOR, 1.0f);

        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.RulerView, defStyle, 0);

        mUnits = a.getInteger(R.styleable.RulerView_rulerUnits, 0);
        mRulerColor = a.getColor(R.styleable.RulerView_rulerColor, Color.BLACK);
        mRulerTextSize = a.getDimension(R.styleable.RulerView_rulerTextSize, 24);
        mTouchLabelColor = a.getColor(R.styleable.RulerView_touchLabelColor, Color.BLUE);
        mTouchLabelTextSize = a.getDimension(R.styleable.RulerView_touchLabelTextSize, 48);

        a.recycle();

        // Set up the TextPaint object used to draw the labels on the ruler.
        mRulerTextPaint = new TextPaint();
        mRulerTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mRulerTextPaint.setTextAlign(Paint.Align.CENTER);

        mCalibratePaint = new Paint();
        mCalibratePaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mCalibratePaint.setStyle(Paint.Style.STROKE);
        mCalibratePaint.setPathEffect(new DashPathEffect(new float[] {1.0f, 1.0f}, 0));
        mCalibratePaint.setStrokeWidth(1);
        mCalibratePaint.setColor(mRulerColor);

        mTouchLabelTextPaint = new TextPaint();
        mTouchLabelTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTouchLabelTextPaint.setTextAlign(Paint.Align.CENTER);

        mHashSizePx = getResources().getDimensionPixelSize(R.dimen.ruler_hash_size);
        // Magic number is only used once: not worth a const.
        mMinTouchLabelXPx = mHashSizePx * 4.5f;
        mTouchLabelOffsetPx = getResources().getDimensionPixelOffset(R.dimen.ruler_label_offset);

        calculateSpacing();
        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements();

        mTouches = new SparseArray<>();
    }

    private void calculateSpacing() {
        // Retrieve display metrics
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        manager.getDefaultDisplay().getMetrics(metrics);

        // First we calculate a 1/10 of an inch in pixels since dpi = "dots per inch"
        mSpacingPx = 16 * metrics.densityDpi / DisplayMetrics.DENSITY_MEDIUM;
        if (mUnits == 0) {
            // Metric: we want every mm, so convert.
            mSpacingPx /= 2.54;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (mCalibrationMode) {
                    startCalibration(event);
                } else {
                    addTouch(event);
                }
                // Return true from here to get the rest of the events from this gesture.
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mCalibrationMode) {
                    updateCalibration(event);
                } else {
                    updateTouch(event);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (mCalibrationMode) {
                    finishCalibration(event);
                } else {
                    removeTouch(event);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (!mCalibrationMode) {
                    removeAllTouches();
                }
                break;
            }
        }


        return super.onTouchEvent(event);
    }

    private void addTouch(MotionEvent event) {
        final int pointerIndex = getPointerIndex(event.getAction());
        final int pointerId = event.getPointerId(pointerIndex);
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        mTouches.append(pointerId, new Point(x, y));
        mActivePointerId = pointerId;
        invalidateFromTouch(y, y);
    }

    private void updateTouch(MotionEvent event) {
        // Update all the pointers.
        for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); ++pointerIndex) {
            final int x = (int) event.getX(pointerIndex);
            final int y = (int) event.getY(pointerIndex);
            final int pointerId = event.getPointerId(pointerIndex);
            Point oldPoint =  mTouches.get(pointerId);
            if (oldPoint == null) {
                // Don't know what this point is.
                continue;
            }
            final int oldX = oldPoint.x;
            final int oldY = oldPoint.y;
            oldPoint.set(x, y);
            final int minY = Math.min(y, oldY);
            final int maxY = Math.max(y, oldY);
            invalidateFromTouch(minY, maxY);
        }
    }

    /**
     * Removes the touch represented by the motion event.
     * @param event MotionEvent with action = ACTION_UP or ACTION_POINTER_UP
     */
    private void removeTouch(MotionEvent event) {
        final int action = event.getAction();
        final int pointerIndex = getPointerIndex(action);
        final int pointerId = event.getPointerId(pointerIndex);
        Point oldPoint = mTouches.get(pointerId);
        if (oldPoint != null) {
            mTouches.remove(pointerId);
            invalidateFromTouch(oldPoint.y, oldPoint.y);
        }
        if (action == MotionEvent.ACTION_UP) {
            mActivePointerId = INVALID_POINTER_ID;
        } else {
            if (pointerId == mActivePointerId) {
                final int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
                mActivePointerId = event.getPointerId(newPointerIndex);
            }
        }
    }

    private void removeAllTouches() {
        mTouches.clear();
        invalidate();
    }

    private void startCalibration(MotionEvent event) {
        final int pointerIndex = getPointerIndex(event.getAction());
        final int pointerId = event.getPointerId(pointerIndex);
        updateScaleFactor(event.getY(pointerIndex));

        mActivePointerId = pointerId;
        invalidate();
    }

    private void updateCalibration(MotionEvent event) {
        if (mActivePointerId == INVALID_POINTER_ID) {
            return;
        }
        final int pointerIndex = event.findPointerIndex(mActivePointerId);
        updateScaleFactor(event.getY(pointerIndex));

        invalidate();
    }

    private void finishCalibration(MotionEvent event) {
        final int action = event.getAction();
        final int pointerIndex = getPointerIndex(action);
        final int pointerId = event.getPointerId(pointerIndex);
        if (action == MotionEvent.ACTION_UP) {
            mActivePointerId = INVALID_POINTER_ID;
            // Save the value here now.
            mPreferences.edit().putFloat(KEY_RULER_SCALE_FACTOR, mScaleFactor).apply();
        } else {
            if (pointerId == mActivePointerId) {
                final int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
                mActivePointerId = event.getPointerId(newPointerIndex);
            }
        }

        invalidate();
    }

    private void updateScaleFactor(float y) {
        int contentHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        float realY = contentHeight - y;
        // The ideal size would be the calibration target * the number of pixels per unit.
        float idealSizePx = mCalibrationTarget * mSpacingPx;
        // The scale factor is what is necessary to line up the real size with the ideal size.
        float scaleFactor = realY / idealSizePx;

        if (scaleFactor < MIN_SCALE_FACTOR) {
            mScaleFactor = MIN_SCALE_FACTOR;
        } else if (scaleFactor > MAX_SCALE_FACTOR) {
            mScaleFactor = MAX_SCALE_FACTOR;
        } else {
            mScaleFactor = scaleFactor;
            mCalibrationY = y;
        }
    }

    private int getPointerIndex(int action) {
        return (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    }

    private void invalidateTextPaintAndMeasurements() {
        mRulerTextPaint.setTextSize(mRulerTextSize);
        mRulerTextPaint.setColor(mRulerColor);

        Paint.FontMetrics fontMetrics = mRulerTextPaint.getFontMetrics();
        mRulerTextHeight = fontMetrics.bottom;

        mTouchLabelTextPaint.setTextSize(mTouchLabelTextSize);
        mTouchLabelTextPaint.setColor(mTouchLabelColor);

        fontMetrics = mTouchLabelTextPaint.getFontMetrics();
        mTouchLabelTextHeight = fontMetrics.bottom;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // TODO: consider storing these as member variables to reduce
        // allocations per draw cycle.
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        int contentWidth = getWidth() - paddingLeft - paddingRight;
        int contentHeight = getHeight() - paddingTop - paddingBottom;
        final float mScaledSpacingPx = mSpacingPx * mScaleFactor;
        if (mCalibrationMode) {
            if (mCalibrationY > 0) {
                canvas.drawLine(0, mCalibrationY, contentWidth, mCalibrationY, mTouchLabelTextPaint);
            }
        } else {
            // Draw all the touch lines
            for (int index = 0, end = mTouches.size(); index < end; ++index) {
                Point point = mTouches.get(mTouches.keyAt(index));
                canvas.drawLine(0, point.y, contentWidth, point.y, mTouchLabelTextPaint);

                // Label the touch line: convert from mScaledSpacingPx to units, then draw it.
                float value = (float) (contentHeight - point.y) / mScaledSpacingPx;
                if (mUnits == UNITS_IMPERIAL) {
                    value /= 10;
                }
                String textLabelString = getResources().getString(mUnits == 0 ?
                        R.string.ruler_label_metric :
                        R.string.ruler_label_imperial, value);
                final float measuredTextLength = mTouchLabelTextPaint.measureText(textLabelString);

                // Try to put the label near the finger.
                float xCoordinate = Math.max(mMinTouchLabelXPx, point.x - measuredTextLength
                        - mTouchLabelOffsetPx);

                if (point.x < mMinTouchLabelXPx - mTouchLabelOffsetPx) {
                    // User is probably using their other hand. Move to the right so this is visible.
                    xCoordinate = point.x + measuredTextLength + mTouchLabelOffsetPx;
                }
                // TODO: the label might also be behind the action bar. Should move it downwards in
                // that case.
                canvas.drawText(textLabelString, xCoordinate, point.y - mTouchLabelOffsetPx,
                        mTouchLabelTextPaint);
            }
        }

        // Start from the base of the view and move up, drawing the text and lines as necessary.
        int hashCount = 0;
        canvas.translate(0, contentHeight);
        for (int yPos = contentHeight; yPos >= 0; yPos -= mScaledSpacingPx) {
            canvas.translate(0, -mScaledSpacingPx);
            hashCount++;
            int length = mHashSizePx;
            if (hashCount % 10 == 0) {
                length = mHashSizePx * 2;
                // Draw the text rotated.
                canvas.rotate(-90);
                int labelValue = hashCount;
                if (mUnits == UNITS_IMPERIAL) {
                    labelValue = hashCount / 10;
                }
                canvas.drawText(String.valueOf(labelValue), (mRulerTextHeight / -2),
                        length + (mRulerTextHeight * 4), mRulerTextPaint);
                canvas.rotate(90);
            }
            canvas.drawLine(0, 0, length, 0, mCalibrationMode ? mCalibratePaint : mRulerTextPaint);
        }
    }

    public @Units int getUnits() {
        return mUnits;
    }

    public int getRulerColor() {
        return mRulerColor;
    }

    public void setRulerColor(int color) {
        mRulerColor = color;
        invalidateTextPaintAndMeasurements();
    }

    public float getRulerTextSize() {
        return mRulerTextSize;
    }

    public void setRulerTextSize(float textSize) {
        mRulerTextSize = textSize;
        invalidateTextPaintAndMeasurements();
    }

    public void setCalibrationMode(boolean mode) {
        mCalibrationMode = mode;
        invalidate();
    }

    public boolean getCalibrationMode() {
        return mCalibrationMode;
    }

    /**
     * Sets the target calibration size, in the appropriate units.
     */
    public void setCalibrationTarget(float target, @Units int units) {
        if (mUnits != units) {
            throw new IllegalArgumentException("Must send calibration target in matching units");
        }
        mCalibrationTarget = target;
    }

    /**
     * Invalidates an area based on the touch region. This needs to have a big enough band to
     * handle the label.
     * <p>We invalidate the entire X range since we draw horizontally across the screen.</p>
     */
    private void invalidateFromTouch(int minY, int maxY) {
        invalidate(0, (int) (minY - (mTouchLabelTextSize + mTouchLabelOffsetPx)),
                getWidth(), maxY + 10);
    }
}
