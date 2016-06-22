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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig.BleSensorConfig
        .ScaleTransform;

/**
 * Filter that applies a linear function to the incoming function
 */
public class ScaleFilter implements ValueFilter {
    private final double mSourceBottom;
    private final double mDestBottom;
    private final double mSourceRange;
    private final double mDestRange;

    public ScaleFilter(ScaleTransform transform) {
        mSourceBottom = transform.sourceBottom;
        mSourceRange = transform.sourceTop - mSourceBottom;
        mDestBottom = transform.destBottom;
        mDestRange = transform.destTop - mDestBottom;
    }

    @Override
    public double filterValue(long timestamp, double value) {
        double ratio = (value - mSourceBottom) / mSourceRange;
        double transformed = (ratio * mDestRange) + mDestBottom;
        return transformed;
    }

}
