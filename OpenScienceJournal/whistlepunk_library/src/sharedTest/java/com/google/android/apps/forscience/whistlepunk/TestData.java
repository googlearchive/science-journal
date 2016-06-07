package com.google.android.apps.forscience.whistlepunk;

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.common.collect.Lists;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class TestData {
    private static final double DELTA = 0.01;

    @NonNull
    public static TestData allPointsBetween(int min, int max, int step) {
        TestData data = new TestData();
        for (int i = min; i <= max; i += step) {
            data.addPoint(i, i);
        }
        return data;
    }

    private static class Point {
        long x;
        double y;

        public Point(long x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "Point{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }
    }

    private List<Point> mPoints = new ArrayList<>();

    public TestData addPoint(long x, double y) {
        mPoints.add(new Point(x, y));
        return this;
    }

    public void checkContents(SortedMap<Double, Double> map) {
        final Iterator<Point> iterator = mPoints.iterator();
        TreeMap<Long, Double> compressedRawData = new TreeMap<>();
        for (Map.Entry<Double, Double> entry : map.entrySet()) {
            // rawData (due to other bugs) can have duplicate entries, which aren't important to
            // us here.
            compressedRawData.put((long)(double)entry.getKey(), entry.getValue());
        }

        checkCompressedData(compressedRawData);
    }

    public void checkObserver(RecordingSensorObserver observer) {
        TreeMap<Long, Double> compressedRawData = new TreeMap<>();
        for (ScalarReading sr : observer.getReadings()) {
            compressedRawData.put(sr.getCollectedTimeMillis(), sr.getValue());
        }

        checkCompressedData(compressedRawData);

    }

    public void checkRawData(List<GraphData.ReadonlyDataPoint> rawData) {
        TreeMap<Long, Double> compressedRawData = new TreeMap<>();
        for (GraphData.ReadonlyDataPoint readonlyDataPoint : rawData) {
            compressedRawData.put(readonlyDataPoint.getX(), readonlyDataPoint.getY());
        }

        checkCompressedData(compressedRawData);
    }

    protected void checkCompressedData(TreeMap<Long, Double> compressedRawData) {
        final Iterator<Point> iterator = mPoints.iterator();

        for (Map.Entry<Long, Double> entry : compressedRawData.entrySet()) {
            if (!iterator.hasNext()) {
                Assert.fail("Expected fewer values in " + compressedRawData);
            }
            final Point expected = iterator.next();
            Assert.assertEquals(expected.x, (long)entry.getKey());
            Assert.assertEquals(expected.y, entry.getValue(), DELTA);
        }

        if (iterator.hasNext()) {
            Assert.fail("Expected another value: " + iterator.next() + " in " + compressedRawData);
        }
    }

    public List<GraphData.ReadonlyDataPoint> asRawData() {
        List<GraphData.ReadonlyDataPoint> rawData = Lists.newArrayList();
        for (Point point : mPoints) {
            rawData.add(new GraphData.ReadonlyDataPoint(point.x, point.y));
        }
        return rawData;
    }
}
