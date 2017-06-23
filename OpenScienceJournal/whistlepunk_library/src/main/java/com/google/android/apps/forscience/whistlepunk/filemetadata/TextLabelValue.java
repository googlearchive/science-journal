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

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.metadata.*;
import com.google.common.annotations.VisibleForTesting;

/**
 * A label value which represents a piece of text.
 */
@Deprecated
public class TextLabelValue extends LabelValue {
    private static final int NUM_FIELDS = 1;
    private static final int INDEX_LABEL_TEXT = 0;
    private static final String KEY_LABEL_TEXT = "label_text";

    public TextLabelValue(GoosciLabelValue.LabelValue value) {
        super(value);
        mValue.type = GoosciLabelValue.LabelValue.TEXT;
    }

    public static TextLabelValue fromText(String text) {
        return new TextLabelValue(createLabelValue(text));
    }

    @VisibleForTesting
    TextLabelValue() {
        super();
        mValue.type = GoosciLabelValue.LabelValue.TEXT;
    }

    @Override
    public boolean canEditTimestamp() {
        return true;
    }

    public void setText(String text) {
        populateLabelValue(getValue(), text);
    }

    public String getText() {
        return getText(getValue());
    }

    public static String getText(GoosciLabelValue.LabelValue value) {
        // Assume text only has one element in the label storage data map.
        return value.data[INDEX_LABEL_TEXT].value;
    }

    public static void populateLabelValue(GoosciLabelValue.LabelValue value, String text) {
        value.type = GoosciLabelValue.LabelValue.TEXT;
        if (value.data == null || value.data.length == 0) {
            value.data = new GoosciLabelValue.LabelValue.DataEntry[NUM_FIELDS];
            value.data[INDEX_LABEL_TEXT] = new GoosciLabelValue.LabelValue.DataEntry();
        }
        value.data[INDEX_LABEL_TEXT].key = KEY_LABEL_TEXT;
        value.data[INDEX_LABEL_TEXT].value = text;
    }

    private static GoosciLabelValue.LabelValue createLabelValue(String text) {
        GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
        populateLabelValue(value, text);
        return value;
    }

    @Override
    public String toString() {
        return getText();
    }
}
