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
import androidx.appcompat.widget.AppCompatTextView;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;

/**
 * TextView which resizes its text to fit on a single line. The text inside the view is only ever
 * sized smaller, so that it may shrink but never grow again. This avoids lots of text changes if
 * the text in the view is updating rapidly and the length is changing, i.e. 99 <-> 100. If the user
 * wishes to reset the text to the max size, they can call resetTextSize(). For example,
 * resetTextSize should be used when this view is recycled and something totally different should be
 * displayed in it.
 */
public class SingleLineResizableTextView extends AppCompatTextView {

  private static final String TAG = "ResizableTextView";

  private float minSize;
  private float maxSize = -1;
  private float oneSp;

  private float width;

  public SingleLineResizableTextView(Context context) {
    super(context);
    init();
  }

  public SingleLineResizableTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public SingleLineResizableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setSingleLine(true);
    maxSize = getTextSize();
    minSize = getResources().getDimensionPixelSize(R.dimen.min_resizable_text_size);
    oneSp = getResources().getDimensionPixelSize(R.dimen.one_sp);
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldwidth, int oldheight) {
    super.onSizeChanged(width, height, oldwidth, oldheight);
    if (width != oldwidth) {
      this.width = width;
      if (width > oldwidth) {
        // If we have gotten larger, reset the text size so we can fill up the view.
        resetTextSize();
      } else {
        resizeTextIfNeeded(getText());
      }
    }
  }

  public void resetTextSize() {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, maxSize);
    resizeTextIfNeeded(getText());
  }

  @Override
  protected void onTextChanged(
      final CharSequence text, final int start, final int before, final int after) {
    resizeTextIfNeeded(text);
  }

  private void resizeTextIfNeeded(CharSequence text) {
    if (this.width <= 0 || TextUtils.isEmpty(text)) {
      return;
    }
    TextPaint paint = getPaint();
    float width = paint.measureText(text, 0, text.length());
    float size = getTextSize();
    while (width > this.width) {
      // It needs to be resized, if possible
      if (size > minSize) {
        // We can still get smaller
        size = Math.max(size - oneSp, minSize);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        width = paint.measureText(text, 0, text.length());
      } else {
        // We cannot go any smaller! Give up.
        return;
      }
    }
  }
}
