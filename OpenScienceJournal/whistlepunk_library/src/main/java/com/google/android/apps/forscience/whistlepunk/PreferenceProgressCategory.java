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

package com.google.android.apps.forscience.whistlepunk;

import android.annotation.TargetApi;
import android.content.Context;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.View;

/** A PreferenceCategory with a progress spinner. */
public class PreferenceProgressCategory extends PreferenceCategory {

  private boolean progress;

  public PreferenceProgressCategory(Context context) {
    this(context, null);
  }

  public PreferenceProgressCategory(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    setLayoutResource(R.layout.preference_progress_category);
  }

  @TargetApi(21)
  public PreferenceProgressCategory(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    setLayoutResource(R.layout.preference_progress_category);
  }

  @Override
  protected void onBindView(View view) {
    super.onBindView(view);
    final View progressBar = view.findViewById(R.id.scanning_progress);
    progressBar.setVisibility(progress ? View.VISIBLE : View.GONE);
  }

  public void setProgress(boolean progressOn) {
    progress = progressOn;
    notifyChanged();
  }
}
