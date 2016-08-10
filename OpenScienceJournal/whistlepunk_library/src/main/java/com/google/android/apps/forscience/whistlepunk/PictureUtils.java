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

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Static picture functions shared across many parts of the app.
 */
public class PictureUtils {
    public static final String TAG = "PicturUtils";

    // Links a photo-taking request intent with the onActivityResult by requestType.
    public static final int REQUEST_TAKE_PHOTO = 1;
    public static final int PERMISSIONS_WRITE_EXTERNAL_STORAGE = 2;
    public static final int PERMISSIONS_CAMERA = 3;

    private static final String KEY_PERMISSIONS_REQUESTED_SET = "permissions_requested";
    private static final Set<String> NO_STRINGS = Collections.emptySet();

    // From http://developer.android.com/training/camera/photobasics.html.
    public static File createImageFile(long timestamp) throws IOException {
        // Create an image file name
        String timeStamp = String.valueOf(timestamp);
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return imageFile;
    }

    // From http://developer.android.com/training/camera/photobasics.html.
    public static void galleryAddPic(String photoPath, Activity activity) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(photoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        activity.sendBroadcast(mediaScanIntent);
    }

    // From http://developer.android.com/training/camera/photobasics.html.
    public static String capturePictureLabel(Activity activity) {
        // Starts a picture intent.
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = PictureUtils.createImageFile(AppSingleton.getInstance(activity)
                        .getSensorEnvironment().getDefaultClock().getNow());
            } catch (IOException ex) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, ex.getMessage());
                }
            }
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                String pictureLabelPath = "file:" + photoFile.getAbsoluteFile();
                activity.startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
                return pictureLabelPath;
            }
        }
        return null;
    }

    // Assumes that the Camera permission always requires external storage.
    public static void cameraPermissionGranted(Activity activity, boolean granted) {
        // Try to get the storage permission granted if it is not yet, so that all the
        // camera-related permissions requests happen at once.
        if (granted) {
            tryRequestingPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    PERMISSIONS_WRITE_EXTERNAL_STORAGE, true);
        }
    }

    // The microphone requires explicit permission in Android M. Check that we have permission
    // before adding the decibel sensor option.
    public static boolean tryRequestingPermission(Activity activity, String permission,
                                                  int permissionType, boolean forceRetry) {
        int permissionCheck = ContextCompat.checkSelfPermission(activity, permission);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
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

        // The set returned by getStringSet should not be modified.
        Set<String> requestedPerms = prefs.getStringSet(KEY_PERMISSIONS_REQUESTED_SET, null);
        Set<String> copyPerms = new HashSet<>();

        if (requestedPerms != null) {
            copyPerms.addAll(requestedPerms);
        }
        copyPerms.add(permission);
        prefs.edit().putStringSet(KEY_PERMISSIONS_REQUESTED_SET, copyPerms).apply();

        ActivityCompat.requestPermissions(activity, new String[]{permission}, permissionType);
    }

    public static void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults, Activity activity) {
        boolean granted = grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;
        switch (requestCode) {
            case PictureUtils.PERMISSIONS_CAMERA:
                PictureUtils.cameraPermissionGranted(activity, granted);
                return;
            case PictureUtils.PERMISSIONS_WRITE_EXTERNAL_STORAGE:
                // Do nothing for now
                return;
        }
    }

    public static void launchExternalViewer(Activity activity, String fileUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileUrl);
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (!TextUtils.isEmpty(type)) {
            intent.setDataAndType(Uri.parse(fileUrl), type);
            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found to handle this " + fileUrl + " type "
                        + type);
            }
        } else {
            Log.w(TAG, "Could not find mime type for " + fileUrl);
        }
    }
}
