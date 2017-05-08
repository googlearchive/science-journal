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

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class CameraPreview extends SurfaceView {
    private static final String TAG = "CameraPreview";

    private SurfaceHolder mHolder;
    private Camera mCamera;

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
        mHolder = getHolder();

        // Warning: this callback is only invoked on SurfaceHolder updates if the CameraPreview
        // is visible.
        mHolder.addCallback(new SurfaceHolder.Callback() {

            public void surfaceCreated(SurfaceHolder holder) {
                // The Surface has been created, now tell the camera where to draw the preview.
                if (holder == null) {

                    displayError("Creating camera preview failed; the surface holder was invalid.");
                    return;
                }
                setupPreviewDisplay(holder);
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mCamera != null) {
                    mCamera.stopPreview();
                }
            }

            public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
                if (holder.getSurface() == null) {
                    return;
                }

                if (mCamera != null) {
                    mCamera.stopPreview();
                }

                // set preview size and make any resize, rotate or reformatting changes here

                setupPreviewDisplay(holder);
            }

            public void setupPreviewDisplay(SurfaceHolder holder) {
                try {
                    if (mCamera != null) {
                        mCamera.setPreviewDisplay(holder);
                        mCamera.startPreview();
                        setCameraDisplayOrientation(0, mCamera);
                    }
                } catch (IOException e) {
                    displayError("Creating camera preview failed; the surface holder was invalid.");
                }
            }

            private void setCameraDisplayOrientation(int cameraId, Camera camera) {
                CameraInfo info = new CameraInfo();
                Camera.getCameraInfo(cameraId, info);
                WindowManager manager =
                        (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
                int rotation = manager.getDefaultDisplay().getRotation();
                int degrees = 0;
                switch (rotation) {
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
                    result = (360 - result) % 360;  // compensate the mirror
                } else {  // back-facing
                    result = (info.orientation - degrees + 360) % 360;
                }
                camera.setDisplayOrientation(result);
            }
        });
    }

    public void displayError(String errorMessage) {
        AccessibilityUtils.makeSnackbar(this, errorMessage, Snackbar.LENGTH_SHORT).show();
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Preview error: " + errorMessage);
        }
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
    }

    public void removeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    public void takePicture(long now, final MaybeConsumer<File> onSuccess) {
        // TODO: better strategy (RxJava?) to avoid these null checks
        if (mCamera == null) {
            onSuccess.fail(new IllegalStateException("No camera loaded in CameraPreview"));
        }
        try {
            final File photoFile = PictureUtils.createImageFile(now);
            final FileOutputStream out = new FileOutputStream(photoFile);

            mCamera.takePicture(null, null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    try {
                        out.write(data);
                        out.close();
                        onSuccess.success(photoFile);
                    } catch (IOException e) {
                        onSuccess.fail(e);
                    }
                }
            });
        } catch (IOException e) {
            onSuccess.fail(e);
        }
    }
}
