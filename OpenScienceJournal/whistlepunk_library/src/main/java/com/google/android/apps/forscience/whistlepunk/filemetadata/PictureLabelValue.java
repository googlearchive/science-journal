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
import com.google.android.apps.forscience.whistlepunk.LabelValuePojo;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue.LabelValue.ValueType;

/** A Label which is represented by a set of pictures. */
@Deprecated
public class PictureLabelValue extends LabelValue {
  private static final String KEY_FILE_PATH = "file_path";
  private static final String KEY_CAPTION = "caption";

  public PictureLabelValue(LabelValuePojo value) {
    super(value);
  }

  @VisibleForTesting
  PictureLabelValue() {
    super();
    value.setType(ValueType.PICTURE);
  }

  public static PictureLabelValue fromPicture(String path, String caption) {
    return new PictureLabelValue(createLabelValue(path, caption));
  }

  @Override
  public boolean canEditTimestamp() {
    return true;
  }

  public String getFilePath() {
    return getFilePath(getValue());
  }

  public static String getFilePath(LabelValuePojo value) {
    return value.getDataOrDefault(KEY_FILE_PATH, "");
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

  public static String getCaption(LabelValuePojo value) {
    return value.getDataOrDefault(KEY_CAPTION, "");
  }

  public static void clearCaption(LabelValuePojo value) {
    value.putData(KEY_CAPTION, "");
  }

  public static void populateLabelValue(LabelValuePojo value, String path, String caption) {
    value.setType(ValueType.PICTURE);
    value.putData(KEY_FILE_PATH, path);
    value.putData(KEY_CAPTION, caption);
  }

  private static LabelValuePojo createLabelValue(String path, String caption) {
    LabelValuePojo value = new LabelValuePojo();
    populateLabelValue(value, path, caption);
    return value;
  }
}
