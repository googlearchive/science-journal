package com.google.android.apps.forscience.whistlepunk.scalarchart;


import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.TextLabel;

import java.util.ArrayList;
import java.util.List;

public class ChartDataTest extends AndroidTestCase {

    private void populatePointsList(int size, List<ChartData.DataPoint> result) {
        for (int i = 0; i < size; i++) {
            result.add(new ChartData.DataPoint(i, i / 10.0));
        }
    }

    private void assertDataEquals(List<ChartData.DataPoint> expected,
            List<ChartData.DataPoint> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertDataPointEquals(expected.get(i), actual.get(i));
        }
    }

    private void assertDataPointEquals(ChartData.DataPoint expected, ChartData.DataPoint actual) {
        assertEquals(expected.getX(), actual.getX());
        assertEquals(expected.getY(), actual.getY(), .000001);
    }

    private void assertWithinRange(int startRange, int endRange, int result) {
        assertTrue(startRange <= result && result <= endRange);
    }

    public void testExactBinarySearch_sizeOne() {
        ChartData chartData = new ChartData();
        populatePointsList(1, chartData.getPoints());
        assertEquals(0, chartData.exactBinarySearch(0, 0));
    }

    public void testExactBinarySearch_sizeFive() {
        ChartData chartData = new ChartData();
        populatePointsList(5, chartData.getPoints());
        assertEquals(0, chartData.exactBinarySearch(0, 0));
        assertEquals(4, chartData.exactBinarySearch(4, 0));
    }

    public void testApproximateBinarySearch_sizeFiveApproxMid() {
        // This chartData's approx range is 2 with a dataset size 5.
        ChartData chartData = new ChartData();
        populatePointsList(5, chartData.getPoints());
        assertEquals(0, chartData.approximateBinarySearch(1, 0, 4, true, 3));
        assertWithinRange(0, 3, chartData.approximateBinarySearch(3, 0, 4, true, 3));
        assertWithinRange(0, 3, chartData.approximateBinarySearch(1, 0, 4, false, 3));
        assertWithinRange(3, 6, chartData.approximateBinarySearch(3, 0, 4, false, 3));
    }

    public void testApproximateBinarySearch_sizeFiveApproxBig() {
        // This chartData has a larger approx range than data size, so this is a test
        // of preferStart and ranges.
        ChartData chartData = new ChartData();
        populatePointsList(5, chartData.getPoints());
        assertEquals(0, chartData.approximateBinarySearch(1, 0, 4, true, 10));
        assertEquals(4, chartData.approximateBinarySearch(1, 0, 4, false, 10));
    }

    public void testApproximateBinarySearch_sizeOneHundredApprox() {
        ChartData chartData = new ChartData();
        populatePointsList(100, chartData.getPoints());

        for (int i = 5; i < 99; i += 10) {
            int result = chartData.approximateBinarySearch(i, 0, 99, true, 10);
            assertWithinRange(i - 10, i, result);
        }
    }

    public void testGetPointsInRange_sizeOne() {
        ChartData chartData = new ChartData();
        List<ChartData.DataPoint> data = new ArrayList<>();
        populatePointsList(1, data);
        chartData.setPoints(data);

        List<ChartData.DataPoint> expected = new ArrayList<>();
        populatePointsList(1, expected);
        assertDataEquals(expected, chartData.getPointsInRange(0, 1));
    }

    public void testGetPointsInRange_sizeThree() {
        ChartData chartData = new ChartData();
        List<ChartData.DataPoint> data = new ArrayList<>();
        populatePointsList(3, data);
        chartData.setPoints(data);

        List<ChartData.DataPoint> expected = new ArrayList<>();
        populatePointsList(3, expected);
        assertDataEquals(expected, chartData.getPointsInRange(0, 1));
    }

    public void testTryAddingLabel_tooSmall() {
        ChartData chartData = new ChartData();
        List<ChartData.DataPoint> data = new ArrayList<>();
        populatePointsList(3, data);
        chartData.setPoints(data);

        Label label = new TextLabel("", "-1", "-1", -1);
        assertFalse(chartData.tryAddingLabel(label));
        assertEquals(chartData.getLabelPoints().size(), 0);
    }

    public void testTryAddingLabel_tooBig() {
        ChartData chartData = new ChartData();
        List<ChartData.DataPoint> data = new ArrayList<>();
        populatePointsList(3, data);
        chartData.setPoints(data);

        Label label = new TextLabel("", "3", "-1", 3);
        assertFalse(chartData.tryAddingLabel(label));
        assertEquals(chartData.getLabelPoints().size(), 0);
    }

    public void testTryAddingLabel_exactMatch() {
        ChartData chartData = new ChartData();
        List<ChartData.DataPoint> data = new ArrayList<>();
        populatePointsList(3, data);
        chartData.setPoints(data);

        Label label = new TextLabel("", "1", "-1", 1);
        assertTrue(chartData.tryAddingLabel(label));
        List<ChartData.DataPoint> labelPoints = chartData.getLabelPoints();
        assertEquals(labelPoints.size(), 1);
    }
}