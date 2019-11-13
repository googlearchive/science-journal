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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GraphOptionsManagerTest {
  private final double DELTA = 0.01;

  @Test
  public void testGetProgressFromSmoothness() {
    assertEquals(0, GraphOptionsManager.getProgressFromSmoothness(0, 100));
    assertEquals(
        100,
        GraphOptionsManager.getProgressFromSmoothness(ScalarDisplayOptions.SMOOTHNESS_MAX, 100));
    assertEquals(
        50,
        GraphOptionsManager.getProgressFromSmoothness(
            (ScalarDisplayOptions.SMOOTHNESS_MIN + ScalarDisplayOptions.SMOOTHNESS_MAX) / 2f, 100));
  }

  @Test
  public void testGetSmoothnessFromProgress() {
    assertEquals(
        ScalarDisplayOptions.SMOOTHNESS_MAX,
        GraphOptionsManager.getSmoothnessFromProgress(100, 100),
        DELTA);
    assertEquals(
        ScalarDisplayOptions.SMOOTHNESS_MIN,
        GraphOptionsManager.getSmoothnessFromProgress(0, 100),
        DELTA);
    assertEquals(
        (ScalarDisplayOptions.SMOOTHNESS_MIN + ScalarDisplayOptions.SMOOTHNESS_MAX) / 2f,
        GraphOptionsManager.getSmoothnessFromProgress(50, 100),
        DELTA);
  }

  @Test
  public void testGetProgressFromGaussianSigma() {
    assertEquals(
        0,
        GraphOptionsManager.getProgressFromGaussianSigma(
            ScalarDisplayOptions.GAUSSIAN_SIGMA_MIN, 100));
    assertEquals(
        100,
        GraphOptionsManager.getProgressFromGaussianSigma(
            ScalarDisplayOptions.GAUSSIAN_SIGMA_MAX, 100));
    assertEquals(
        50,
        GraphOptionsManager.getProgressFromGaussianSigma(
            (ScalarDisplayOptions.GAUSSIAN_SIGMA_MIN + ScalarDisplayOptions.GAUSSIAN_SIGMA_MAX)
                / 2.0f,
            100));
  }

  @Test
  public void testGetGaussianSigmaFromProgress() {
    assertEquals(
        ScalarDisplayOptions.GAUSSIAN_SIGMA_MAX,
        GraphOptionsManager.getGaussianSigmaFromProgress(100, 100),
        DELTA);
    assertEquals(
        ScalarDisplayOptions.GAUSSIAN_SIGMA_MIN,
        GraphOptionsManager.getGaussianSigmaFromProgress(0, 100),
        DELTA);
    assertEquals(
        (ScalarDisplayOptions.GAUSSIAN_SIGMA_MIN + ScalarDisplayOptions.GAUSSIAN_SIGMA_MAX) / 2f,
        GraphOptionsManager.getGaussianSigmaFromProgress(50, 100),
        DELTA);
  }
}
