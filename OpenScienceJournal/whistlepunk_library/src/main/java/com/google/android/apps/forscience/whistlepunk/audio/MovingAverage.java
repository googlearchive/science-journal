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

package com.google.android.apps.forscience.whistlepunk.audio;

/** Computes moving average using a circular buffer. */
class MovingAverage {
  private final int bufferSize;
  private final double[] buffer;
  private double sum;
  private int size;
  private int next;

  MovingAverage(int size) {
    bufferSize = size;
    buffer = new double[bufferSize];
  }

  /** Clears this MovingAverage of previous entries. */
  void clear() {
    sum = 0;
    size = 0;
    next = 0;
  }

  /** Inserts the given number and returns the moving average. */
  double insertAndReturnAverage(double n) {
    if (size == bufferSize) {
      double removed = buffer[next];
      sum = sum - removed;
    }
    buffer[next] = n;
    sum += n;
    next = (next + 1) % bufferSize;
    if (size < bufferSize) {
      size++;
    }
    return sum / size;
  }
}
