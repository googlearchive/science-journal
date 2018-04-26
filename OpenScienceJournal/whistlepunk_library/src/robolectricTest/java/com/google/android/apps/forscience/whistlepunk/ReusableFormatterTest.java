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

import static junit.framework.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for ResuableFormatter */
@RunWith(RobolectricTestRunner.class)
public class ReusableFormatterTest {
  @Test
  public void testReusingFormatter() {
    ReusableFormatter formatter = new ReusableFormatter();
    assertEquals("I am a cat", formatter.format("I am a %s", "cat").toString());
    assertEquals("I am still a cat", formatter.format("I am still a %s", "cat").toString());
    assertEquals("3:14.16", formatter.format("%1d:%02d.%2d", 3, 14, 16).toString());
  }
}
