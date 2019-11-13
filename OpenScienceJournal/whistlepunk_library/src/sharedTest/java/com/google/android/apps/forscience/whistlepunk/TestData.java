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

import androidx.annotation.NonNull;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import junit.framework.Assert;

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

  public static TestData fromPoints(List<ChartData.DataPoint> points) {
    TestData data = new TestData();
    for (ChartData.DataPoint point : points) {
      data.addPoint(point.getX(), point.getY());
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
      return "Point{" + "x=" + x + ", y=" + y + '}';
    }
  }

  private List<Point> points = new ArrayList<>();

  public TestData addPoint(long x, double y) {
    points.add(new Point(x, y));
    return this;
  }

  public void checkObserver(RecordingSensorObserver observer) {
    ArrayList<ChartData.DataPoint> rawData = new ArrayList<>();
    for (ScalarReading sr : observer.getReadings()) {
      rawData.add(new ChartData.DataPoint(sr.getCollectedTimeMillis(), sr.getValue()));
    }

    checkRawData(rawData);
  }

  public void checkRawData(List<ChartData.DataPoint> rawData) {
    removeDupes(rawData);
    final Iterator<Point> iterator = points.iterator();

    for (ChartData.DataPoint entry : rawData) {
      // rawData (due to other bugs) can have duplicate entries, which aren't important to
      // us here.
      if (!iterator.hasNext()) {
        Assert.fail("Expected fewer values in " + rawData);
      }
      final Point expected = iterator.next();
      Assert.assertEquals(expected.x, entry.getX());
      Assert.assertEquals(expected.y, entry.getY(), DELTA);
    }

    if (iterator.hasNext()) {
      Assert.fail("Expected another value: " + iterator.next() + " in " + rawData);
    }
  }

  private void removeDupes(List<ChartData.DataPoint> rawData) {
    // rawData (due to other bugs) can have duplicate entries, which aren't important to
    // us here.
    if (rawData.size() < 2) {
      return;
    }
    Iterator<ChartData.DataPoint> iterator = rawData.iterator();
    ChartData.DataPoint thisPoint = iterator.next();
    ChartData.DataPoint nextPoint;
    while (iterator.hasNext()) {
      nextPoint = iterator.next();
      if (nextPoint.getX() == thisPoint.getX() && nextPoint.getY() == thisPoint.getY()) {
        iterator.remove();
      } else {
        thisPoint = nextPoint;
      }
    }
  }
}
