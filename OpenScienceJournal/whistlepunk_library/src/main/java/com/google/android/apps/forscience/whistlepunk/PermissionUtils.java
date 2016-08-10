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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Static permission functions shared across many parts of the app.
 */
public class PermissionUtils {
    public static final String TAG = "PermissionUtils";

    private static final String KEY_PERMISSIONS_REQUESTED_SET = "permissions_requested";
    private static final Set<String> NO_STRINGS = Collections.emptySet();

    // Some things require explicit permission in Android M+. Check that we have permission.
    public static boolean tryRequestingPermission(Activity activity, String permission,
                                                  int permissionType, boolean forceRetry) {
        if (!permissionIsGranted(activity, permission)) {
            // Should we show an explanation?
            if (canRequestAgain(activity, permission) && forceRetry) {
                // No explanation needed, so we can request the permission.
                requestPermission(activity, permission, permissionType);
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
        // If the user has denied the permissions request, but not either never asked before
        // or clicked "never ask again", this will return true. In that case, we can request again.
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            return true;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        Set<String> requestedPerms = prefs.getStringSet(KEY_PERMISSIONS_REQUESTED_SET, NO_STRINGS);

        // If the permission is in the set, they have asked for it already, and
        // at this point shouldShowRequestPermissionRationale is false, so they have also
        // clicked "never ask again". Return false -- we cannot request again.
        // If it was not found in the set, they haven't asked for it yet, so we can still ask.
        return !requestedPerms.contains(permission);
    }

    private static void requestPermission(Activity activity, String permission,
            int permissionType) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        Set<String> requestedPerms = prefs.getStringSet(KEY_PERMISSIONS_REQUESTED_SET, null);
        if (requestedPerms == null) {
            requestedPerms = new HashSet<>();
        }
        requestedPerms.add(permission);
        prefs.edit().putStringSet(KEY_PERMISSIONS_REQUESTED_SET, requestedPerms).apply();

        ActivityCompat.requestPermissions(activity, new String[]{permission}, permissionType);
    }
}
