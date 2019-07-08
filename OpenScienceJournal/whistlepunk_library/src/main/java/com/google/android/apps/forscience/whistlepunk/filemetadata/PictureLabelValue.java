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

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue.LabelValue.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabelValue;

/** A Label which is represented by a set of pictures. */
@Deprecated
public class PictureLabelValue extends LabelValue {
  private static final String KEY_FILE_PATH = "file_path";
  private static final String KEY_CAPTION = "caption";

  public PictureLabelValue(GoosciLabelValue.LabelValue value) {
    super(value);
  }

  public static PictureLabelValue fromPicture(String path, String caption) {
    return new PictureLabelValue(createLabelValue(path, caption));
  }

  @VisibleForTesting
  PictureLabelValue() {
    super();
    value.type = ValueType.PICTURE;
  }

  @Override
  public boolean canEditTimestamp() {
    return true;
  }

  public String getFilePath() {
    return getFilePath(getValue());
  }

  /** @return the pure disk path of the file, without any "file" prepending. */
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
    return value.getDataOrDefault(KEY_FILE_PATH, "");
  }

  /** Changes the path for this picture label value. */
  public void updateFilePath(String filePath) {
    getValue().putData(KEY_FILE_PATH, filePath);
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
    return value.getDataOrDefault(KEY_CAPTION, "");
  }

  public static void clearCaption(GoosciLabelValue.LabelValue value) {
    value.putData(KEY_CAPTION, "");
  }

  public static void populateLabelValue(
      GoosciLabelValue.LabelValue value, String path, String caption) {
    value.type = ValueType.PICTURE;
    value.putData(KEY_FILE_PATH, path);
    value.putData(KEY_CAPTION, caption);
  }

  private static GoosciLabelValue.LabelValue createLabelValue(String path, String caption) {
    GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
    populateLabelValue(value, path, caption);
    return value;
  }
}
