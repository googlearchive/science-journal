/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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
package com.google.android.apps.forscience.whistlepunk.filemetadata;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Interprets a label
 */
// TODO: This will be deprecated. Rename to LegacyLabelValue, and rename classes that inherit
// from it as well.
@Deprecated
public abstract class LabelValue {
    GoosciLabelValue.LabelValue mValue;

    public LabelValue(GoosciLabelValue.LabelValue value) {
        mValue = value;
    }

    public LabelValue() {
        mValue = new GoosciLabelValue.LabelValue();
    }

    public GoosciLabelValue.LabelValue getValue() {
        return mValue;
    }

    protected void setValue(GoosciLabelValue.LabelValue value) {
        mValue = value;
    }

    public abstract boolean canEditTimestamp();
}
