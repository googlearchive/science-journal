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
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Static permission functions shared across many parts of the app.
 */
public class PermissionUtils {
    public static final String TAG = "PermissionUtils";

    // The microphone requires explicit permission in Android M. Check that we have permission
    // before adding the decibel sensor option.
    public static boolean tryRequestingPermission(Activity activity, String permission,
                                                  int permissionType, boolean forceRetry) {
        if (!permissionIsGranted(activity, permission)) {
            // Should we show an explanation?
            if (canRequestAgain(activity, permission) && forceRetry) {
                // No explanation needed, so we can request the permission.
                ActivityCompat.requestPermissions(activity, new String[]{permission},
                        permissionType);
            } else {
                // Then the user didn't explicitly ask for us to retry the permission,
                // so we won't do anything.
            }
            return false;
        }
        return true;
    }

    public static boolean permissionIsGranted(Activity activity, String permission) {
        int permissionCheck = ContextCompat.checkSelfPermission(activity, permission);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canRequestAgain(Activity activity, String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
}
