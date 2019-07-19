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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.fragment.app.Fragment;
import androidx.core.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Static picture functions shared across many parts of the app. */
public class PictureUtils {
  public static final String TAG = "PictureUtils";

  // Links a photo-taking request intent with the onActivityResult by requestType.
  public static final int REQUEST_TAKE_PHOTO = ActivityRequestCodes.REQUEST_TAKE_PHOTO;
  public static final int REQUEST_SELECT_PHOTO = ActivityRequestCodes.REQUEST_SELECT_PHOTO;

  private static final String PICTURE_NAME_TEMPLATE = "%s.jpg";

  // From http://developer.android.com/training/camera/photobasics.html.
  public static File createImageFile(
      Context context, AppAccount appAccount, String experimentId, String uuid) {
    // Create an image file name using the uuid of the item it is attached to.
    String imageFileName = String.format(PICTURE_NAME_TEMPLATE, uuid);
    File storageDir = FileMetadataUtil.getInstance().getAssetsDirectory(appAccount, experimentId);
    return new File(storageDir, imageFileName);
  }

  /**
   * Tries to capture a picture label using the default camera app.
   *
   * @return The relative path to the picture in the experiment.
   */
  public static String capturePictureLabel(
      final Activity activity, AppAccount appAccount, String experimentId, String uuid) {
    return capturePictureLabel(
        activity,
        appAccount,
        experimentId,
        uuid,
        new IStartable() {
          @Override
          public void startActivityForResult(Intent intent, int requestCode) {
            activity.startActivityForResult(intent, requestCode);
          }
        });
  }

  // From http://developer.android.com/training/camera/photobasics.html.
  private static String capturePictureLabel(
      Context context,
      AppAccount appAccount,
      String experimentId,
      String uuid,
      IStartable startable) {
    // Starts a picture intent.
    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
      File photoFile = PictureUtils.createImageFile(context, appAccount, experimentId, uuid);
      if (photoFile != null) {
        Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName(), photoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, contentUri);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startable.startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
        return FileMetadataUtil.getInstance().getRelativePathInExperiment(experimentId, photoFile);
      }
    }
    return null;
  }

  private interface IStartable {
    void startActivityForResult(Intent intent, int requestCode);
  }

  public static void launchExternalEditor(
      Activity activity, AppAccount appAccount, String experimentId, String relativeFilePath) {
    File file =
        FileMetadataUtil.getInstance()
            .getExperimentFile(appAccount, experimentId, relativeFilePath);
    String extension = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
    String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    if (!TextUtils.isEmpty(type)) {
      Intent intent = new Intent(Intent.ACTION_EDIT);
      Uri photoUri = FileProvider.getUriForFile(activity, activity.getPackageName(), file);
      intent.setDataAndType(photoUri, type);
      intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      try {
        activity.startActivity(
            Intent.createChooser(
                intent, activity.getResources().getString(R.string.photo_editor_dialog_title)));
      } catch (ActivityNotFoundException e) {
        Log.e(TAG, "No activity found to handle this " + file.getAbsolutePath() + " type " + type);
      }
    } else {
      Log.w(TAG, "Could not find mime type for " + file.getAbsolutePath());
    }
  }

  public static void loadExperimentImage(
      Context context,
      ImageView view,
      AppAccount appAccount,
      String experimentId,
      String relativeFilePath,
      boolean scale) {
    if (isDestroyed(context)) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Trying to load image for destroyed context");
      }
      // Nothing we can do, return
      return;
    }
    File file =
        FileMetadataUtil.getInstance()
            .getExperimentFile(appAccount, experimentId, relativeFilePath);
    if (scale) {
      // Use last modified time as part of the signature to force a glide cache refresh.
      GlideApp.with(context)
          .load(file.getAbsolutePath())
          .placeholder(R.drawable.placeholder)
          .signature(new ObjectKey(file.getPath() + file.lastModified()))
          .centerCrop()
          // caches only the final image, after reducing the resolution
          .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
          .into(view);
    } else {
      // Use last modified time as part of the signature to force a glide cache refresh.
      GlideApp.with(context)
          .load(file.getAbsolutePath())
          .placeholder(R.drawable.placeholder)
          .signature(new ObjectKey(file.getPath() + file.lastModified()))
          .fitCenter()
          // caches only the final image, after reducing the resolution
          .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
          .into(view);
    }
  }

  private static boolean isDestroyed(Context context) {
    if (context instanceof Activity) {
      Activity activity = (Activity) context;
      return activity.isDestroyed();
    }
    return false;
  }

  public static void clearImage(ImageView image) {
    GlideApp.with(image).clear(image);
  }

  public static String getExperimentImagePath(
      Context context, AppAccount appAccount, String experimentId, String relativeFilePath) {
    File file =
        FileMetadataUtil.getInstance()
            .getExperimentFile(appAccount, experimentId, relativeFilePath);
    return file.getAbsolutePath();
  }

  public static String getExperimentOverviewRelativeImagePath(
      String experimentId, String relativeFilePath) {
    return FileMetadataUtil.getInstance()
        .getRelativePathInFilesDir(experimentId, relativeFilePath)
        .toString();
  }

  /**
   * The experiment overview has a relative file path to the root directory of internal storage,
   * i.e. it already includes experiments/experiment_id/assets as well as the filename. This
   * function prepends the root directory of internal storage to that relative path.
   */
  public static String getExperimentOverviewFullImagePath(
      AppAccount appAccount, String relativeFilePath) {
    return FileMetadataUtil.getInstance().getFilesDir(appAccount) + "/" + relativeFilePath;
  }

  public static void loadExperimentOverviewImage(
      AppAccount appAccount, ImageView imageView, String experimentOverviewFilePath) {
    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    String fullPath =
        PictureUtils.getExperimentOverviewFullImagePath(appAccount, experimentOverviewFilePath);
    File file = new File(fullPath);
    Context context = imageView.getContext();
    GlideApp.with(context)
        .load(fullPath)
        .placeholder(R.drawable.experiment_card_placeholder)
        // Create a signature based on the last modified time so that cached images will
        // not be used if the underlying file changes. This may happen if the user has
        // picked an experiment photo from the "edit experiment" page because there is only
        // one filename used for that photo.
        .signature(new ObjectKey(file.getPath() + file.lastModified()))
        .into(imageView);
  }

  public static void launchPhotoPicker(Fragment fragment) {
    Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
    photoPickerIntent.addCategory(Intent.CATEGORY_OPENABLE);
    photoPickerIntent.setType("image/*");
    fragment.startActivityForResult(photoPickerIntent, REQUEST_SELECT_PHOTO);
  }

  public static boolean writeDrawableToFile(Context context, File pictureFile, int drawableId) {
    // Populate the file with the bitmap!
    Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawableId);
    try (FileOutputStream outputStream = new FileOutputStream(pictureFile)) {
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
      outputStream.flush();
      outputStream.close();
      return true;
    } catch (IOException ex) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, ex.getMessage());
      }
      return false;
    }
  }
}
