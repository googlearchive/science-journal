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

import com.google.android.material.snackbar.Snackbar;

/** Allows us to only show one snackbar at a time. */
public class SnackbarManager {
  private Snackbar visibleSnackbar = null;

  public void showSnackbar(Snackbar bar) {
    // TODO: UX asks for the Snackbar to be shown above the external axis...
    // may need to do a custom snackbar class.
    bar.setCallback(
        new Snackbar.Callback() {

          @Override
          public void onDismissed(Snackbar snackbar, int event) {
            visibleSnackbar = null;
          }

          @Override
          public void onShown(Snackbar snackbar) {}
        });
    hideVisibleSnackbar();
    bar.show();
    visibleSnackbar = bar;
  }

  public boolean snackbarIsVisible() {
    return visibleSnackbar != null;
  }

  /**
   * Call when the fragment/activity managing the snackbars is destroyed, and any snackbar should be
   * dismissed.
   */
  public void onDestroy() {
    hideVisibleSnackbar();
    visibleSnackbar = null;
  }

  public void hideVisibleSnackbar() {
    if (visibleSnackbar != null) {
      visibleSnackbar.dismiss();
    }
  }
}
