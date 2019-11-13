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
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.drawerlayout.widget.DrawerLayout;

/**
 * This is a workaround to a multi-touch bug in DrawerLayout as described in
 * https://code.google.com/p/android/issues/detail?id=60464. This is taken nearly verbatum from a
 * solution in that thread, https://code.google.com/p/android/issues/detail?id=60464#c5.
 */
public class MultiTouchDrawerLayout extends DrawerLayout {
  public MultiTouchDrawerLayout(Context context) {
    super(context);
  }

  public MultiTouchDrawerLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public MultiTouchDrawerLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  private boolean isDisallowIntercept = false;

  @Override
  public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    // keep the info about if the innerViews do requestDisallowInterceptTouchEvent
    isDisallowIntercept = disallowIntercept;
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    // the incorrect array size will only happen in the multi-touch scenario.
    if (ev.getPointerCount() > 1 && isDisallowIntercept) {
      requestDisallowInterceptTouchEvent(false);
      boolean handled = super.dispatchTouchEvent(ev);
      requestDisallowInterceptTouchEvent(true);
      return handled;
    } else {
      return super.dispatchTouchEvent(ev);
    }
  }
}
