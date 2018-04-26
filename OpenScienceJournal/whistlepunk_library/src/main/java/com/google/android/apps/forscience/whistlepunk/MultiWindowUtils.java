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

import android.content.Context;

/** Utility class for figuring out if the app support multiwindow. */
public class MultiWindowUtils {

  public static boolean isMultiWindowEnabled(Context context) {
    return AndroidVersionUtils.isApiLevelAtLeastNougat()
        || (AndroidVersionUtils.isApiLevelAtLeastMarshmallow() && isARC(context));
  }

  private static boolean isARC(Context context) {
    if (context == null) {
      return false;
    }
    return context.getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
  }
}
