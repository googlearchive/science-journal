
/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxEvent;

/**
 * A view containing an animated set of bars to denote a recording in progress
 */
public class RecordingThrobberView extends View {
    private static final String TAG = "RecordingThrobber";
    private static final int NUMBER_BARS = 5;
    private static final int MS_PER_CYCLE = 500;

    private Paint mPaint;
    private float mWidth;
    private float mHeight;
    private float mBarAndSpacingWidth;
    private float[] mAnimatedFraction = new float[NUMBER_BARS];
    private RxEvent mStopped = new RxEvent();

    public RecordingThrobberView(Context context) {
        super(context);
        init();
    }

    public RecordingThrobberView(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecordingThrobberView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public RecordingThrobberView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        int color = getContext().getResources().getColor(R.color.recording_axis_bar_color);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(color);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    public void startAnimation() {
        for (int i = 0; i < NUMBER_BARS; i++) {
            final int index = i;
            ValueAnimator animator = ValueAnimator.ofFloat(0, 100);
            animator.setDuration(MS_PER_CYCLE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            // Get sorta random starts using some prime numbers and modulo math
            animator.setCurrentPlayTime((long) (MS_PER_CYCLE * (i * 3 + 7 * 1.0 % NUMBER_BARS) /
                                                NUMBER_BARS));
            animator.addUpdateListener(valueAnimator -> {
                mAnimatedFraction[index] = valueAnimator.getAnimatedFraction();
                // Coordinate the invalidates for performance.
                postInvalidateOnAnimation();
            });
            animator.start();
            mStopped.happensNext().subscribe(() -> animator.end());
        }
    }

    public void stopAnimation() {
        mStopped.onHappened();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();
        // Bars and inter-bar spacing are the same size
        mBarAndSpacingWidth = mWidth / (NUMBER_BARS * 2 - 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float padding = mBarAndSpacingWidth / 2;
        float halfHeight =  mHeight / 2 - padding;
        float top;
        float bottom;
        for (int i = 0; i < NUMBER_BARS; i++) {
            top = padding + halfHeight * mAnimatedFraction[i];
            bottom = mHeight - padding - halfHeight * mAnimatedFraction[i];
            canvas.drawRect(i * 2 * mBarAndSpacingWidth, top, (i * 2 + 1) * mBarAndSpacingWidth,
                    bottom, mPaint);
            canvas.drawCircle((i * 2 + .5f) * mBarAndSpacingWidth, top, mBarAndSpacingWidth / 2,
                    mPaint);
            canvas.drawCircle((i * 2 + .5f) * mBarAndSpacingWidth, bottom, mBarAndSpacingWidth / 2,
                    mPaint);
        }

    }
}
