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

import android.view.View;

/** TextWatcher that updates an ActiveBundle with a float value when the text is changed. */
public class FloatUpdatingWatcher extends OptionsUpdatingWatcher {
  private final String bundleKey;

  public FloatUpdatingWatcher(ActiveBundle activeBundle, String bundleKey, View view) {
    super(activeBundle, view);
    this.bundleKey = bundleKey;
  }

  @Override
  protected void applyUpdate(String string, ActiveBundle activeBundle) {
    Float value = Float.valueOf(string);
    activeBundle.changeFloat(bundleKey, value);
  }
}
