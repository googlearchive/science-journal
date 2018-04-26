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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link ColorAllocator}. */
@RunWith(RobolectricTestRunner.class)
public class ColorAllocatorTest {

  @Test
  public void testColor_simple() {
    ColorAllocator allocator = new ColorAllocator(3);

    assertEquals(0, allocator.getNextColor(new int[] {}));
    assertEquals(1, allocator.getNextColor(new int[] {0}));
    assertEquals(2, allocator.getNextColor(new int[] {0, 1}));
  }

  @Test
  public void testColor_startingPoint() {
    ColorAllocator allocator = new ColorAllocator(3);

    assertEquals(2, allocator.getNextColor(new int[] {1, 0}));
    assertEquals(0, allocator.getNextColor(new int[] {1, 2, 0}));
  }

  @Test
  public void testColor_full() {
    ColorAllocator allocator = new ColorAllocator(3);

    assertEquals(0, allocator.getNextColor(new int[] {0, 1, 2}));
    assertEquals(1, allocator.getNextColor(new int[] {0, 1, 2, 0}));
    assertEquals(2, allocator.getNextColor(new int[] {0, 1, 2, 0, 1}));
    assertEquals(0, allocator.getNextColor(new int[] {0, 1, 2, 0, 1, 2}));
  }

  @Test
  public void testColor_nullInput() {
    ColorAllocator allocator = new ColorAllocator(3);

    assertEquals(0, allocator.getNextColor(null));
  }
}
