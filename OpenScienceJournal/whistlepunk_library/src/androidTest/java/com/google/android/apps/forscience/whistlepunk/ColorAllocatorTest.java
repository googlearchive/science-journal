package com.google.android.apps.forscience.whistlepunk;

import android.test.AndroidTestCase;

/**
 * Tests for {@link ColorAllocator}.
 */
public class ColorAllocatorTest extends AndroidTestCase {

    public void testColor_simple() {
        ColorAllocator allocator = new ColorAllocator(new int[] {1, 2, 3});

        assertEquals(1, allocator.getNextColor(new int[] {}));
        assertEquals(2, allocator.getNextColor(new int[] {1}));
        assertEquals(3, allocator.getNextColor(new int[] {1, 2}));
    }

    public void testColor_startingPoint() {
        ColorAllocator allocator = new ColorAllocator(new int[] {1, 2, 3});

        assertEquals(2, allocator.getNextColor(new int[] {1, 3}));
        assertEquals(1, allocator.getNextColor(new int[] {1, 2, 3}));
    }


    public void testColor_full() {
        ColorAllocator allocator = new ColorAllocator(new int[] {1, 2, 3});

        assertEquals(1, allocator.getNextColor(new int[] {1, 2, 3}));
        assertEquals(2, allocator.getNextColor(new int[] {1, 2, 3, 1}));
        assertEquals(3, allocator.getNextColor(new int[] {1, 2, 3, 1, 2}));
        assertEquals(1, allocator.getNextColor(new int[] {1, 2, 3, 1, 2, 3}));
    }

    public void testColor_nullInput() {
        ColorAllocator allocator = new ColorAllocator(new int[] {1, 2, 3});

        assertEquals(1, allocator.getNextColor(null));
    }
}
