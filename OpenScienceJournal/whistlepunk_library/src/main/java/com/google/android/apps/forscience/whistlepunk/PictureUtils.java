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
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Static picture functions shared across many parts of the app.
 */
public class PictureUtils {
    public static final String TAG = "PictureUtils";

    // Links a photo-taking request intent with the onActivityResult by requestType.
    public static final int REQUEST_TAKE_PHOTO = 1;
    public static final int PERMISSIONS_WRITE_EXTERNAL_STORAGE = 2;
    public static final int PERMISSIONS_CAMERA = 3;

    private static final String PICTURES_DIR_NAME = "ScienceJournal";
    private static final String PICTURE_NAME_TEMPLATE = "Picture_%s.jpg";

    // From http://developer.android.com/training/camera/photobasics.html.
    public static File createImageFile(long timestamp) throws IOException {
        // Create an image file name
        String imageDate = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(timestamp));
        String imageFileName = String.format(PICTURE_NAME_TEMPLATE, imageDate);
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), PICTURES_DIR_NAME);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        File imageFile = new File(storageDir, imageFileName);
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

    public static String capturePictureLabel(final Activity activity) {
        return capturePictureLabel(activity, new IStartable() {
            @Override
            public void startActivityForResult(Intent intent, int requestCode) {
                activity.startActivityForResult(intent, requestCode);
            }
        });
    }

    // From http://developer.android.com/training/camera/photobasics.html.
    private static String capturePictureLabel(Context context, IStartable startable) {
        // Starts a picture intent.
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = PictureUtils.createImageFile(AppSingleton.getInstance(context)
                        .getSensorEnvironment().getDefaultClock().getNow());
            } catch (IOException ex) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, ex.getMessage());
                }
            }
            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(context, context.getPackageName(),
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    // Needed to avoid security exception on KitKat.
                    takePictureIntent.setClipData(ClipData.newRawUri(null, photoUri));
                }
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                String pictureLabelPath = "file:" + photoFile.getAbsoluteFile();
                startable.startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
                return pictureLabelPath;
            }
        }
        return null;
    }

    private interface IStartable {
        void startActivityForResult(Intent intent, int requestCode);
    }

    // Assumes that the Camera permission always requires external storage.
    private static void cameraPermissionGranted(Activity activity, boolean granted) {
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
            String filePath = fileUrl;
            if (filePath.startsWith("file:")) {
                filePath = filePath.substring("file:".length());
            }
            Uri photoUri = FileProvider.getUriForFile(activity, activity.getPackageName(),
                    new File(filePath));
            intent.setDataAndType(photoUri, type);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // Needed to avoid security exception on KitKat.
                intent.setClipData(ClipData.newRawUri(null, photoUri));
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
