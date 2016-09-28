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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;

/**
 * Static picture functions shared across many parts of the app.
 */
public class PictureUtils {
    public static final String TAG = "PictureUtils";

    // Links a photo-taking request intent with the onActivityResult by requestType.
    public static final int REQUEST_TAKE_PHOTO = 1;
    public static final int PERMISSIONS_WRITE_EXTERNAL_STORAGE = 2;
    public static final int PERMISSIONS_CAMERA = 3;

    // From http://developer.android.com/training/camera/photobasics.html.
    public static File createImageFile(long timestamp) throws IOException {
        // Create an image file name
        String timeStamp = String.valueOf(timestamp);
        String imageFileName = "ScienceJournal_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return imageFile;
    }

    /**
     * Scan the file when adding or deleting so that media scanner aware apps like Gallery apps can
     * properly index the file.
     */
    public static void scanFile(String photoPath, Context context) {
        MediaScannerConnection.scanFile(context, new String[]{photoPath}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i(TAG, "Scanned " + path + ":");
                        Log.i(TAG, "-> uri=" + uri);
                    }
                });
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
            PermissionUtils.tryRequestingPermission(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSIONS_WRITE_EXTERNAL_STORAGE,
                    true);
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults, Activity activity) {
        boolean granted = grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;
        switch (requestCode) {
            case PERMISSIONS_CAMERA:
                cameraPermissionGranted(activity, granted);
                return;
            case PERMISSIONS_WRITE_EXTERNAL_STORAGE:
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
