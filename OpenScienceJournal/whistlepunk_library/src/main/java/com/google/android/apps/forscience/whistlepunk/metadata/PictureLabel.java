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
import android.text.TextUtils;

/**
 * A Label which is represented by a set of pictures.
 */
public class PictureLabel extends Label {

    public static final String TAG = "picture";

    private static final int NUM_FIELDS = 2;
    private static final String KEY_FILE_PATH = "file_path";
    private static final int INDEX_FILE_PATH = 0;
    private static final String KEY_CAPTION = "caption";
    private static final int INDEX_CAPTION = 1;

    public PictureLabel(String labelId, String startLabelId, long timestamp,
            GoosciLabelValue.LabelValue value) {
        super(labelId, startLabelId, timestamp, value);
    }

    public PictureLabel(String path, String caption, String labelId, String startLabelId,
            long timestampMs) {
        this(labelId, startLabelId, timestampMs, createStorageValue(path, caption));
    }

    private PictureLabel() {
        super();
    }

    public String getFilePath() {
        return getFilePath(getValue());
    }

    public static String getFilePath(GoosciLabelValue.LabelValue value) {
        if (value.data.length > INDEX_FILE_PATH &&
                TextUtils.equals(value.data[INDEX_FILE_PATH].key, KEY_FILE_PATH)) {
            return value.data[INDEX_FILE_PATH].value;
        }
        return "";
    }

    public void setCaption(String caption) {
        populateStorageValue(getValue(), getFilePath(), caption);
    }

    public String getCaption() {
        return getCaption(getValue());
    }

    public static String getCaption(GoosciLabelValue.LabelValue value) {
        if (value.data.length > INDEX_CAPTION &&
                TextUtils.equals(value.data[INDEX_CAPTION].key, KEY_CAPTION)) {
            return value.data[INDEX_CAPTION].value;
        }
        return "";
    }

    public static void populateStorageValue(GoosciLabelValue.LabelValue value, String path,
            String caption) {
        if (value.data == null || value.data.length == 0) {
            value.data = new GoosciLabelValue.LabelValue.DataEntry[NUM_FIELDS];
            value.data[INDEX_FILE_PATH] = new GoosciLabelValue.LabelValue.DataEntry();
            value.data[INDEX_CAPTION] = new GoosciLabelValue.LabelValue.DataEntry();
        }
        value.data[INDEX_FILE_PATH].key = KEY_FILE_PATH;
        value.data[INDEX_FILE_PATH].value = path;
        value.data[INDEX_CAPTION].key = KEY_CAPTION;
        value.data[INDEX_CAPTION].value = caption;
    }

    private static GoosciLabelValue.LabelValue createStorageValue(String path, String caption) {
        GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
        populateStorageValue(value, path, caption);
        return value;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    static boolean isTag(String tag) {
        return TAG.equalsIgnoreCase(tag);
    }

    public static final Parcelable.Creator<PictureLabel> CREATOR =
            new Parcelable.Creator<PictureLabel>() {
        public PictureLabel createFromParcel(Parcel in) {
            PictureLabel label = new PictureLabel();
            label.populateFromParcel(in);
            return label;
        }

        @Override
        public PictureLabel[] newArray(int size) {
            return new PictureLabel[size];
        }
    };
}
