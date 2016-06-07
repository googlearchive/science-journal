package com.google.android.apps.forscience.whistlepunk;

import android.os.Bundle;

import junit.framework.Assert;

import java.util.HashSet;
import java.util.Set;

public class BundleAssert {
    static void assertBundlesEqual(Bundle a, Bundle b) {
        final Set<String> akeys = a.keySet();
        final Set<String> bkeys = b.keySet();
        Assert.assertEquals(new HashSet(akeys), new HashSet(bkeys));

        for (String key : akeys) {
            Assert.assertEquals(a.get(key), b.get(key));
        }
    }
}
