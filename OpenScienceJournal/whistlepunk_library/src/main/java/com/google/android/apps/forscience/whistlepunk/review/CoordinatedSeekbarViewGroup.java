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

package com.google.android.apps.forscience.whistlepunk.review;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

/** Coordinate touches between two seekbars so that they do not pass each other. */
public class CoordinatedSeekbarViewGroup extends RelativeLayout {
  private CropSeekBar startSeekBar;
  private CropSeekBar endSeekBar;
  private CropSeekBar selectedSeekbar = null;

  public CoordinatedSeekbarViewGroup(Context context) {
    super(context);
  }

  public CoordinatedSeekbarViewGroup(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public CoordinatedSeekbarViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public CoordinatedSeekbarViewGroup(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public void setSeekbarPair(CropSeekBar startSeekBar, CropSeekBar endSeekBar) {
    this.startSeekBar = startSeekBar;
    this.endSeekBar = endSeekBar;

    this.startSeekBar.setType(CropSeekBar.TYPE_START);
    this.endSeekBar.setType(CropSeekBar.TYPE_END);
    this.startSeekBar.setOtherSeekbar(this.endSeekBar);
    this.endSeekBar.setOtherSeekbar(this.startSeekBar);

    addView(this.startSeekBar);
    addView(this.endSeekBar);
    this.startSeekBar.setProgress(0);
    this.endSeekBar.setProgress((int) GraphExploringSeekBar.SEEKBAR_MAX);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    // Always intercept touch events!
    return true;
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev) {
    final int action = ev.getActionMasked();
    if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
      if (selectedSeekbar != null) {
        selectedSeekbar.onTouchEvent(ev);
        selectedSeekbar = null;
      }
      return true;
    }
    if (selectedSeekbar == null) {
      // And send them to the children as appropriate -- based on which is closer!
      // Need to make sure to pay attention to which one is active.
      int startProgress = startSeekBar.getProgress();
      int endProgress = endSeekBar.getProgress();
      int touchProgress = (int) (ev.getX() / getWidth() * GraphExploringSeekBar.SEEKBAR_MAX);
      if (Math.abs(touchProgress - startProgress) < Math.abs(touchProgress - endProgress)) {
        selectedSeekbar = startSeekBar;
      } else {
        selectedSeekbar = endSeekBar;
      }
      // Make sure the accessibility focus is updated, so that announcements are generated
      // for the correct view.
      selectedSeekbar.requestFocus();
    }
    selectedSeekbar.onTouchEvent(ev);

    return true;
  }

  public CropSeekBar getStartSeekBar() {
    return startSeekBar;
  }

  public CropSeekBar getEndSeekBar() {
    return endSeekBar;
  }

  public void setMillisecondsInRange(long millisecondsInRange) {
    startSeekBar.setMillisecondsInRange(millisecondsInRange);
    endSeekBar.setMillisecondsInRange(millisecondsInRange);
  }
}
