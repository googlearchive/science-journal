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

import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.common.annotations.VisibleForTesting;

/**
 * A Label which is represented by a set of pictures.
 */
@Deprecated
public class PictureLabelValue extends LabelValue {
    private static final int NUM_FIELDS = 2;
    private static final String KEY_FILE_PATH = "file_path";
    private static final int INDEX_FILE_PATH = 0;
    private static final String KEY_CAPTION = "caption";
    private static final int INDEX_CAPTION = 1;

    public PictureLabelValue(GoosciLabelValue.LabelValue value) {
        super(value);
    }

    public static PictureLabelValue fromPicture(String path, String caption) {
        return new PictureLabelValue(createLabelValue(path, caption));
    }

    @VisibleForTesting
    PictureLabelValue() {
        super();
        mValue.type = GoosciLabelValue.LabelValue.PICTURE;
    }

    @Override
    public boolean canEditTimestamp() {
        return true;
    }

    public String getFilePath() {
        return getFilePath(getValue());
    }

    /**
     * @return the pure disk path of the file, without any "file" prepending.
     */
    public String getAbsoluteFilePath() {
        return getAbsoluteFilePath(getFilePath());
    }

    public static String getAbsoluteFilePath(String filePath) {
        if (filePath.startsWith("file:")) {
            filePath = filePath.substring("file:".length());
        }
        return filePath;
    }

    public static String getFilePath(GoosciLabelValue.LabelValue value) {
        if (hasFilePath(value)) {
            return value.data[INDEX_FILE_PATH].value;
        }
        return "";
    }

    /**
     * Changes the path for this picture label value.
     */
    public void updateFilePath(String filePath) {
        if (hasFilePath(getValue())) {
            getValue().data[INDEX_FILE_PATH].value = filePath;
        }
    }

    private static boolean hasFilePath(GoosciLabelValue.LabelValue value) {
        return value.data.length > INDEX_FILE_PATH &&
                TextUtils.equals(value.data[INDEX_FILE_PATH].key, KEY_FILE_PATH);
    }

    // The caption within the PictureLabelValue is no longer used.
    @Deprecated
    public void setCaption(String caption) {
        populateLabelValue(getValue(), getFilePath(), caption);
    }

    // The caption within the PictureLabelValue is no longer used.
    @Deprecated
    @VisibleForTesting
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

    public static void clearCaption(GoosciLabelValue.LabelValue value) {
        if (value.data.length > INDEX_CAPTION &&
                TextUtils.equals(value.data[INDEX_CAPTION].key, KEY_CAPTION)) {
            value.data[INDEX_CAPTION].value = "";
        }
    }

    public static void populateLabelValue(GoosciLabelValue.LabelValue value, String path,
            String caption) {
        value.type = GoosciLabelValue.LabelValue.PICTURE;
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

    private static GoosciLabelValue.LabelValue createLabelValue(String path, String caption) {
        GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
        populateLabelValue(value, path, caption);
        return value;
    }
}
