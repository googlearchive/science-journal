package com.google.android.apps.forscience.whistlepunk;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;

/**
 * Static picture functions shared across many parts of the app.
 */
public class PictureUtils {
    public static final String TAG = "PicturUtils";

    // Links a photo-taking request intent with the onActivityResult by requestType.
    public static final int REQUEST_TAKE_PHOTO = 1;
    public static final int PERMISSIONS_WRITE_EXTERNAL_STORAGE = 2;
    public static final int PERMISSIONS_CAMERA = 3;

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
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                    && !forceRetry) {
                // Then the user didn't explicitly ask for us to retry the permission,
                // so we won't do anything.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(activity, new String[]{permission},
                        permissionType);
            }
            return false;
        }
        return true;
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
