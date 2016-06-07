package com.google.android.apps.forscience.whistlepunk.audiogen.voices;

import android.test.AndroidTestCase;

import org.junit.Assert;

/**
 * Tests for PitchGenerator
 */
public class PitchGeneratorTest extends AndroidTestCase {
    public void testPitchGenerator_testPitchExtrema() {
        int scale[] = {
                0 /* C */, 2 /* D */, 4 /* E */, 7 /* G */, 9 /* A */ }; // pentatonic scale

        int pitchMin = 71; // C5
        int pitchMax = 92; // A6
        int[] pitches = PitchGenerator.generatePitches(scale, pitchMin, pitchMax);

        int[] expected = {71, 73, 75, 78, 80, 83, 85, 87, 90, 92};
        Assert.assertArrayEquals(expected, pitches);
    }

    public void testPitchGenerator_testPitchMaxNotInScale() {
        int scale[] = {
                0 /* C */, 2 /* D */, 4 /* E */, 7 /* G */, 9 /* A */ }; // pentatonic scale

        int pitchMin = 71; // C5
        int pitchMax = 93; // A#6, note is not in scale.
        int[] pitches = PitchGenerator.generatePitches(scale, pitchMin, pitchMax);

        int[] expected = {71, 73, 75, 78, 80, 83, 85, 87, 90, 92};
        Assert.assertArrayEquals(expected, pitches);
    }
}
