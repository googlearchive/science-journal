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

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import static org.junit.Assert.assertEquals;

import android.os.Bundle;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScalarDisplayOptionsTest {
  @Test
  public void testAsBundle() {
    final ScalarDisplayOptions options = new ScalarDisplayOptions();
    float smoothness = Arbitrary.singleFloat();
    int window = Arbitrary.integer();
    int blurType = ScalarDisplayOptions.BLUR_TYPE_AVERAGE;
    float sigma = Arbitrary.singleFloat();

    options.updateLineSettings(smoothness, window, blurType, sigma);
    final Bundle bundle = options.asBundle();

    assertEquals(smoothness, bundle.getFloat(ScalarDisplayOptions.PREFS_KEY_SMOOTHNESS), 0.1f);
    assertEquals(window, bundle.getInt(ScalarDisplayOptions.PREFS_KEY_WINDOW));
    assertEquals(blurType, bundle.getInt(ScalarDisplayOptions.PREFS_KEY_BLUR_TYPE));
    assertEquals(sigma, bundle.getFloat(ScalarDisplayOptions.PREFS_KEY_GAUSSIAN_SIGMA), 0.1f);
  }
}
