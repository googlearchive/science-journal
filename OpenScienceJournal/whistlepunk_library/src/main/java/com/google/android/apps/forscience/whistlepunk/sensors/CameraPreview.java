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

/* Custom SurfaceView that receives Camera preview frames and displays them. */
package com.google.android.apps.forscience.whistlepunk.sensors;

import static androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL;
import static androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90;
import static androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import androidx.exifinterface.media.ExifInterface;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.PanesBottomSheetBehavior;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.material.snackbar.Snackbar;
import io.reactivex.Maybe;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class CameraPreview extends SurfaceView {
  private static final String TAG = "CameraPreview";

  private Camera camera;
  private boolean previewStarted = false;
  private boolean shouldChopPictures = false;

  public CameraPreview(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  public CameraPreview(Context context) {
    super(context);
    init();
  }

  private void init() {
    // Warning: this callback is only invoked on SurfaceHolder updates if the CameraPreview
    // is visible.
    getHolder()
        .addCallback(
            new SurfaceHolder.Callback() {
              public void surfaceCreated(SurfaceHolder holder) {
                // The Surface has been created, now tell the camera where to draw the preview.
                if (holder == null) {

                  displayError("Creating camera preview failed; the surface holder was invalid.");
                  return;
                }
                setupPreviewDisplay(holder);
              }

              public void surfaceDestroyed(SurfaceHolder holder) {
                stopPreview();
              }

              public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
                if (holder.getSurface() == null) {
                  return;
                }

                stopPreview();

                // set preview size and make any resize, rotate or reformatting changes here

                setupPreviewDisplay(holder);
              }
            });
  }

  private void stopPreview() {
    if (camera != null) {
      camera.stopPreview();
    }
    previewStarted = false;
  }

  private int getDisplayRotation() {
    WindowManager manager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
    return manager.getDefaultDisplay().getRotation();
  }

  public void displayError(String errorMessage) {
    AccessibilityUtils.makeSnackbar(this, errorMessage, Snackbar.LENGTH_SHORT).show();
    if (Log.isLoggable(TAG, Log.ERROR)) {
      Log.e(TAG, "Preview error: " + errorMessage);
    }
  }

  public void setCamera(Camera camera) {
    this.camera = camera;
    if (!previewStarted) {
      setupPreviewDisplay(getHolder());
    }
    requestLayout();
  }

  public void setupPreviewDisplay(SurfaceHolder holder) {
    try {
      if (camera != null) {
        camera.setPreviewDisplay(holder);
        startPreview();
        setCameraDisplayOrientation(0, camera);
      }
    } catch (IOException e) {
      displayError("Creating camera preview failed; the surface holder was invalid.");
    }
  }

  private void startPreview() {
    camera.startPreview();
    previewStarted = true;
  }

  private void setCameraDisplayOrientation(int cameraId, Camera camera) {
    CameraInfo info = new CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    int degrees = 0;
    switch (getDisplayRotation()) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
    }

    int result;
    if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
      result = (info.orientation + degrees) % 360;
      result = (360 - result) % 360; // compensate the mirror
    } else { // back-facing
      result = (info.orientation - degrees + 360) % 360;
    }

    // Adjust orientation of taken photo
    Camera.Parameters params = this.camera.getParameters();
    params.setRotation(result);
    this.camera.setParameters(params);

    // Adjust orientation of preview
    camera.setDisplayOrientation(result);
  }

  public void removeCamera() {
    if (camera != null) {
      stopPreview();
      camera.release();
      camera = null;
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (camera != null) {
      adjustPreviewSizeAndShrinkToMatchRatio();
    }
  }

  public static class PreviewSize {
    public PreviewSize(int width, int height) {
      this.width = width;
      this.height = height;
    }

    public int width;
    public int height;

    public void setFrom(Camera.Size size) {
      this.width = size.width;
      this.height = size.height;
    }

    @Override
    public String toString() {
      return this.width + "," + this.height;
    }
  }

  // To avoid allocation when preview sizes are the same (which I assume they'll often be)
  private PreviewSize[] cachedSupportedSizes = new PreviewSize[0];

  private void adjustPreviewSizeAndShrinkToMatchRatio() {
    Camera.Parameters params = camera.getParameters();
    int idealWidth = getMeasuredWidth();
    int idealHeight = getMeasuredHeight();
    double idealRatio = (1.0 * idealHeight) / idealWidth;

    // Sizes come out as larger-dimension first regardless of orientation, which makes them
    // weird if we're in portrait mode, so we have to flip them in that case
    // TODO: is this still right on a Chromebook?

    boolean flipSizes = isInPortrait();
    List<Camera.Size> sizeOptions = params.getSupportedPreviewSizes();

    PreviewSize bestSize = getBestSize(idealRatio, flipSizes, getPreviewSizes(sizeOptions));

    if (bestSize == null) {
      return;
    }

    // Bake in the new preview size
    params.setPreviewSize(bestSize.width, bestSize.height);
    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    try {
      stopPreview();
      camera.setParameters(params);
    } catch (RuntimeException e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        String msg = "Failure setting camera size to " + bestSize.width + "," + bestSize.height;
        Log.e(TAG, msg, e);
        return;
      }
    } finally {
      // TODO(b/67042632) Why does this fail sometimes? Are we rotating while a camera capture
      // is in progress maybe?
      try {
        startPreview();
      } catch (RuntimeException e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, "Failure to start camera preview", e);
          //noinspection ReturnInsideFinallyBlock
          return;
        }
      }
    }

    // Remeasure to match ideal
    double ratio = 1.0 * bestSize.height / bestSize.width;

    if (flipSizes) {
      ratio = 1.0 / ratio;
    }

    // always use full-width
    setMeasuredDimension(idealWidth, (int) (idealHeight * ratio / idealRatio));
  }

  private PreviewSize[] getPreviewSizes(List<Camera.Size> sizeOptions) {
    if (cachedSupportedSizes.length != sizeOptions.size()) {
      cachedSupportedSizes = new PreviewSize[sizeOptions.size()];
      for (int i = 0; i < cachedSupportedSizes.length; i++) {
        cachedSupportedSizes[i] = new PreviewSize(0, 0);
      }
    }
    for (int i = 0; i < sizeOptions.size(); i++) {
      cachedSupportedSizes[i].setFrom(sizeOptions.get(i));
    }
    return cachedSupportedSizes;
  }

  /**
   * @param idealRatio the ideal ratio (height / width)
   * @param flipSizes if the options need to be flipped (so that height becomes width and vice
   *     versa) in order to be correctly matched with the idealRatio (because of oddity in how
   *     camera API handles rotation)
   * @param sizeOptions the optional sizes
   * @return the best size. "Best size" means the most preview pixels visible on screen, _minus_ the
   *     number of preview-sized pixels needed to letter box to the current size. This is heuristic,
   *     and should choose high-resolution previews, with letterbox minimization as a lower-priority
   *     tiebreaker
   */
  @Nullable
  @VisibleForTesting
  public static PreviewSize getBestSize(
      double idealRatio, boolean flipSizes, PreviewSize... sizeOptions) {
    PreviewSize bestSize = null;
    double bestPixels = 0;

    for (PreviewSize size : sizeOptions) {
      int goodPixels = size.height * size.width;

      int testHeight = !flipSizes ? size.height : size.width;
      int testWidth = !flipSizes ? size.width : size.height;
      double ratio = (1.0 * testHeight) / testWidth;
      double ratioMatch = Math.min(ratio, idealRatio) / Math.max(ratio, idealRatio);

      // How many pixels worth of letterboxing?
      double badPixels = goodPixels * (1 - ratioMatch);
      double pixelScore = goodPixels - badPixels;

      if (pixelScore > bestPixels) {
        bestSize = size;
        bestPixels = pixelScore;
      }
    }
    return bestSize;
  }

  private boolean isInPortrait() {
    int rotation = getDisplayRotation();
    return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180;
  }

  public void setCurrentDrawerState(int state) {
    shouldChopPictures = (state == PanesBottomSheetBehavior.STATE_MIDDLE);
  }

  public void takePicture(
      AppAccount appAccount,
      Maybe<String> maybeExperimentId,
      String uuid,
      final MaybeConsumer<String> onSuccess) {
    // TODO: better strategy (RxJava?) to avoid these null checks
    if (camera == null) {
      onSuccess.fail(new IllegalStateException("No camera loaded in CameraPreview"));
    }
    maybeExperimentId.subscribe(
        experimentId -> takePicture(appAccount, experimentId, uuid, shouldChopPictures, onSuccess));
  }

  private void takePicture(
      AppAccount appAccount,
      String experimentId,
      String uuid,
      boolean chopInHalf,
      MaybeConsumer<String> onSuccess) {
    final File photoFile =
        PictureUtils.createImageFile(getContext(), appAccount, experimentId, uuid);

    try {
      this.camera.takePicture(
          null,
          null,
          null,
          (data, camera) -> {
            byte[] bytes = getRightBytes(data, chopInHalf);

            try (FileOutputStream out = new FileOutputStream(photoFile)) {
              out.write(bytes);
              // Pass back the relative path for saving in the label.
              onSuccess.success(
                  FileMetadataUtil.getInstance()
                      .getRelativePathInExperiment(experimentId, photoFile));
              startPreview();
              // If the original, uncropped photo was rotated in EXIF, we have to re-rotate it
              // by getting an ExifInterface to the new JPEG and writing the rotation
              // EXIF tag. We can't write this to the uncompressed bytes earlier on,
              // unfortunately.
              if (getOrientation(data) == ExifInterface.ORIENTATION_ROTATE_90) {
                String path = photoFile.getPath();
                ExifInterface exif = new ExifInterface(path);
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    Integer.toString(ExifInterface.ORIENTATION_ROTATE_90));
                exif.saveAttributes();
              }
              // NPE occurs when data == null (unclear what causes that case)
            } catch (IOException | NullPointerException e) {
              // Delete the file if we failed to write to it
              if (photoFile.exists()) {
                photoFile.delete();
              }
              onSuccess.fail(e);
            }
          });
    } catch (RuntimeException e) {
      // TODO: why is this so common?  Do we need to disable the picture button for a while
      //       after taking a picture?
      onSuccess.fail(e);
    }
  }

  private byte[] getRightBytes(byte[] data, boolean chopInHalf) {
    if (!chopInHalf) {
      return data;
    }

    int orientation = getOrientation(data);
    switch (orientation) {
      case (ORIENTATION_ROTATE_90):
        // Chop off the right half.
        try {
          Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
          Bitmap halfBitmap =
              Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth() / 2, bitmap.getHeight());
          ByteArrayOutputStream stream = new ByteArrayOutputStream();
          halfBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
          return stream.toByteArray();
        } catch (Throwable t) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "exception when chopping image, storing in full size", t);
          }
          return data;
        }

      case (ORIENTATION_NORMAL): // Intentional Fallthrough.
      case (ORIENTATION_UNDEFINED):
        // Chop off the bottom half.
        try {
          Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
          Bitmap halfBitmap =
              Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight() / 2);
          ByteArrayOutputStream stream = new ByteArrayOutputStream();
          halfBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
          return stream.toByteArray();
        } catch (Throwable t) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "exception when chopping image, storing in full size", t);
          }
          return data;
        }

      default:
        // If rotated in EXIF, but not 90 degrees, don't crop.
        return data;
    }
  }

  private int getOrientation(byte[] data) {
    ExifInterface exif = null;
    try {
      exif = new ExifInterface(new ByteArrayInputStream(data));
    } catch (IOException e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "exif parsing", e);
      }
      return ExifInterface.ORIENTATION_UNDEFINED;
    }
    return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
  }
}
