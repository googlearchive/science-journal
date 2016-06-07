package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.os.Bundle;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;

public class ScalarDisplayOptionsTest extends AndroidTestCase {
    public void testAsBundle() {
        final ScalarDisplayOptions options = new ScalarDisplayOptions();
        float smoothness = Arbitrary.singleFloat();
        int window = Arbitrary.integer();
        int blurType = Arbitrary.integer();
        float sigma = Arbitrary.singleFloat();

        options.updateLineSettings(smoothness, window, blurType, sigma);
        final Bundle bundle = options.asBundle();

        assertEquals(smoothness, bundle.getFloat(ScalarDisplayOptions.PREFS_KEY_SMOOTHNESS), 0.1f);
        assertEquals(window, bundle.getInt(ScalarDisplayOptions.PREFS_KEY_WINDOW));
        assertEquals(blurType, bundle.getInt(ScalarDisplayOptions.PREFS_KEY_BLUR_TYPE));
        assertEquals(sigma, bundle.getFloat(ScalarDisplayOptions.PREFS_KEY_GAUSSIAN_SIGMA), 0.1f);
    }
}
