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

import android.util.Log;
import android.view.View;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * Interface for loading and storing a sensor's options from persistent storage.
 *
 * <p>Implementations may use SharedPreferences, a SQLite database, or another strategy.
 */
// TODO: remove uses of OptionsStorage, rename to remove "New"
public interface NewOptionsStorage {
  WriteableSensorOptions load(FailureListener onFailures);

  class SnackbarFailureListener implements FailureListener {
    private static final String TAG = "SnackbarFListener";
    private final View view;

    public SnackbarFailureListener(View view) {
      this.view = view;
    }

    @Override
    public void fail(Exception e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Error loading options", e);
      }
      if (view == null) {
        return;
      }
      String message = view.getResources().getString(R.string.options_load_error);
      AccessibilityUtils.makeSnackbar(view, message, Snackbar.LENGTH_LONG).show();
    }
  }
}
