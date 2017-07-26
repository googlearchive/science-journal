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

package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;

/**
 * Tests for {@link com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class AgeVerifierTest {
    @Test
    public void testOver13_withSentinel() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(1980, 1, 1);

        assertTrue(AgeVerifier.isOver13(calendar.getTimeInMillis()));
    }

    @Test
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
