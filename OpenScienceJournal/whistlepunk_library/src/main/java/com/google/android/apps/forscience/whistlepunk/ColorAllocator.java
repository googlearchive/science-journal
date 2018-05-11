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

/**
 * Stores a list of master colors and provides a way to get the next color that should be picked
 * from the master list given a list of already taken colors.
 */
public class ColorAllocator {

  private final int[] colorIndexCounts;

  public ColorAllocator(int listSize) {
    colorIndexCounts = new int[listSize];
  }

  /**
   * Gets the next color that is least used from the master color array, based on the input used
   * color array.
   */
  public int getNextColor(int[] usedColorIndexes) {
    if (usedColorIndexes == null || usedColorIndexes.length == 0) {
      return 0;
    }
    // Zero out the counts
    for (int i = 0; i < colorIndexCounts.length; i++) {
      colorIndexCounts[i] = 0;
    }
    // Count up the used color indexes
    for (int index : usedColorIndexes) {
      colorIndexCounts[index]++;
    }
    int leastUsedCount = Integer.MAX_VALUE;
    int leastUsedIndex = -1;

    // Now start at the 0th index and move forward, keeping track of the smallest count item.
    for (int index = 0; index < colorIndexCounts.length; index++) {
      if (colorIndexCounts[index] < leastUsedCount) {
        leastUsedIndex = index;
        leastUsedCount = colorIndexCounts[index];
      }
    }
    return leastUsedIndex;
  }
}
