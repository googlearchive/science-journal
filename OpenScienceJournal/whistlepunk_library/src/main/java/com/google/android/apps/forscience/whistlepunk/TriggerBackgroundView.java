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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * A view for the trigger section's background, which can animate its looks for firing triggers.
 *
 * TODO: Can this be done with a drawable to increase efficiency?
 */
public class TriggerBackgroundView extends View {

    private static final long NO_TRIGGER_FIRING = -1;
    private static final long ANIMATION_PHASE_DURATION = 300;

    private static final int STATE_NOT_FIRING = 0;
    private static final int STATE_INCREASING = 1;
    private static final int STATE_STEADY = 2;
    private static final int STATE_DECREASING = 3;

    public interface TriggerAnimationListener {
        void onAnimationStart();
        void onAnimationEnd();
    }

    private TriggerAnimationListener mAnimationListener;

    private int mWidth;
    private int mHeight;
    private int mStartCenterX;
    private int mRadiusToUsePath;
    private Paint mTriggerPaint;
    private Paint mBackgroundPaint;
    private RectF mTriggerOutline;
    private Path mTriggerPath;

    private long mLastFiredTimestamp = NO_TRIGGER_FIRING;
    private long mAnimationStateStartTimestamp = NO_TRIGGER_FIRING;
    private int mAnimationState;

    private boolean isLtrLayout;

    public TriggerBackgroundView(Context context) {
        super(context);
        init();
    }

    public TriggerBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TriggerBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(21)
    public TriggerBackgroundView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        int backgroundColor = getResources().getColor(R.color.text_color_dark_grey);
        int triggerColor = getResources().getColor(R.color.trigger_fire_color);

        mTriggerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTriggerPaint.setColor(triggerColor);
        mTriggerPaint.setStyle(Paint.Style.FILL);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(backgroundColor);
        mBackgroundPaint.setStyle(Paint.Style.FILL);

        mTriggerOutline = new RectF();
        mTriggerPath = new Path();

        isLtrLayout = getResources().getConfiguration().getLayoutDirection() ==
                View.LAYOUT_DIRECTION_LTR;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();
        int centerX = getResources().getDimensionPixelSize(
                R.dimen.accessibility_touch_target_min_size) / 2;
        mStartCenterX = isLtrLayout ? centerX : mWidth - centerX;

