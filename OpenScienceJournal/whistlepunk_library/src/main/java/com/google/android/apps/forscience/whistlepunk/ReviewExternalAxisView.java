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
import android.graphics.Canvas;
import android.util.AttributeSet;

/** ExternalAxisView used in RunReview. */
public class ReviewExternalAxisView extends ExternalAxisView {
  public ReviewExternalAxisView(Context context) {
    super(context);
  }

  public ReviewExternalAxisView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ReviewExternalAxisView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public ReviewExternalAxisView(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    // Draw the main line
    canvas.drawLine(paddingLeft, paddingTop, width - paddingRight, paddingTop, paint);

    if (timeBetweenTicks == 0 || format == null) {
      return;
    }

    if (timeBetweenTicks < ExternalAxisController.MS_IN_SEC) {
      // Adjust the time and distance between ticks so that there is at least one
      // second per labeled tick.
      timeBetweenTicks = ExternalAxisController.MS_IN_SEC / 2;
    } else {
      // Round to label at seconds.
      timeBetweenTicks =
          ((int) timeBetweenTicks / ExternalAxisController.MS_IN_SEC)
              * ExternalAxisController.MS_IN_SEC;
    }

    // Recalculate distance between ticks.
    distanceBetweenTicks =
        super.getOffsetForTimestamp(recordingStart + timeBetweenTicks)
            - super.getOffsetForTimestamp(recordingStart);

    // Don't draw any ticks before the padding.
    long minTickTime = getTimestampForOffset(paddingLeft);
    int multiplier = (int) Math.ceil((minTickTime - recordingStart) * 1.0 / timeBetweenTicks);
    long firstTickTime = multiplier * timeBetweenTicks + recordingStart;

    // Label ticks starting with mRecordingStart.
    boolean labelTick = multiplier % 2 == 0;

    super.drawTicks(canvas, firstTickTime, xMax, labelTick, paddingTop + tickPaddingTop);
  }
}
