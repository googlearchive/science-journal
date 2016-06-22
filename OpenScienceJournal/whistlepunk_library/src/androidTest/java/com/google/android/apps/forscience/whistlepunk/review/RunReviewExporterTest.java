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

package com.google.android.apps.forscience.whistlepunk.review;

import android.test.AndroidTestCase;

/**
 * Tests for {@link RunReviewExporter}.
 */
public class RunReviewExporterTest extends AndroidTestCase {


    public void testSanitizeFileName_normal() {
        String experimentName = "Untitled Experiment";
        String runName = "Run 1";

        assertEquals("Untitled Experiment Run 1", RunReviewExporter.sanitizeFilename(
                experimentName + " " + runName));
    }

    public void testSanitizeFileName_evil() {
        String experimentName;
        String runName;
        experimentName = "//Untitled Experiment";
        runName = "Run 1";

        assertEquals("__Untitled Experiment Run 1", RunReviewExporter.sanitizeFilename(
                experimentName + " " + runName));

        experimentName = "U.nti\tled Ex. periment";
        runName = "Run 10";

        assertEquals("U.nti_led Ex. periment Run 10", RunReviewExporter.sanitizeFilename(
                experimentName + " " + runName));
    }
}
