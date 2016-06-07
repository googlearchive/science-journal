package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.test.AndroidTestCase;

public class GraphOptionsManagerTest extends AndroidTestCase {

    public void testGetProgressFromSmoothness() {
        assertEquals(0, GraphOptionsManager.getProgressFromSmoothness(0, 100));
        assertEquals(100, GraphOptionsManager.getProgressFromSmoothness(
                ScalarDisplayOptions.SMOOTHNESS_MAX, 100));
        assertEquals(50, GraphOptionsManager.getProgressFromSmoothness(
                (ScalarDisplayOptions.SMOOTHNESS_MIN + ScalarDisplayOptions.SMOOTHNESS_MAX) / 2f,
                100));
    }

    public void testGetSmoothnessFromProgress() {
        assertEquals(ScalarDisplayOptions.SMOOTHNESS_MAX,
                GraphOptionsManager.getSmoothnessFromProgress(100, 100));
        assertEquals(ScalarDisplayOptions.SMOOTHNESS_MIN,
                GraphOptionsManager.getSmoothnessFromProgress(0, 100));
        assertEquals((ScalarDisplayOptions.SMOOTHNESS_MIN + ScalarDisplayOptions.SMOOTHNESS_MAX) / 2f,
                GraphOptionsManager.getSmoothnessFromProgress(50, 100));
    }

    public void testGetProgressFromGaussianSigma() {
        assertEquals(0, GraphOptionsManager.getProgressFromGaussianSigma(
                ScalarDisplayOptions.GAUSSIAN_SIGMA_MIN, 100));
        assertEquals(100, GraphOptionsManager.getProgressFromGaussianSigma(
                ScalarDisplayOptions.GAUSSIAN_SIGMA_MAX, 100));
        assertEquals(50, GraphOptionsManager.getProgressFromGaussianSigma(
                (ScalarDisplayOptions.GAUSSIAN_SIGMA_MIN + ScalarDisplayOptions.GAUSSIAN_SIGMA_MAX) /
                        2.0f, 100));
    }

    public void testGetGaussianSigmaFromProgress() {
        assertEquals(ScalarDisplayOptions.GAUSSIAN_SIGMA_MAX,
                GraphOptionsManager.getGaussianSigmaFromProgress(100, 100));
        assertEquals(ScalarDisplayOptions.GAUSSIAN_SIGMA_MIN,
                GraphOptionsManager.getGaussianSigmaFromProgress(0, 100));
        assertEquals((ScalarDisplayOptions.GAUSSIAN_SIGMA_MIN +
                        ScalarDisplayOptions.GAUSSIAN_SIGMA_MAX) / 2f,
                GraphOptionsManager.getGaussianSigmaFromProgress(50, 100));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }
}
