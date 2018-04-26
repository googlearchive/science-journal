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

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

/** Utility class for coloring icons. */
public class ColorUtils {

  // TODO: Find all setColorFilter methods and update them to use this utility function.

  /** Copies and colors a white drawable with the color ID specified. */
  public static Drawable colorDrawable(Context context, Drawable drawable, int colorId) {
    Drawable result = drawable.mutate();
    result.setColorFilter(context.getResources().getColor(colorId), PorterDuff.Mode.MULTIPLY);
    return result;
  }

  /** Copies and colors a black drawable with the color ID specified. */
  public static Drawable colorBlackDrawable(Context context, Drawable drawable, int colorId) {
    Drawable result = drawable.mutate();
    result.setColorFilter(context.getResources().getColor(colorId), PorterDuff.Mode.SRC_IN);
    return result;
  }

  static int getSlightlyLighterColor(int color) {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    hsv[2] *= 1.2;
    return Color.HSVToColor(hsv);
  }

  /**
   * Copies and colors a white drawable with the color specified. Note that this is not the color
   * ID, it should be the real color.
   */
  public static Drawable colorDrawableWithActual(Drawable drawable, int color) {
    Drawable result = drawable.mutate();
    result.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
    return result;
  }
}
