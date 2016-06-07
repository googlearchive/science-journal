package com.google.android.apps.forscience.whistlepunk;

import android.graphics.Rect;
import android.test.AndroidTestCase;

/**
 * Tests for Accessibility Utils.
 */
public class AccessibilityUtilsTest extends AndroidTestCase {
    public void testResizeRect_correctSize() {
        Rect testRect = new Rect(0, 0, 48, 48);
        Rect expected = new Rect(0, 0, 48, 48);
        AccessibilityUtils.resizeRect(48, testRect);
        assertTrue(testRect.equals(expected));
    }

    public void testResizeRect_biggerSize() {
        Rect testRect = new Rect(0, 0, 100, 100);
        Rect expected = new Rect(0, 0, 100, 100);
        AccessibilityUtils.resizeRect(48, testRect);
        assertTrue(testRect.equals(expected));
    }

    public void testResizeRect_smallerWidth() {
        Rect testRect = new Rect(0, 0, 30, 48);
        Rect expected = new Rect(-9, 0, 39, 48);
        AccessibilityUtils.resizeRect(48, testRect);
        assertTrue(testRect.equals(expected));
    }

    public void testResizeRect_smallerHeight() {
        Rect testRect = new Rect(0, 0, 48, 30);
        Rect expected = new Rect(0, -9, 48, 39);
        AccessibilityUtils.resizeRect(48, testRect);
        assertTrue(testRect.equals(expected));
    }

    public void testResizeRect_smallerBoth() {
        Rect testRect = new Rect(0, 0, 30, 30);
        Rect expected = new Rect(-9, -9, 39, 39);
        AccessibilityUtils.resizeRect(48, testRect);
        assertTrue(testRect.equals(expected));
    }

    public void testResizeRect_roundsUp() {
        Rect testRect = new Rect(0, 0, 47, 48);
        Rect expected = new Rect(-1, 0, 48, 48);
        AccessibilityUtils.resizeRect(48, testRect);
        assertTrue(testRect.equals(expected));
    }
}
