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

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A label which represents a piece of text.
 */
public class TextLabel extends Label {

    public static final String TAG = "text";

    private static final int NUM_FIELDS = 1;
    private static final int INDEX_LABEL_TEXT = 0;
    private static final String KEY_LABEL_TEXT = "label_text";

    public TextLabel(String labelId, String startLabelId, long timestamp,
            GoosciLabelValue.LabelValue value) {
        super(labelId, startLabelId, timestamp, value);
    }

    public TextLabel(String text, String labelId, String startLabelId, long timestampMillis) {
        this(labelId, startLabelId, timestampMillis, createStorageValue(text));
    }

    private TextLabel() {
        super();
    }

    public void setText(String text) {
        populateStorageValue(getValue(), text);
    }

    public String getText() {
        return getText(getValue());
    }

    public static String getText(GoosciLabelValue.LabelValue value) {
        // Assume text only has one element in the label storage data map.
        return value.data[INDEX_LABEL_TEXT].value;
    }

    public static void populateStorageValue(GoosciLabelValue.LabelValue value, String text) {
        if (value.data == null || value.data.length == 0) {
            value.data = new GoosciLabelValue.LabelValue.DataEntry[NUM_FIELDS];
            value.data[INDEX_LABEL_TEXT] = new GoosciLabelValue.LabelValue.DataEntry();
        }
        value.data[INDEX_LABEL_TEXT].key = KEY_LABEL_TEXT;
        value.data[INDEX_LABEL_TEXT].value = text;
    }

    private static GoosciLabelValue.LabelValue createStorageValue(String text) {
        GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
        populateStorageValue(value, text);
        return value;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    static boolean isTag(String tag) {
        return TAG.equalsIgnoreCase(tag);
    }

    @Override
    public boolean canEditTimestamp() {
        return true;
    }

    public static final Parcelable.Creator<TextLabel> CREATOR =
            new Parcelable.Creator<TextLabel>() {
        public TextLabel createFromParcel(Parcel in) {
            TextLabel label = new TextLabel();
            label.populateFromParcel(in);
            return label;
        }

        @Override
        public TextLabel[] newArray(int size) {
            return new TextLabel[size];
        }
    };
}
