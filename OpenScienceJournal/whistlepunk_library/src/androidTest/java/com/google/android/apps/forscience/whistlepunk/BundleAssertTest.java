package com.google.android.apps.forscience.whistlepunk;

import android.os.Bundle;
import android.test.AndroidTestCase;

public class BundleAssertTest extends AndroidTestCase {
    public void testAssertBundlesEqualMissingKey() {
        try {
            final Bundle hasKey = new Bundle();
            hasKey.putString("key", "value");
            final Bundle noKey = new Bundle();
            BundleAssert.assertBundlesEqual(hasKey, noKey);
        } catch (AssertionError expected) {
            return;
        }
        fail("Should have failed!");
    }

    public void testAssertBundlesEqualDifferentValues() {
        try {
            final Bundle a = new Bundle();
            a.putString("key", "a");
            final Bundle b = new Bundle();
            b.putString("key", "b");
            BundleAssert.assertBundlesEqual(a, b);
        } catch (AssertionError expected) {
            return;
        }
        fail("Should have failed!");
    }
}
