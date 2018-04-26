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

/** Creates a filter that applies two filters in order */
public class ComposeFilter {
  public static ValueFilter applyInOrder(final ValueFilter first, final ValueFilter second) {
    return new ValueFilter() {
      @Override
      public double filterValue(long timestamp, double value) {
        double firstValue = first.filterValue(timestamp, value);
        return second.filterValue(timestamp, firstValue);
      }
    };
  }
}
