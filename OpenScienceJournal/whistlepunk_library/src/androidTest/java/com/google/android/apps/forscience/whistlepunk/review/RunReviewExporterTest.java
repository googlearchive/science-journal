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
