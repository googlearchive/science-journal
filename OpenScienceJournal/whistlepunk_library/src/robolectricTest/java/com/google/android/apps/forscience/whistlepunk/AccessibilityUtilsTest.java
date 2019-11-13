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

import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for Accessibility Utils. */
@RunWith(RobolectricTestRunner.class)
public class AccessibilityUtilsTest {
  @Test
  public void testResizeRect_correctSize() {
    Rect testRect = new Rect(0, 0, 48, 48);
    Rect expected = new Rect(0, 0, 48, 48);
    AccessibilityUtils.resizeRect(48, testRect);
    assertTrue(testRect.equals(expected));
  }

  @Test
  public void testResizeRect_biggerSize() {
    Rect testRect = new Rect(0, 0, 100, 100);
    Rect expected = new Rect(0, 0, 100, 100);
    AccessibilityUtils.resizeRect(48, testRect);
    assertTrue(testRect.equals(expected));
  }

  @Test
  public void testResizeRect_smallerWidth() {
    Rect testRect = new Rect(0, 0, 30, 48);
    Rect expected = new Rect(-9, 0, 39, 48);
    AccessibilityUtils.resizeRect(48, testRect);
    assertTrue(testRect.equals(expected));
  }

  @Test
  public void testResizeRect_smallerHeight() {
    Rect testRect = new Rect(0, 0, 48, 30);
    Rect expected = new Rect(0, -9, 48, 39);
    AccessibilityUtils.resizeRect(48, testRect);
    assertTrue(testRect.equals(expected));
  }

  @Test
  public void testResizeRect_smallerBoth() {
    Rect testRect = new Rect(0, 0, 30, 30);
    Rect expected = new Rect(-9, -9, 39, 39);
    AccessibilityUtils.resizeRect(48, testRect);
    assertTrue(testRect.equals(expected));
  }

  @Test
  public void testResizeRect_roundsUp() {
    Rect testRect = new Rect(0, 0, 47, 48);
    Rect expected = new Rect(-1, 0, 48, 48);
    AccessibilityUtils.resizeRect(48, testRect);
    assertTrue(testRect.equals(expected));
  }
}
