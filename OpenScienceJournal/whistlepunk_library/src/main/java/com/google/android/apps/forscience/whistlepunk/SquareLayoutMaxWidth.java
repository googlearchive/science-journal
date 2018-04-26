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
package com.google.android.apps.forscience.whistlepunk;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/** A RelativeLayout that is a square, based on the available width. */
public class SquareLayoutMaxWidth extends RelativeLayout {
  public SquareLayoutMaxWidth(Context context) {
    super(context);
  }

  public SquareLayoutMaxWidth(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SquareLayoutMaxWidth(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @TargetApi(21)
  public SquareLayoutMaxWidth(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // Set the width and height to be the width, i.e. the view is as wide as it would like
    // to be and the height is set to match.
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
  }
}
