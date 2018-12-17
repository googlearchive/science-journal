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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for Android Version Utils. */
@RunWith(RobolectricTestRunner.class)
public class AndroidVersionUtilsTest {
  @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
  @Test
  public void testIsApiLevel() {
    assertFalse(AndroidVersionUtils.isApiLevelAtLeast(Build.VERSION_CODES.CUR_DEVELOPMENT));
    assertFalse(AndroidVersionUtils.isApiLevelAtLeast(Build.VERSION_CODES.M));
  }

  @Config(sdk = Build.VERSION_CODES.O)
  @Test
  public void testAgainstOreo() {
    assertTrue(AndroidVersionUtils.isApiLevelAtLeastOreo());
    assertTrue(AndroidVersionUtils.isApiLevelAtLeastNougat());
    assertTrue(AndroidVersionUtils.isApiLevelAtLeastMarshmallow());
  }

  @Config(sdk = Build.VERSION_CODES.N)
  @Test
  public void testAgainstNougat() {
    assertFalse(AndroidVersionUtils.isApiLevelAtLeastOreo());
    assertTrue(AndroidVersionUtils.isApiLevelAtLeastNougat());
    assertTrue(AndroidVersionUtils.isApiLevelAtLeastMarshmallow());
  }

  @Config(sdk = Build.VERSION_CODES.M)
  @Test
  public void testAgainstMarshmallow() {
    assertFalse(AndroidVersionUtils.isApiLevelAtLeastOreo());
    assertFalse(AndroidVersionUtils.isApiLevelAtLeastNougat());
    assertTrue(AndroidVersionUtils.isApiLevelAtLeastMarshmallow());
  }

  @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
  @Test
  public void testAgainstLollipop() {
    assertFalse(AndroidVersionUtils.isApiLevelAtLeastOreo());
    assertFalse(AndroidVersionUtils.isApiLevelAtLeastNougat());
    assertFalse(AndroidVersionUtils.isApiLevelAtLeastMarshmallow());
  }
}
