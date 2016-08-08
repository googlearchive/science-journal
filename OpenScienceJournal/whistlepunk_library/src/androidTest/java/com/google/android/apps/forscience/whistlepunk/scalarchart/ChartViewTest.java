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
