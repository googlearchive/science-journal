/* Custom SurfaceView that receives Camera preview frames and displays them. */
package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.support.annotation.IntDef;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


public class CameraPreview extends SurfaceView {

    private static final String TAG = "CameraPreview";

    @IntDef({ERROR_UNKNOWN, ERROR_INVALID_SURFACE_HOLDER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Error {
    }

    public static final int ERROR_UNKNOWN = 0;
    public static final int ERROR_INVALID_SURFACE_HOLDER = 1;


    private SurfaceHolder mHolder;
    private Camera mCamera;
    private SourceListener mSourceListener;

    public CameraPreview(Context context) {
        super(context);
        mHolder = getHolder();
        mSourceListener = new CameraPreview.SourceListener() {
            @Override
            public void onSourceError(int error, String errorMessage) {
                AccessibilityUtils.makeSnackbar(CameraPreview.this, errorMessage,
                        Snackbar.LENGTH_SHORT).show();
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Preview error: " + errorMessage);
                }
            }
        };

        // Warning: this callback is only invoked on SurfaceHolder updates if the CameraPreview
        // is visible.
        mHolder.addCallback(new SurfaceHolder.Callback() {

            public void surfaceCreated(SurfaceHolder holder) {
                // The Surface has been created, now tell the camera where to draw the preview.
                if (holder == null) {
                    mSourceListener.onSourceError(ERROR_INVALID_SURFACE_HOLDER,
                            "Creating camera preview failed; the surface holder was invalid.");
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
                    mSourceListener.onSourceError(ERROR_INVALID_SURFACE_HOLDER,
                            "Creating camera preview failed; the surface holder was invalid.");
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

    public void setCamera(Camera camera) {
        mCamera = camera;
    }

    public void removeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * An object listening for CameraPreview errors.
     */
    public interface SourceListener {


        /**
         * Called if there was an error in the CameraPreview .
         *
         * @param error        one of the {@link Error} values.
         * @param errorMessage human readable error message which will be displayed to the user
         */
        void onSourceError(@Error int error, String errorMessage);
    }


}
