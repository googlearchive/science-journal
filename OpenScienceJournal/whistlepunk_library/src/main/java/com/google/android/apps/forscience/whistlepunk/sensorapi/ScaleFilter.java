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

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig.BleSensorConfig.ScaleTransform;

/** Filter that applies a linear function to the incoming function */
public class ScaleFilter implements ValueFilter {
  private final double sourceBottom;
  private final double destBottom;
  private final double sourceRange;
  private final double destRange;

  public ScaleFilter(ScaleTransform transform) {
    sourceBottom = transform.getSourceBottom();
    sourceRange = transform.getSourceTop() - sourceBottom;
    destBottom = transform.getDestBottom();
    destRange = transform.getDestTop() - destBottom;
  }

  @Override
  public double filterValue(long timestamp, double value) {
    double ratio = (value - sourceBottom) / sourceRange;
    double transformed = (ratio * destRange) + destBottom;
    return transformed;
  }
}
