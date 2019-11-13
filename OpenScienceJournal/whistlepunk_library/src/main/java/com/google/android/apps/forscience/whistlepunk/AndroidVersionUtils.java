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
package com.google.android.apps.forscience.whistlepunk;

import android.os.Build;

/** Utility classes to determine current Android API level. */
public class AndroidVersionUtils {
  public static boolean isApiLevelAtLeast(int apiLevel) {
    return Build.VERSION.SDK_INT >= apiLevel;
  }

  public static boolean isApiLevelAtLeastOreo() {
    return isApiLevelAtLeast(Build.VERSION_CODES.O);
  }

  public static boolean isApiLevelAtLeastNougat() {
    return isApiLevelAtLeast(Build.VERSION_CODES.N);
  }

  public static boolean isApiLevelAtLeastMarshmallow() {
    return isApiLevelAtLeast(Build.VERSION_CODES.M);
  }
}
