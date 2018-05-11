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

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ChartData {
  public static class DataPoint {

    private final long x;
    private final double y;

    public DataPoint(long x, double y) {
      this.x = x;
      this.y = y;
    }

    public long getX() {
      return x;
    }

    public double getY() {
      return y;
    }

    /** For debugging only */
    @Override
    public String toString() {
      return String.format("(%d,%.3g)", x, y);
    }
  }

  // The number of indicies that an approximate binary search may be off.
  // Larger numbers cause binary search to be faster at the risk of drawing unnecessary points.
  // TODO: Look into tweaking this number for utmost efficency and memory usage!
  @VisibleForTesting private static final int DEFAULT_APPROX_RANGE = 8;

  public static final int DEFAULT_THROWAWAY_THRESHOLD = 100;
  private int throwawayDataSizeThreshold;

  // 2 minutes is plenty.
  public static final long DEFAULT_THROWAWAY_TIME_THRESHOLD = 1000 * 60 * 2;
  private long throwawayDataTimeThreshold = DEFAULT_THROWAWAY_TIME_THRESHOLD;

  private List<DataPoint> data = new ArrayList<>();

  // The list of data points at which a label should be displayed.
  private List<DataPoint> labels = new ArrayList<>();

  // The list of Label objects which are not yet converted into DataPoints and added to the
  // labels list. This happens when the Label is outside of the range for which we have data,
  // so we cannot calculate where that label should be drawn.
  private List<Label> unaddedLabels = new ArrayList<>();

  // The stats for this list.
  private List<StreamStat> stats = new ArrayList<>();

  private static final Comparator<? super DataPoint> DATA_POINT_COMPARATOR =
      new Comparator<DataPoint>() {
        @Override
        public int compare(DataPoint lhs, DataPoint rhs) {
          return Long.compare(lhs.getX(), rhs.getX());
        }
      };

  public ChartData() {
    this(DEFAULT_THROWAWAY_THRESHOLD, DEFAULT_THROWAWAY_TIME_THRESHOLD);
  }

  public ChartData(int throwawayDataSizeThreshold, long throwawayDataTimeThreshold) {
    this.throwawayDataSizeThreshold = throwawayDataSizeThreshold;
    this.throwawayDataTimeThreshold = throwawayDataTimeThreshold;
  }

  // This assumes the data point occurs after all previous data points.
  // Order is not checked.
  public void addPoint(DataPoint point) {
    data.add(point);
    if (unaddedLabels.size() > 0) {
      // TODO to avoid extra work, only try again if new data might come in in the direction
      // of these labels...?
      Iterator<Label> unaddedLabelIterator = unaddedLabels.iterator();
      while (unaddedLabelIterator.hasNext()) {
        Label next = unaddedLabelIterator.next();
        if (tryAddingLabel(next)) {
          unaddedLabelIterator.remove();
        }
      }
    }
  }

  public List<DataPoint> getPoints() {
    return data;
  }

  // This assumes the List<DataPoint> is ordered by timestamp.
  public void setPoints(List<DataPoint> data) {
    this.data = data;
  }

  public void addOrderedGroupOfPoints(List<DataPoint> points) {
    if (points == null || points.size() == 0) {
      return;
    }
    data.addAll(points);
    Collections.sort(data, DATA_POINT_COMPARATOR);
  }

  public List<DataPoint> getPointsInRangeToEnd(long xMin) {
    int startIndex = approximateBinarySearch(xMin, 0, true);
    return data.subList(startIndex, data.size());
  }

  public List<DataPoint> getPointsInRange(long xMin, long xMax) {
    int startIndex = approximateBinarySearch(xMin, 0, true);
    int endIndex = approximateBinarySearch(xMax, startIndex, false);
    if (startIndex > endIndex) {
      return Collections.emptyList();
    }
    return data.subList(startIndex, endIndex + 1);
  }

  public DataPoint getClosestDataPointToTimestamp(long timestamp) {
    int index = getClosestIndexToTimestamp(timestamp);
    if (data.size() == 0) {
      return null;
    }
    return data.get(index);
  }

  // Searches for the closest index to a given timestamp, round up or down if the search
  // does not find an exact match, to the closest timestamp.
  public int getClosestIndexToTimestamp(long timestamp) {
    return exactBinarySearch(timestamp, 0);
  }

  /**
   * Searches for the index of the value that is equal to or just less than the search X value, in
   * the range of startSearchIndex to the end of the data array.
   *
   * @param searchX The X value to search for
   * @param startSearchIndex The index into the data where the search starts
   * @return The exact index of the value at or just below the search X value.
   */
  @VisibleForTesting
  int exactBinarySearch(long searchX, int startSearchIndex) {
    return approximateBinarySearch(searchX, startSearchIndex, data.size() - 1, true, 0);
  }

  /**
   * A helper function to search for the index of the point with the closest value to the searchX
   * provided, using the default approximate search range.
   *
   * @param searchX The value to search for
   * @param startSearchIndex The index into the data where the search starts
   * @param preferStart Whether the approximate result should prefer the start of a range or the end
   *     of a range. This can be used to make sure the range is not too short.
   * @return The index of an approximate X match in the array
   */
  private int approximateBinarySearch(long searchX, int startSearchIndex, boolean preferStart) {
    return approximateBinarySearch(
        searchX, startSearchIndex, data.size() - 1, preferStart, DEFAULT_APPROX_RANGE);
  }

  /**
   * Searches for the index of the point with the closest value to the searchX provided. Does not
   * try for an exact match, rather returns when the range is smaller than the
   * approximateSearchRange. Assumes points are ordered.
   *
   * @param searchX The value to search for
   * @param startIndex The index into the data where the search starts
   * @param endIndex The index where the search ends
   * @param preferStart Whether the approximate result should prefer the start of a range or the end
   *     of a range. This can be used to make sure the range is not too short.
   * @param searchRange The size of the range at which we can stop searching and just return
   *     something, either at the start of the current range if preferStart, or the end of the
   *     current range if preferEnd. This function is often used to find the approximate start and
   *     end indices of a known range, when erring on the outside of that range is ok but erring on
   *     the inside of the range causes points to be clipped.
   * @return The index of an approximate X match in the array
   */
  @VisibleForTesting
  int approximateBinarySearch(
      long searchX, int startIndex, int endIndex, boolean preferStart, int searchRange) {
    if (data.isEmpty()) {
      return 0;
    }

    // See if we're already done (need to do this before calculating distances below, in case
    // searchX is so big or small we're in danger of overflow).

    long startValue = data.get(startIndex).getX();
    if (searchX <= startValue) {
      return startIndex;
    }
    long endValue = data.get(endIndex).getX();
    if (searchX >= endValue) {
      return endIndex;
    }
    if (endIndex - startIndex <= searchRange) {
      return preferStart ? startIndex : endIndex;
    }
    if (searchRange == 0 && endIndex - startIndex == 1) {
      long distanceToStart = searchX - startValue;
      long distanceToEnd = endValue - searchX;
      if (distanceToStart < distanceToEnd) {
        return startIndex;
      } else if (distanceToStart == distanceToEnd) {
        return preferStart ? startIndex : endIndex;
      } else {
        return endIndex;
      }
    }
    int mid = (startIndex + endIndex) / 2;
    long midX = data.get(mid).getX();
    if (midX < searchX) {
      return approximateBinarySearch(searchX, mid, endIndex, preferStart, searchRange);
    } else if (midX > searchX) {
      return approximateBinarySearch(searchX, startIndex, mid, preferStart, searchRange);
    } else {
      return mid;
    }
  }

  public int getNumPoints() {
    return data.size();
  }

  public boolean isEmpty() {
    return data.isEmpty();
  }

  // Assume points are ordered
  public long getXMin() {
    return data.get(0).getX();
  }

  // Assume points are ordered
  public long getXMax() {
    return data.get(data.size() - 1).getX();
  }

  public void clear() {
    data.clear();
    labels.clear();
    unaddedLabels.clear();
  }

  public void setDisplayableLabels(List<Label> labels) {
    this.labels.clear();
    unaddedLabels.clear();
    for (Label label : labels) {
      if (!tryAddingLabel(label)) {
        unaddedLabels.add(label);
      }
    }
  }

  public void addLabel(Label label) {
    if (!tryAddingLabel(label)) {
      unaddedLabels.add(label);
    }
  }

  @VisibleForTesting
  boolean tryAddingLabel(Label label) {
    long timestamp = label.getTimeStamp();
    if (data.isEmpty() || timestamp < getXMin() || timestamp > getXMax()) {
      return false;
    }
    int indexPrev = exactBinarySearch(timestamp, 0);
    DataPoint start = data.get(indexPrev);
    if (timestamp == start.getX()) {
      labels.add(start);
      return true;
    } else if (indexPrev < data.size() - 2) {
      DataPoint end = data.get(indexPrev + 1);
      double weight = (timestamp - start.getX()) / (1.0 * end.getX() - start.getX());
      labels.add(new DataPoint(timestamp, start.getY() * weight + end.getY() * (1 - weight)));
      return true;
    }
    return false;
  }

  public List<DataPoint> getLabelPoints() {
    return labels;
  }

  public void updateStats(List<StreamStat> stats) {
    this.stats = stats;
  }

  public List<StreamStat> getStats() {
    return stats;
  }

  public void throwAwayBefore(long throwawayThreshold) {
    throwAwayBetween(Long.MIN_VALUE, throwawayThreshold);
  }

  public void throwAwayAfter(long throwawayThreshold) {
    throwAwayBetween(throwawayThreshold, Long.MAX_VALUE);
  }

  public void throwAwayBetween(long throwAwayMinX, long throwAwayMaxX) {
    if (throwAwayMaxX <= throwAwayMinX) {
      return;
    }

    // This should be the index to the right of max
    int indexEnd = approximateBinarySearch(throwAwayMaxX, 0, data.size() - 1, false, 1);
    int indexStart = approximateBinarySearch(throwAwayMinX, 0, data.size() - 1, false, 1);

    // Only throw away in bulk once we reach a threshold, so that all the work is not done on
    // every iteration. Make sure to also throw out very far away old data to avoid
    // "path too long". So if the data is less than the size, and the range is not too long,
    // we can just "return" here.
    if (indexEnd - indexStart < throwawayDataSizeThreshold
        && (indexStart >= 0
            && indexEnd < data.size()
            && data.get(indexEnd).getX() - data.get(indexStart).getX()
                < throwawayDataTimeThreshold)) {
      return;
    }
    data.subList(indexStart, indexEnd).clear();
  }
}
