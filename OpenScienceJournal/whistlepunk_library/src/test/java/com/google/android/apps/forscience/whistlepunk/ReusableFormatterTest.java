package com.google.android.apps.forscience.whistlepunk;

import static junit.framework.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for ResuableFormatter
 */
public class ReusableFormatterTest {
    @Test
    public void testReusingFormatter() {
        ReusableFormatter formatter = new ReusableFormatter();
        assertEquals("I am a cat", formatter.format("I am a %s", "cat").toString());
        assertEquals("I am still a cat", formatter.format("I am still a %s", "cat").toString());
        assertEquals("3:14.16", formatter.format("%1d:%02d.%2d", 3, 14, 16).toString());
    }
}
