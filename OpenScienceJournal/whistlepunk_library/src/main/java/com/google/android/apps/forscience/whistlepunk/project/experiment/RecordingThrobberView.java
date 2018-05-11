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
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxEvent;

/** A view containing an animated set of bars to denote a recording in progress */
public class RecordingThrobberView extends View {
  private static final String TAG = "RecordingThrobber";
  private static final int NUMBER_BARS = 5;
  private static final int MS_PER_CYCLE = 500;

  private Paint paint;
  private float width;
  private float height;
  private float barAndSpacingWidth;
  private float[] animatedFraction = new float[NUMBER_BARS];
  private RxEvent stopped = new RxEvent();

  public RecordingThrobberView(Context context) {
    super(context);
    init();
  }

  public RecordingThrobberView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public RecordingThrobberView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  public RecordingThrobberView(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
  }

  private void init() {
    int color = getContext().getResources().getColor(R.color.recording_axis_bar_color);
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(color);
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
      animator.setCurrentPlayTime(
          (long) (MS_PER_CYCLE * (i * 3 + 7 * 1.0 % NUMBER_BARS) / NUMBER_BARS));
      animator.addUpdateListener(
          valueAnimator -> {
            animatedFraction[index] = valueAnimator.getAnimatedFraction();
            // Coordinate the invalidates for performance.
            postInvalidateOnAnimation();
          });
      animator.start();
      stopped.happensNext().subscribe(() -> animator.end());
    }
  }

  public void stopAnimation() {
    stopped.onHappened();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    width = getMeasuredWidth();
    height = getMeasuredHeight();
    // Bars and inter-bar spacing are the same size
    barAndSpacingWidth = width / (NUMBER_BARS * 2 - 1);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float padding = barAndSpacingWidth / 2;
    float halfHeight = height / 2 - padding;
    float top;
    float bottom;
    for (int i = 0; i < NUMBER_BARS; i++) {
      top = padding + halfHeight * animatedFraction[i];
      bottom = height - padding - halfHeight * animatedFraction[i];
      canvas.drawRect(
          i * 2 * barAndSpacingWidth, top, (i * 2 + 1) * barAndSpacingWidth, bottom, paint);
      canvas.drawCircle((i * 2 + .5f) * barAndSpacingWidth, top, barAndSpacingWidth / 2, paint);
      canvas.drawCircle((i * 2 + .5f) * barAndSpacingWidth, bottom, barAndSpacingWidth / 2, paint);
    }
  }
}
