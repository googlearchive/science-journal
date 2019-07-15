/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

/**
 * Holder for flags
 *
 * Flag values should be set in WhistlePunkApplication#onCreate if they differ from their default
 * values or are dependent on server-side values. After WhistlePunkApplication#onCreate, the getters
 * can be trusted and the setters should not be called.
 *
 * If the flag protects a specific feature, the flag should be removed in the next launch following
 * the full rollout of the feature.
 **/
public class Flags {
  private static boolean showActionBar = false;
  private static boolean showTestingOptions = false;
  private static boolean showDrawOption = false;
  private static boolean showVelocityTrackerOption = false;

  private Flags() {} // uninstantiable

  public static void setShowActionBar(boolean show) {
    showActionBar = show;
  }

  public static void setShowDrawOption(boolean show) {
    showDrawOption = show;
  }

  public static void setShowVelocityTrackerOption(boolean show) {
    showVelocityTrackerOption = show;
  }

  public static void setShowTestingOptions(boolean show) {
    showTestingOptions = show;
  }

  public static boolean showActionBar() {
    return showActionBar;
  }

  public static boolean showTestingOptions() {
    return showTestingOptions;
  }

  public static boolean showDrawOption() {
    return showDrawOption;
  }

  public static boolean showVelocityTrackerOption() {
    return showVelocityTrackerOption;
  }
}
