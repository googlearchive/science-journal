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
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;

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

    private static final String PICTURE_NAME_TEMPLATE = "%s.jpg";

    // From http://developer.android.com/training/camera/photobasics.html.
    public static File createImageFile(Context context, String experimentId, String uuid) {
        // Create an image file name using the uuid of the item it is attached to.
        String imageFileName = String.format(PICTURE_NAME_TEMPLATE, uuid);
        File storageDir = FileMetadataManager.getAssetsDirectory(context, experimentId);
        File imageFile = new File(storageDir, imageFileName);
        return imageFile;
    }

    /**
     * Scan the file when adding or deleting so that media scanner aware apps like Gallery apps can
     * properly index the file.
     */
    // TODO: Delete this, it doesn't work in the new system. Instead, use DocumentProvider.
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

    /**
     * Tries to capture a picture label using the default camera app.
     * @return The relative path to the picture in the experiment.
     */
    public static String capturePictureLabel(final Activity activity, String experimentId,
            String uuid) {
        return capturePictureLabel(activity, experimentId, uuid, new IStartable() {
            @Override
            public void startActivityForResult(Intent intent, int requestCode) {
                activity.startActivityForResult(intent, requestCode);
            }
        });
    }

    // From http://developer.android.com/training/camera/photobasics.html.
    private static String capturePictureLabel(Context context, String experimentId, String uuid,
            IStartable startable) {
        // Starts a picture intent.
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
            File photoFile = null;
            photoFile = PictureUtils.createImageFile(context, experimentId, uuid);
            if (photoFile != null) {
                Uri contentUri = FileProvider.getUriForFile(context,
                        "com.google.android.apps.forscience.whistlepunk", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, contentUri);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    // Needed to avoid security exception on KitKat.
                    takePictureIntent.setClipData(ClipData.newRawUri(null, contentUri));
                }
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startable.startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
                return FileMetadataManager.getRelativePathInExperiment(experimentId, photoFile);
            }
        }
        return null;
    }

    private interface IStartable {
        void startActivityForResult(Intent intent, int requestCode);
    }

    public static void launchExternalViewer(Activity activity, String experimentId,
            String relativeFilePath) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        File file = FileMetadataManager.getExperimentFile(activity, experimentId, relativeFilePath);
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (!TextUtils.isEmpty(type)) {
            Uri photoUri = FileProvider.getUriForFile(activity, activity.getPackageName(), file);
            intent.setDataAndType(photoUri, type);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // Needed to avoid security exception on KitKat.
                intent.setClipData(ClipData.newRawUri(null, photoUri));
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found to handle this " + file.getAbsolutePath() + " type "
                        + type);
            }
        } else {
            Log.w(TAG, "Could not find mime type for " + file.getAbsolutePath());
        }
    }

    public static void loadExperimentImage(Context context, ImageView view, String experimentId,
            String relativeFilePath) {
        File file = FileMetadataManager.getExperimentFile(context, experimentId, relativeFilePath);
        Glide.clear(view);
        Glide.with(context).load(file.getAbsolutePath()).into(view);
    }

    public static String getExperimentImagePath(Context context, String experimentId,
            String relativeFilePath) {
        File file = FileMetadataManager.getExperimentFile(context, experimentId, relativeFilePath);
        return file.getAbsolutePath();
    }

    public static String getExperimentOverviewRelativeImagePath(String experimentId,
            String relativeFilePath) {
        return FileMetadataManager.getRelativePathInFilesDir(experimentId, relativeFilePath);
    }

    public static String getExperimentOverviewFullImagePath(Context context,
            String relativeFilePath) {
        return context.getFilesDir() + "/" + relativeFilePath;
    }
}
