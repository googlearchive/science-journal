package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;

public class ValueFilterTest extends AndroidTestCase {
    public void testIdentity() {
        double value = Arbitrary.doubleFloat();
        long now = Arbitrary.longInteger();
        assertEquals(value, ValueFilter.IDENTITY.filterValue(now, value));
    }
}