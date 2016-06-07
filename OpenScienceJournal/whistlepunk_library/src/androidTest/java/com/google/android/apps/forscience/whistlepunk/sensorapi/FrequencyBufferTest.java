package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.test.AndroidTestCase;

public class FrequencyBufferTest extends AndroidTestCase {
    public void testTwenty() {
        final FrequencyBuffer buffer = new FrequencyBuffer(100, 1000.0, 0.0);
        buffer.filterValue(0, 0);
        buffer.filterValue(25, 1);
        buffer.filterValue(50, 0);
        buffer.filterValue(75, 1);
        buffer.filterValue(100, 0);
        assertEquals(20.0, buffer.getLatestFrequency(), 0.01);
    }

    public void testRestrictWindowToCrosses() {
        final FrequencyBuffer buffer = new FrequencyBuffer(100, 1000.0, 0.0);
        buffer.filterValue(0, 0);
        buffer.filterValue(10, 1);
        buffer.filterValue(35, 0);
        buffer.filterValue(60, 1);
        buffer.filterValue(85, 0);
        assertEquals(20.0, buffer.getLatestFrequency(), 0.01);
        buffer.filterValue(90, 0);
        buffer.filterValue(95, 0);
        buffer.filterValue(100, 0);
        assertEquals(20.0, buffer.getLatestFrequency(), 0.01);
    }
    
    public void testTwentyAnotherDataPoint() {
        final FrequencyBuffer buffer = new FrequencyBuffer(100, 1000.0, 0.0);
        buffer.filterValue(0, 0);
        buffer.filterValue(1, 0);
        buffer.filterValue(25, 1);
        buffer.filterValue(50, 0);
        buffer.filterValue(75, 1);
        buffer.filterValue(100, 0);
        assertEquals(20.0, buffer.getLatestFrequency(), 0.01);
    }

    public void testTen() {
        final FrequencyBuffer buffer = new FrequencyBuffer(100, 1000.0, 0.0);
        buffer.filterValue(0, 0);
        buffer.filterValue(50, 1);
        buffer.filterValue(100, 0);
        assertEquals(10.0, buffer.getLatestFrequency(), 0.01);
    }

    public void testTenFiltered() {
        final FrequencyBuffer buffer = new FrequencyBuffer(100, 1000.0, 4.0);
        buffer.filterValue(0, 0);

        // noise below the filter
        buffer.filterValue(1, 3);
        buffer.filterValue(2, 0);
        buffer.filterValue(3, 3);
        buffer.filterValue(4, 0);

        buffer.filterValue(50, 10);
        buffer.filterValue(100, 0);
        assertEquals(10.0, buffer.getLatestFrequency(), 0.01);
        buffer.changeFilter(0);
        assertEquals(25.25, buffer.getLatestFrequency(), 0.01);
    }

    public void testRpm() {
        final FrequencyBuffer buffer = new FrequencyBuffer(100, 60000.0, 0.0);
        buffer.filterValue(0, 0);
        buffer.filterValue(50, 1);
        buffer.filterValue(100, 0);
        assertEquals(600.0, buffer.getLatestFrequency(), 0.01);
    }

    public void testChangeWindow() {
        final FrequencyBuffer buffer = new FrequencyBuffer(200, 1000.0, 0.0);
        buffer.filterValue(0, 0);
        buffer.filterValue(50, 1);
        buffer.filterValue(100, 0);
        buffer.filterValue(125, 1);
        buffer.filterValue(150, 0);
        buffer.filterValue(175, 1);
        buffer.filterValue(200, 0);
        assertEquals(16.66, buffer.getLatestFrequency(), 0.01);
        buffer.changeWindow(100);
        assertEquals(20.0, buffer.getLatestFrequency(), 0.01);
    }

    public void testPruneAsWeGo() {
        final FrequencyBuffer buffer = new FrequencyBuffer(100, 1000.0, 0.0);
        buffer.filterValue(0, 0);
        buffer.filterValue(25, 1);
        buffer.filterValue(50, 0);
        buffer.filterValue(75, 1);
        buffer.filterValue(100, 0);
        buffer.filterValue(150, 1);
        buffer.filterValue(200, 0);
        assertEquals(10, buffer.getLatestFrequency(), 0.01);
    }

    public void testIgnoreNearlyEmptyWindows() {
        final FrequencyBuffer buffer = new FrequencyBuffer(100, 1000.0, 0.0);
        buffer.filterValue(0, 0);
        buffer.filterValue(10, 1);
        buffer.filterValue(20, 0);
        buffer.filterValue(99, 0);
        assertEquals(0, buffer.getLatestFrequency(), 0.01);
    }

    public void testEmpty() {
        assertEquals(0.0, new FrequencyBuffer(200, 1000.0, 0.0).getLatestFrequency());
    }

    public void testSingleton() {
        final FrequencyBuffer buffer = new FrequencyBuffer(200, 1000.0, 0.0);
        buffer.filterValue(0, 0);
        assertEquals(0.0, buffer.getLatestFrequency());
    }

    public void testChangeWindowEmptyBuffer() {
        final FrequencyBuffer buffer = new FrequencyBuffer(200, 1000.0, 0.0);
        buffer.changeWindow(200);
        // Just don't crash
    }
}
