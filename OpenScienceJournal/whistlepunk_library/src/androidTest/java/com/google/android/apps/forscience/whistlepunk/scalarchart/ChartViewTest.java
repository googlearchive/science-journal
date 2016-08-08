package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.test.AndroidTestCase;

import java.util.Arrays;
import java.util.List;


public class ChartViewTest extends AndroidTestCase {
    private List<Double> initPoints() {
        return Arrays.asList(0., 2., 4., 6., 8., 10.);
    }

    public void testcalculateSizeShownNext_noPointsShown() {
        List<Double> points = initPoints();
        assertEquals(0, ChartView.calculateSizeShownNext(points, 2, 4));
    }

    public void testcalculateSizeShownNext_allPointsFitPerfectly() {
        List<Double> points = initPoints();
        assertEquals(6, ChartView.calculateSizeShownNext(points, -1, 11));
    }

    public void testCalculateSizeShownNext_somePointsFit() {
        List<Double> points = initPoints();
        assertEquals(4, ChartView.calculateSizeShownNext(points, 1, 9));
        assertEquals(2, ChartView.calculateSizeShownNext(points, 1, 5));
    }

    public void testCalculateSizeShownNext_offCenter() {
        List<Double> points = initPoints();
        assertEquals(4, ChartView.calculateSizeShownNext(points, -5, 3));
        assertEquals(4, ChartView.calculateSizeShownNext(points, 7, 15));
    }

    public void testCalculateSizeShownNext_zoomOut() {
        List<Double> points = initPoints();
        assertEquals(20, ChartView.calculateSizeShownNext(points, 1, 41));
    }
}
