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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.AttributeSet;
import android.view.View;


/**
 * A View which draws a level.
 */
public class LevelView extends View {
    // The level's background.
    private ShapeDrawable mBackground;

    // The level's bubble.
    private ShapeDrawable mBubble;

    // The marks on the level.
    private Paint mMarkPaint;

    // The outer edge of the level.
    private Paint mEdgePaint;

    private int mPaddingLeft;
    private int mPaddingTop;
    private int mPaddingRight;
    private int mPaddingBottom;
    private int mContentWidth;
    private int mContentHeight;
    private int mBubbleWidth;

    private int mBubbleOffsetX = 0;
    private int mBubbleOffsetY = 0;

    private float[] mGravity = null;
    private static final float mForceGravity = -9.81f;

    private static final int mInterpolationFactor = 3;

    private static final float mPrecision = .1f;

    public LevelView(Context context) {
        super(context);
        init(null, 0);
    }

    public LevelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public LevelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.LevelView, defStyle, 0);

        mBackground = new ShapeDrawable(new OvalShape());
        mBackground.getPaint().setColor(getResources().getColor(R.color.level_background_color));
        mBubble = new ShapeDrawable(new OvalShape());
        mBubble.getPaint().setColor(getResources().getColor(R.color.level_bubble_color));

        mMarkPaint = new Paint();
        mMarkPaint.setColor(getResources().getColor(R.color.level_mark_color));

        mEdgePaint = new Paint();
        mEdgePaint.setColor(getResources().getColor((R.color.level_edge_color)));
        mEdgePaint.setStyle(Paint.Style.STROKE);
        mEdgePaint.setStrokeWidth(a.getDimension(R.styleable.LevelView_edgeWidth, 10));

        a.recycle();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mPaddingLeft = getPaddingLeft();
        mPaddingTop = getPaddingTop();
        mPaddingRight = getPaddingRight();
        mPaddingBottom = getPaddingBottom();

        mContentWidth = getWidth() - mPaddingLeft - mPaddingRight;
        mContentHeight = getHeight() - mPaddingTop - mPaddingBottom;
        mBubbleWidth = mContentWidth / 10;
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mGravity == null) {
            return;
        }

        // Draw the drawable. We don't allow rotation in the level so we can assume that the
        // content width is less than the content height.
        if (mBackground != null) {
            int xMin = mPaddingLeft;
            int xMax = xMin + mContentWidth;
            int yMin = mPaddingTop + (mContentHeight - mContentWidth) / 2;
            int yMax = yMin + mContentWidth;

            mBackground.setBounds(xMin, yMin, xMax, yMax);
            mBackground.draw(canvas);

            // Draw some marks on the level above the bubble.
            canvas.drawCircle(xMin + mContentWidth / 2, yMin + mContentWidth / 2, mBubbleWidth * 3,
                    mMarkPaint);
            // Create a doughnut by drawing a background-colored circle inside the previous circle.
            canvas.drawCircle(xMin + mContentWidth / 2, yMin + mContentWidth / 2, mBubbleWidth * 2,
                    this.mBackground.getPaint());

            // Draws two rectangles through the center point to create a thick 'X' marking the
            // X and Y axes.
            double rad = Math.toRadians(6);
            canvas.drawRect((float) (xMin + mContentWidth / 2 * (1 - Math.sin(rad))),
                    (float) (yMin + mContentWidth / 2 * (1 - Math.cos(rad))),
                    (float) (xMin + mContentWidth / 2 * (1 + Math.sin(rad))),
                    (float) (yMin + mContentWidth / 2 * (1 + Math.cos(rad))), mMarkPaint);
            canvas.drawRect((float) (xMin + mContentWidth / 2 * (1 - Math.cos(rad))),
                    (float) (yMin + mContentWidth / 2 * (1 - Math.sin(rad))),
                    (float) (xMin + mContentWidth / 2 * (1 + Math.cos(rad))),
                    (float) (yMin + mContentWidth / 2 * (1 + Math.sin(rad))),
                    mMarkPaint);

            // Draw the background edge.
            float edgeWidth = mEdgePaint.getStrokeWidth();
            float innerDiameter = (mContentWidth - edgeWidth) / 2;
            canvas.drawCircle(xMin + mContentWidth / 2, yMin + mContentWidth / 2,
                    innerDiameter, mEdgePaint);

            // Scale and move the bubble due to gravity.
            float xGravity = 1 - mGravity[0] / mForceGravity;
            float yGravity = 1 + mGravity[1] / mForceGravity;

            // The "goal" is for the bubble reach an offset defined by gravity.
            int xBubbleGoal = (int) (xGravity * (mContentWidth - mBubbleWidth) / 2);
            int yBubbleGoal = (int) (yGravity * (mContentWidth - mBubbleWidth) / 2);

            // We only move the bubble to the "goal" if the bubble has not yet been placed.
            // Otherwise the bubble is moved partway between the goal and the current position based
            // on the interpolation factor.
            if (mBubbleOffsetX == 0) {
                mBubbleOffsetX = xBubbleGoal;
                mBubbleOffsetY = yBubbleGoal;
            } else {
                mBubbleOffsetX = mBubbleOffsetX + (xBubbleGoal - mBubbleOffsetX) /
                        mInterpolationFactor;
                mBubbleOffsetY = mBubbleOffsetY + (yBubbleGoal - mBubbleOffsetY) /
                        mInterpolationFactor;
            }

            int xBubble = xMin + mBubbleOffsetX;
            int yBubble = yMin + mBubbleOffsetY;

            mBubble.setBounds(xBubble, yBubble, xBubble + mBubbleWidth, yBubble + mBubbleWidth);

            // Change the color if it is mostly centered.
            if (Math.abs(mGravity[0]) < mPrecision && Math.abs(mGravity[1]) < mPrecision) {
                mBubble.getPaint().setColor(getResources().getColor(R.color.level_bubble_color_centered));
            } else {
                mBubble.getPaint().setColor(getResources().getColor(R.color.level_bubble_color));
            }

            mBubble.draw(canvas);

        }
    }

    public void updateGravityValues(float[] values) {
        mGravity = values;
        invalidate();
    }
}
