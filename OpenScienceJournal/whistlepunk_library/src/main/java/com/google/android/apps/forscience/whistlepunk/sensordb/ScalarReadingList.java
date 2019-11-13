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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import java.util.List;

public interface ScalarReadingList {
  /**
   * Delivers all of the readings in this list, in order, to the given consumer, on the calling
   * thread.
   */
  void deliver(StreamConsumer c);

  /** Returns the size of the ScalarReadingList. */
  int size();

  /**
   * Converts the ScalarReadingList into a list of data points.
   *
   * @return The scalar reading list as a list of data points.
   */
  List<ChartData.DataPoint> asDataPoints();
}
