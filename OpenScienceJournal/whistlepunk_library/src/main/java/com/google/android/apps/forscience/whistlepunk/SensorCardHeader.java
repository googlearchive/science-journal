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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

/**
 * SensorCardHeader gets Touch Events and calls an OnTouchListener function. It does not steal touch
 * events from its children.
 */
public class SensorCardHeader extends RelativeLayout {

  public interface onHeaderTouchListener {
    void onTouch();
  }

  private onHeaderTouchListener onTouchListener;

  public SensorCardHeader(Context context) {
    super(context);
  }

  public SensorCardHeader(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SensorCardHeader(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @TargetApi(21)
  public SensorCardHeader(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public void setOnHeaderTouchListener(onHeaderTouchListener listener) {
    onTouchListener = listener;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (onTouchListener != null) {
      onTouchListener.onTouch();
    }
    return false;
  }
}
