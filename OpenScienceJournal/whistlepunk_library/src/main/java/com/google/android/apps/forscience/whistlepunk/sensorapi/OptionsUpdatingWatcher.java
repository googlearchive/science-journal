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

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

/** TextWatcher that updates an ActiveBundle with a long value when the text is changed. */
public abstract class OptionsUpdatingWatcher implements TextWatcher {
  protected ActiveBundle activeBundle;
  protected View view;

  public OptionsUpdatingWatcher(ActiveBundle activeBundle, View view) {
    this.activeBundle = activeBundle;
    this.view = view;
  }

  protected abstract void applyUpdate(String string, ActiveBundle activeBundle)
      throws NumberFormatException;

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {}

  @Override
  public void afterTextChanged(Editable s) {
    try {
      String string = s.toString();
      applyUpdate(string, activeBundle);
    } catch (NumberFormatException e) {
      activeBundle.reportError(e.getMessage(), view);
    }
  }
}
