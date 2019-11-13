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

/** A label value which represents a piece of text. */
@Deprecated
public class TextLabelValue extends LabelValue {
  private static final String KEY_LABEL_TEXT = "label_text";

  public TextLabelValue(LabelValuePojo value) {
    super(value);
    this.value.setType(ValueType.TEXT);
  }

  @VisibleForTesting
  TextLabelValue() {
    super();
    value.setType(ValueType.TEXT);
  }

  public static TextLabelValue fromText(String text) {
    return new TextLabelValue(createLabelValue(text));
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

  public static String getText(LabelValuePojo value) {
    return value.getDataOrThrow(KEY_LABEL_TEXT);
  }

  public static void populateLabelValue(LabelValuePojo value, String text) {
    value.setType(ValueType.TEXT);
    value.putData(KEY_LABEL_TEXT, text);
  }

  private static LabelValuePojo createLabelValue(String text) {
    LabelValuePojo value = new LabelValuePojo();
    populateLabelValue(value, text);
    return value;
  }

  @Override
  public String toString() {
    return getText();
  }
}
