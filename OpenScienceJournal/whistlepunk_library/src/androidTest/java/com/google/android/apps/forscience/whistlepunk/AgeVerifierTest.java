package com.google.android.apps.forscience.whistlepunk;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;

import java.util.Calendar;

/**
 * Tests for {@link com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier}.
 */
public class AgeVerifierTest extends AndroidTestCase {

    public void testOver13_withSentinel() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(1980, 1, 1);

        assertTrue(AgeVerifier.isOver13(calendar.getTimeInMillis()));
    }

    public void testOver13_withDynamic() {
        Calendar nowCalendar = Calendar.getInstance();

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        assertFalse(AgeVerifier.isOver13(calendar.getTimeInMillis()));

        calendar.set(Calendar.YEAR, nowCalendar.get(Calendar.YEAR) - 12);
        assertFalse(AgeVerifier.isOver13(calendar.getTimeInMillis()));

        calendar.set(Calendar.YEAR, nowCalendar.get(Calendar.YEAR) - 13);
        assertTrue(AgeVerifier.isOver13(calendar.getTimeInMillis()));
        calendar.add(Calendar.HOUR, 1);
        assertFalse(AgeVerifier.isOver13(calendar.getTimeInMillis()));
        calendar.set(Calendar.YEAR, nowCalendar.get(Calendar.YEAR) - 13);
        calendar.add(Calendar.HOUR, -1);
        assertTrue(AgeVerifier.isOver13(calendar.getTimeInMillis()));

        calendar.set(Calendar.YEAR, nowCalendar.get(Calendar.YEAR) - 14);
        assertTrue(AgeVerifier.isOver13(calendar.getTimeInMillis()));
    }
}
