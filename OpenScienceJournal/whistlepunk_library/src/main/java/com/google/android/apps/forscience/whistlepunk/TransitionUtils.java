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

import android.app.Activity;
import androidx.core.util.Pair;
import android.view.View;
import android.view.Window;
import java.util.ArrayList;

/** Utilities for working with activity transitions. */
public class TransitionUtils {
  private TransitionUtils() {}

  public static Pair<View, String>[] getTransitionPairs(
      Activity activity, View v, String transitionName) {
    ArrayList<Pair<View, String>> list = new ArrayList<>();
    list.add(Pair.create(v, transitionName));
    View statusBar = activity.findViewById(android.R.id.statusBarBackground);
    if (statusBar != null) {
      list.add(Pair.create(statusBar, Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME));
    }
    View navigationBar = activity.findViewById(android.R.id.navigationBarBackground);
    if (navigationBar != null) {
      list.add(Pair.create(navigationBar, Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME));
    }
    return list.toArray(new Pair[list.size()]);
  }
}
