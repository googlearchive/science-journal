package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.support.annotation.VisibleForTesting;

import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ChartData {

    public static class DataPoint {

        private final long mX;
        private final double mY;

        public DataPoint(long x, double y) {
            mX = x;
            mY = y;
        }

        public long getX() {
            return mX;
        }

        public double getY() {
            return mY;
        }
    }

    // The number of indicies that an approximate binary search may be off.
    // Larger numbers cause binary search to be faster at the risk of drawing unnecessary points.
    // TODO: Look into tweaking this number for utmost efficency and memory usage!
    @VisibleForTesting
    public static final int DEFAULT_APPROX_RANGE = 8;

    private List<DataPoint> mData = new ArrayList<>();

    // The list of data points at which a label should be displayed.
    private List<DataPoint> mLabels = new ArrayList<>();

    // The list of Label objects which are not yet converted into DataPoints and added to the
    // mLabels list. This happens when the Label is outside of the range for which we have data,
    // so we cannot calculate where that label should be drawn.
    private List<Label> mUnaddedLabels = new ArrayList<>();

    private List<StreamStat> mStats = new ArrayList<>();

    public ChartData() {
    }

    // This assumes the data point occurs after all previous data points.
    // Order is not checked.
    public void addPoint(DataPoint point) {
        mData.add(point);
        if (mUnaddedLabels.size() > 0) {
            // TODO to avoid extra work, only try again if new data might come in in the direction
            // of these labels...?
            Iterator<Label> unaddedLabelIterator = mUnaddedLabels.iterator();
            while (unaddedLabelIterator.hasNext()) {
                Label next = unaddedLabelIterator.next();
                if (tryAddingLabel(next)) {
                    unaddedLabelIterator.remove();
                }
            }
        }
    }

    public List<DataPoint> getPoints() {
        return mData;
    }

    // This assumes the List<DataPoint> is ordered by timestamp.
    public void setPoints(List<DataPoint> data) {
        mData = data;
    }

    public List<DataPoint> getPointsInRangeToEnd(long xMin) {
        int startIndex = approximateBinarySearch(xMin, 0, true);
        return mData.subList(startIndex, mData.size());
    }

    public List<DataPoint> getPointsInRange(long xMin, long xMax) {
        int startIndex = approximateBinarySearch(xMin, 0, true);
        int endIndex = approximateBinarySearch(xMax, startIndex, false);
        if (startIndex > endIndex) {
            return Collections.emptyList();
        }
        return mData.subList(startIndex, endIndex + 1);
    }

    public DataPoint getClosestDataPointToTimestamp(long timestamp) {
        int index = getClosestIndexToTimestamp(timestamp);
        if (mData.size() == 0) {
            return null;
        }
        return mData.get(index);
    }

    // Searches for the closest index to a given timestamp.
    public int getClosestIndexToTimestamp(long timestamp) {
        int lowerIndex = exactBinarySearch(timestamp, 0);
        if (lowerIndex >= mData.size() - 1) {
            return lowerIndex;
        }
        long valueAtLowerIndex = mData.get(lowerIndex).getX();
        long valueAtUpperIndex = mData.get(lowerIndex + 1).getX();
        if (timestamp - valueAtLowerIndex < valueAtUpperIndex - timestamp) {
            return lowerIndex;
        }
        return lowerIndex + 1;
    }

    /**
     * Searches for the index of the value that is equal to or just less than the search X value, in
     * the range of startSearchIndex to the end of the data array.
     * @param searchX The X value to search for
     * @param startSearchIndex The index into the data where the search starts
     * @return The exact index of the value at or just below the search X value.
     */
    @VisibleForTesting
    int exactBinarySearch(long searchX, int startSearchIndex) {
        return approximateBinarySearch(searchX, startSearchIndex, mData.size() - 1, true, 0);
    }

    /**
     * A helper function to search for the index of the point with the closest value to the searchX
     * provided, using the default approximate search range.
     * @param searchX The value to search for
     * @param startIndex The index into the data where the search starts
     * @param preferStart Whether the approximate result should prefer the start of a range or
     *                    the end of a range. This can be used to make sure the range is not
     *                    too short.
     * @return The index of an approximate X match in the array
     */
    private int approximateBinarySearch(long searchX, int startSearchIndex, boolean preferStart) {
        return approximateBinarySearch(searchX, startSearchIndex, mData.size() - 1, preferStart,
                DEFAULT_APPROX_RANGE);
    }

    /**
     * Searches for the index of the point with the closest value to the searchX provided.
     * Does not try for an exact match, rather returns when the range is smaller than the
     * approximateSearchRange. Assumes points are ordered.
     * @param searchX The value to search for
     * @param startIndex The index into the data where the search starts
     * @param endIndex The index where the search ends
     * @param preferStart Whether the approximate result should prefer the start of a range or
     *                    the end of a range. This can be used to make sure the range is not
     *                    too short.
     * @param searchRange The size of the range at which we can stop searching and just return
     *                    something, either at the start of the current range if preferStart,
     *                    or the end of the current range if preferEnd. This function is often used
     *                    to find the approximate start and end indices of a known range, when
     *                    erring on the outside of that range is ok but erring on the inside of
     *                    the range causes points to be clipped.
     * @return The index of an approximate X match in the array
     */
    @VisibleForTesting
    int approximateBinarySearch(long searchX, int startIndex, int endIndex,
            boolean preferStart, int searchRange) {
        if (endIndex - startIndex <= searchRange) {
            return preferStart ? startIndex: endIndex;
        }
        int mid = (startIndex + endIndex) / 2;
        if (mData.get(mid).getX() < searchX) {
            return approximateBinarySearch(searchX, mid + 1, endIndex, preferStart, searchRange);
        } else if (mData.get(mid).getX() > searchX){
            return approximateBinarySearch(searchX, startIndex, mid - 1, preferStart, searchRange);
        } else {
            return mid;
        }
    }

    public int getNumPoints() {
        return mData.size();
    }

    public boolean isEmpty() {
        return mData.isEmpty();
    }

    // Assume points are ordered
    public long getXMin() {
        return mData.get(0).getX();
    }

    // Assume points are ordered
    public long getXMax() {
        return mData.get(mData.size() - 1).getX();
    }

    public void clear() {
        mData.clear();
        mLabels.clear();
        mUnaddedLabels.clear();
    }

    public void setDisplayableLabels(List<Label> labels) {
        mLabels.clear();
        mUnaddedLabels.clear();
        for (Label label : labels) {
            if (!tryAddingLabel(label)) {
                mUnaddedLabels.add(label);
            }
        }
    }

    public void addLabel(Label label) {
        if (!tryAddingLabel(label)) {
            mUnaddedLabels.add(label);
        }
    }

    @VisibleForTesting
    protected boolean tryAddingLabel(Label label) {
        long timestamp = label.getTimeStamp();
        if (mData.isEmpty() || timestamp < getXMin() || timestamp > getXMax()) {
            return false;
        }
        int indexPrev = exactBinarySearch(timestamp, 0);
        int indexEnd = exactBinarySearch(timestamp, indexPrev);
        DataPoint start = mData.get(indexPrev);
        DataPoint end = mData.get(indexEnd);
        if (start.getX() == end.getX()) {
            mLabels.add(start);
        } else {
            double weight = (timestamp - start.getX()) / (end.getX() - start.getX()) * 1.0;
            mLabels.add(
                    new DataPoint(timestamp, start.getY() * weight + end.getY() * (1 - weight)));
        }
        return true;
    }

    public List<DataPoint> getLabelPoints() {
        return mLabels;
    }

    public void updateStats(List<StreamStat> stats) {
        mStats = stats;
    }

    public List<StreamStat> getStats() {
        return mStats;
    }

}