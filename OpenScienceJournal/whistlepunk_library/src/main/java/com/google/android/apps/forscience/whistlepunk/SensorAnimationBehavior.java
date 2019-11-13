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

import androidx.annotation.Nullable;
import android.widget.RelativeLayout;

/** Defines sensor animation behavior for sensor cards. */
public interface SensorAnimationBehavior {
  /**
   * Initialize the icon.
   *
   * @param layout the layout that will contain the icon
   * @param value the note view value, or null
   */
  void initializeLargeIcon(RelativeLayout layout, @Nullable Double value);

  /**
   * Reset the icon.
   *
   * @param layout the layout that contains the icon
   */
  void resetIcon(RelativeLayout layout);

  /** Returns whether the animation and text should be updated together. */
  boolean updateIconAndTextTogether();

  /**
   * Update the icon.
   *
   * @param layout the layout that contains the icon
   */
  void updateIcon(
      RelativeLayout layout, double newValue, double yMin, double yMax, int screenOrientation);
}
