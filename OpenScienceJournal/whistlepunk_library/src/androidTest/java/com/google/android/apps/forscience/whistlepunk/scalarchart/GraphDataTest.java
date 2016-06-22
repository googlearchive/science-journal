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

import android.os.Parcel;
import android.os.Parcelable;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.TestData;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;

import org.achartengine.model.XYSeries;
import org.achartengine.util.IndexXYMap;

import java.util.ArrayList;
import java.util.List;

// Note: these tests will break if the LineGraphPresenter default window size changes from 1.
public class GraphDataTest extends AndroidTestCase {

    private static class TestLabel extends Label implements Parcelable {
        protected TestLabel(long timestamp) {
            super(Arbitrary.string(), Arbitrary.string(), timestamp);
        }

        protected TestLabel(Parcel in) {
            super();
            populateFromParcel(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<TestLabel> CREATOR = new Creator<TestLabel>() {
            @Override
            public TestLabel createFromParcel(Parcel in) {
                return new TestLabel(in);
            }

            @Override
            public TestLabel[] newArray(int size) {
                return new TestLabel[size];
            }
        };

        @Override
        public String getTag() {
            return "TEST";
        }

        @Override
        public GoosciLabelValue.LabelValue getValue() {
            return new GoosciLabelValue.LabelValue();
        }
    }

    private GraphData makeSimpleGraphData() {
        return new GraphData(0);
    }

    public void testBasicAdd() {
        final GraphData data = makeSimpleGraphData();
        final TestData testData = new TestData();

        final int x = Arbitrary.integer();
        final double y = Arbitrary.doubleFloat();
        data.addDataPoint(x, y);
        testData.addPoint(x, y);

        testData.checkContents(data.getDataRange(x - 1, x + 1));
    }

    public void testClear() {
        final GraphData data = makeSimpleGraphData();
        final TestData testData = new TestData();

        final int x = Arbitrary.integer();
        final double y = Arbitrary.doubleFloat();
        data.addDataPoint(x, y);
        testData.addPoint(x, y);

        testData.checkContents(data.getDataRange(x - 1, x + 1));
        data.clear();
        new TestData().checkContents(data.getDataRange(x - 1, x + 1));
    }

    public void testDataSet() {
        final GraphData data = makeSimpleGraphData();
        final TestData testData = new TestData();

        final int x = Arbitrary.integer();
        final double y = Arbitrary.doubleFloat();
        data.addDataPoint(x, y);
        testData.addPoint(x, y);

        // There's also a single label at the given datapoint
        data.setLabels(labels(x));

        testData.checkContents(data.getDataset().getSeriesAt(0).getXYMap());
        testData.checkContents(getLabels(data));
    }

    public void testReplaceLabels() {
        final GraphData data = makeSimpleGraphData();
        final TestData testData = new TestData();

        data.addDataPoint(0, 0);
        data.addDataPoint(1, 1);

        data.setLabels(labels(0));
        new TestData().addPoint(0, 0).checkContents(getLabels(data));

        data.setLabels(labels(1));
        new TestData().addPoint(1, 1).checkContents(getLabels(data));
    }

    public void testPending() {
        final GraphData data = makeSimpleGraphData();

        data.addDataPoint(0, 0);

        data.setLabels(labels(1, 2));
        new TestData().addPoint(1, 0).addPoint(2, 0).checkContents(getLabels(data));
    }

    public void testLabelBefore() {
        final GraphData data = makeSimpleGraphData();

        data.addDataPoint(1, 1);

        data.setLabels(labels(0));
        new TestData().addPoint(0, 1).checkContents(getLabels(data));
    }

    public void testLabelBeforeAnyData() {
        final GraphData data = makeSimpleGraphData();
        data.setLabels(labels(0));
        new TestData().checkContents(getLabels(data));

        data.addDataPoint(1, 1);
        new TestData().addPoint(0, 1).checkContents(getLabels(data));
    }

    // TODO: separate out label interpolation into its own testable algorithm

    public void testLabelInterpolate() {
        final GraphData data = makeSimpleGraphData();
        data.addDataPoint(0, 0);
        data.addDataPoint(4, 8);
        data.setLabels(labels(1, 2, 3));
        new TestData().addPoint(1, 2).addPoint(2, 4).addPoint(3, 6).checkContents(getLabels(data));
    }

    public void testInterpolateAfter() {
        final GraphData data = makeSimpleGraphData();
        data.addDataPoint(0, 0);
        data.setLabels(labels(1, 2, 3));
        new TestData().addPoint(1, 0).addPoint(2, 0).addPoint(3, 0).checkContents(getLabels(data));
        data.addDataPoint(4, 8);
        new TestData().addPoint(1, 2).addPoint(2, 4).addPoint(3, 6).checkContents(getLabels(data));
    }

    public void testInterpolateDataAddedBackwards() {
        final GraphData data = makeSimpleGraphData();
        data.addDataPoint(4, 8);
        data.setLabels(labels(1, 2, 3));
        new TestData().addPoint(1, 8).addPoint(2, 8).addPoint(3, 8).checkContents(getLabels(data));
        data.addDataPoint(0, 0);
        new TestData().addPoint(1, 2).addPoint(2, 4).addPoint(3, 6).checkContents(getLabels(data));
    }

    public void testInterpolateAfterTwice() {
        final GraphData data = makeSimpleGraphData();
        data.addDataPoint(0, 0);
        data.setLabels(labels(1, 2, 3));
        new TestData().addPoint(1, 0).addPoint(2, 0).addPoint(3, 0).checkContents(getLabels(data));
        data.addDataPoint(2, 4);
        new TestData().addPoint(1, 2).addPoint(2, 4).addPoint(3, 4).checkContents(getLabels(data));
        data.addDataPoint(4, 8);
        new TestData().addPoint(1, 2).addPoint(2, 4).addPoint(3, 6).checkContents(getLabels(data));
    }

    public void testAddDataPointsNoSmoothingWindow() {
        GraphData data = makeSimpleGraphData();
        data.setWindowAndBlur(1, ScalarDisplayOptions.BLUR_TYPE_AVERAGE,
                ScalarDisplayOptions.DEFAULT_GAUSSIAN_SIGMA);
        XYSeries expected = new XYSeries("expected");

        for (int i = 0; i < 10; i++) {
            data.addDataPoint(i, i*2);
            expected.add(i, i*2);
        }
        assertXYSeriesEqualValues(data.getDataset().getSeriesAt(data.getLineDataIndex()), expected);

        data = makeSimpleGraphData();
        data.setWindowAndBlur(1, ScalarDisplayOptions.BLUR_TYPE_GAUSSIAN,
                ScalarDisplayOptions.DEFAULT_GAUSSIAN_SIGMA);
        for (int i = 0; i < 10; i++) {
            data.addDataPoint(i, i * 2);
        }
        assertXYSeriesEqualValues(data.getDataset().getSeriesAt(data.getLineDataIndex()), expected);
    }

    public void testAddDataPointsWithSmoothingSizeTwo_AverageBlur() {
        // Smooths across 2 points total: each point is the average of itself and the previous raw
        // data point.
        final GraphData data = new GraphData(2, ScalarDisplayOptions.BLUR_TYPE_AVERAGE,
                ScalarDisplayOptions.DEFAULT_GAUSSIAN_SIGMA, 0);
        XYSeries expected = new XYSeries("expected");

        for (int i = 0; i < 10; i++) {
            data.addDataPoint(i, i * 2);
            if (i == 0) {
                // The first point has no smoothing because it is alone.
                expected.add(0, 0);
            } else {
                // The average timestamp and the average value.
                expected.add(i - .5, 2 * i - 1);
            }
        }
        assertXYSeriesEqualValues(data.getDataset().getSeriesAt(data.getLineDataIndex()), expected);
    }

    public void testAddDataPointsWithSmoothingSizeFour_GaussianBlur() {
        final GraphData data = new GraphData(4, ScalarDisplayOptions.BLUR_TYPE_GAUSSIAN, 1, 0);

        XYSeries expected = new XYSeries("expected");

        // A gaussian kernel is 1 / sqrt(2 * pi * sigma ^ 2) * e ^ (-x ^ 2 / (2 * pi * sigma)
        // In this case, sigma is 1, so the calculation gets quite simple. However, we will
        // create the array statically instead of dynamically!
        double[] kernel = {0.3989422804014327, 0.24197072451914337, 0.05399096651318806,
                0.004431848411938008};
        double sum = kernel[0] + kernel[1] + kernel[2] + kernel[3];
        for (int i = 0; i < 4; i++) {
            kernel[i] /= sum;
        }

        // Let's use the line y=x to make this more simple.
        data.addDataPoint(0, 0);
        expected.add(0, 0);

        data.addDataPoint(1, 1);
        double denom = kernel[0] + kernel[1];
        double value = kernel[0] / denom;
        expected.add(value, value);

        data.addDataPoint(2, 2);
        denom = kernel[0] + kernel[1] + kernel[2];
        value = 2 * kernel[0] / denom + 1 * kernel[1] / denom;
        expected.add(value, value);

        data.addDataPoint(3, 3);
        // Now we are using the whole kernel and don't need to re-scale the values.
        value = 3 * kernel[0] + 2 * kernel[1] + 1 * kernel[2];
        expected.add(value, value);

        data.addDataPoint(4, 4);
        value = 4 * kernel[0] + 3 * kernel[1] + 2 * kernel[2] + 1 * kernel[3];
        expected.add(value, value);

        data.addDataPoint(5, 5);
        value = 5 * kernel[0] + 4 * kernel[1] + 3 * kernel[2] + 2 * kernel[3];
        expected.add(value, value);

        assertXYSeriesEqualValues(data.getDataset().getSeriesAt(data.getLineDataIndex()), expected);
    }

    public void testGetClosestDataPointToTimestamp() {
        final GraphData data = new GraphData(0);
        data.addDataPoint(100, 1);
        data.addDataPoint(110, 2);
        data.addDataPoint(120, 3);
        data.addDataPoint(130, 4);

        // exact match
        GraphData.ReadonlyDataPoint expected = new GraphData.ReadonlyDataPoint(110, 2);
        assertReadonlyDataPointEqualValues(data.getClosestDataPointToTimestamp(110), expected);

        // too small for range
        expected = new GraphData.ReadonlyDataPoint(100, 1);
        assertReadonlyDataPointEqualValues(data.getClosestDataPointToTimestamp(80), expected);

        // too large for range
        expected = new GraphData.ReadonlyDataPoint(130, 4);
        assertReadonlyDataPointEqualValues(data.getClosestDataPointToTimestamp(200), expected);

        // in the middle, needs ceiling
        expected = new GraphData.ReadonlyDataPoint(120, 3);
        assertReadonlyDataPointEqualValues(data.getClosestDataPointToTimestamp(119), expected);

        // in the middle, needs floor
        expected = new GraphData.ReadonlyDataPoint(120, 3);
        assertReadonlyDataPointEqualValues(data.getClosestDataPointToTimestamp(121), expected);
    }

    private IndexXYMap<Double, Double> getLabels(GraphData data) {
        return data.getDataset().getSeriesAt(1).getXYMap();
    }

    private List<Label> labels(int... xs) {
        List<Label> labels = new ArrayList<>(xs.length);
        for (int x : xs) {
            labels.add(new TestLabel(x));
        }
        return labels;
    }

    private void assertXYSeriesEqualValues(XYSeries actual, XYSeries expected) {
        assertEquals(actual.getItemCount(), expected.getItemCount());
        int size = actual.getItemCount();
        for (int i = 0; i < size; i++) {
            // Have a slight tolerance for error in the test.
            assertEquals(actual.getX(i), expected.getX(i), 0.000000001f);
            assertEquals(actual.getY(i), expected.getY(i), 0.000000001f);
        }
    }

    private void assertReadonlyDataPointEqualValues(GraphData.ReadonlyDataPoint actual,
                                                    GraphData.ReadonlyDataPoint expected) {
        assertEquals(actual.getX(), expected.getX(), 0.000000001);
        assertEquals(actual.getY(), expected.getY(), 0.000000001);
    }
}