        // When the radius reaches this size, the circle will touch or extend past the
        // top left and lower left corners. At this point, we should start using a path
        // to draw the trigger color.
        mRadiusToUsePath = (int) Math.sqrt(mHeight * mHeight + mStartCenterX * mStartCenterX);
    }

    public void setAnimationListener(TriggerAnimationListener animationListener) {
        mAnimationListener = animationListener;
    }

    public void onTriggerFired() {
        if (mLastFiredTimestamp == NO_TRIGGER_FIRING) {
            mAnimationListener.onAnimationStart();
        }
        mLastFiredTimestamp = System.currentTimeMillis();
        updateAnimationStateOnTriggerFired();
        postInvalidateOnAnimation();
    }

    // When a trigger fires, it may update the animation state. Update vars tracking where we are
    // in the animation progress.
    private void updateAnimationStateOnTriggerFired() {
        if (mAnimationState == STATE_NOT_FIRING) {
            mAnimationState = STATE_INCREASING;
            mAnimationStateStartTimestamp = mLastFiredTimestamp;
        } else if (mAnimationState == STATE_INCREASING) {
            // Do nothing - we don't interrupt an increase
        } else if (mAnimationState == STATE_STEADY) {
            mAnimationStateStartTimestamp = mLastFiredTimestamp;
        } else if (mAnimationState == STATE_DECREASING) {
            mAnimationState = STATE_INCREASING;
            // Calculate the "state start timestamp" based on the amount of decrease
            // we have already experienced.
            long now = System.currentTimeMillis();

            // How many millis have we already progressed through the phase
            long currentProgress = ANIMATION_PHASE_DURATION - (now - mAnimationStateStartTimestamp);

            // To increase from the same point, just reverse.
            long newProgressInPhaseIncreaing = ANIMATION_PHASE_DURATION - currentProgress;

            // And that gives us our new increase state start timestamp.
            mAnimationStateStartTimestamp = now - newProgressInPhaseIncreaing;
        }
    }

    // Updates the current state of the trigger after some time has passed, checking if it is time
    // to enter a new state.
    private void updateAnimationStateOnClockTick() {
        long now = System.currentTimeMillis();
        if (now - mAnimationStateStartTimestamp > ANIMATION_PHASE_DURATION) {
            mAnimationStateStartTimestamp = now;
            mAnimationState = (mAnimationState + 1) % 4;
        }
    }

    // Calculates the fraction which the trigger bar should be filled.
    private double calculateFractionFull() {
        if (mAnimationState == STATE_STEADY) {
            return 1.0;
        }

        long now = System.currentTimeMillis();
        // Based on when this animation state "started".
        long diff = now - mAnimationStateStartTimestamp;
        double result = 0;
        if (mAnimationState == STATE_INCREASING) {
            result = (float) diff / (float) ANIMATION_PHASE_DURATION;
        } else if (mAnimationState == STATE_DECREASING) {
            result = (float) (ANIMATION_PHASE_DURATION - diff) /
                    (float) ANIMATION_PHASE_DURATION;
        }
        // Pass the result through an interpolator, in this case a simple square root, to get
        // a quick-start / slow-finish result. In this case, quicker when the fullness is lower,
        // and slower when the fullness is closer to 1.
        return Math.min(1, Math.sqrt(result));
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, mWidth, mHeight, mBackgroundPaint);
        if (mLastFiredTimestamp != NO_TRIGGER_FIRING) {
            updateAnimationStateOnClockTick();

            // End the animation process
            if (mAnimationState == STATE_NOT_FIRING) {
                mLastFiredTimestamp = NO_TRIGGER_FIRING;
                mAnimationListener.onAnimationEnd();
            }

            double fractionFull = calculateFractionFull();
            if (fractionFull >= 1.0) {
                canvas.drawRect(0, 0, mWidth, mHeight, mTriggerPaint);
            } else {
                double radius = (mWidth * fractionFull);
                int radInt = (int) radius;
                mTriggerOutline.set(mStartCenterX - radInt, mHeight / 2 - radInt,
                        mStartCenterX + radInt, mHeight / 2 + radInt);
                // For large radiuses, define a path instead of drawing the whole oval, otherwise
                // the path can get too large to render.
                if (radInt > mRadiusToUsePath) {
                    // Calculate the angle between the horizontal and where the arc intersects the
                    // trigger box.
                    float angle = (float) Math.toDegrees(Math.asin(mHeight / 2 / radius));

                    mTriggerPath.reset();
                    if (isLtrLayout) {
                        mTriggerPath.moveTo(0, 0);
                        // arcTo automatically creates a lineTo the start of the arc, so we don't
                        // actually need to do as much math!
                        mTriggerPath.arcTo(mTriggerOutline, -angle, 2 * angle);
                        mTriggerPath.lineTo(0, mHeight);
                        mTriggerPath.lineTo(0, 0);
                    } else {
                        mTriggerPath.moveTo(mWidth, 0);
                        mTriggerPath.lineTo(mWidth, mHeight);
                        // arcTo automatically creates a lineTo the start of the arc, so we don't
                        // actually need to do as much math!
                        mTriggerPath.arcTo(mTriggerOutline, 180 - angle, 2 * angle);
                        mTriggerPath.lineTo(mWidth, 0);
                    }
                    canvas.drawPath(mTriggerPath, mTriggerPaint);
                } else {
                    canvas.drawOval(mTriggerOutline, mTriggerPaint);
                }
            }
            postInvalidateOnAnimation();
        }
    }
}
