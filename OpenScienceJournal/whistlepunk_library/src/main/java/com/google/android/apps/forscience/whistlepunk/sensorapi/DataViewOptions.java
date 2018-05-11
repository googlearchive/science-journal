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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;

/**
 * Encapsulates information that a SensorChoice needs in order to choose how to graphically display
 * the data it has gathered.
 */
public class DataViewOptions {
  private final int graphColor;
  private final ScalarDisplayOptions options;

  /**
   * @param colorIndex index for "theme color" for this sensor data view. For example, if this is a
   *     line graph, this is the resource ID for the color in which the line should be drawn. (This
   *     is currently customized per-graph)
   * @param options settings that affect other aspects of how the line should be drawn, especially
   *     its shape. (This is currently shared between all graphs)
   */
  public DataViewOptions(int colorIndex, Context context, ScalarDisplayOptions options) {
    graphColor = context.getResources().getIntArray(R.array.graph_colors_array)[colorIndex];
    this.options = options;
  }

  @VisibleForTesting
  public DataViewOptions(int color, ScalarDisplayOptions options) {
    graphColor = color;
    this.options = options;
  }

  public int getGraphColor() {
    return graphColor;
  }

  public ScalarDisplayOptions getLineGraphOptions() {
    return options;
  }
}
