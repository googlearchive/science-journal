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

/* VideoStreamSource provides access to a camera and wires it to a CameraPreview. */
package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.StatsListener;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


@SuppressWarnings("deprecation")
public class VideoSensor extends SensorChoice {
    public static final String TAG = "VideoSensor";
    public static final String MEDIA_SUBDIRECTORY = "WhistlePunk";

    public static final String ID = "VIDEO_STREAM";
    private Camera mCamera = null;
    private CameraPreview mCameraPreview;
    private Context mContext;
    private MediaRecorder mMediaRecorder;
    private static final int VIDEO_RECORDING_WIDTH = 640;
    private static final int VIDEO_RECORDING_HEIGHT = 480;
    private static final int VIDEO_RECORDING_BITRATE = 1000000;
    // Frame rate for video preview (not recording)
    private static final int VIDEO_PREVIEW_FPS = 30;
    // Frame rate for video recording
    public static final float DEFAULT_VIDEO_RECORDING_FPS = 30.f;
    public float mFps = DEFAULT_VIDEO_RECORDING_FPS;

    // TODO: convert to not need Context on creation.
    public VideoSensor(Context context) {
        super(ID);
        mContext = context;
        mCamera = null;
    }

    public SensorPresenter createPresenter(final DataViewOptions dataViewOptions,
            NumberFormat statsNumberFormat, StatsListener statsListener) {
        mCameraPreview = new CameraPreview(mContext);

        clearParentViewsIfNecessary();
        return new SensorPresenter() {
            @Override
            public void startShowing(View contentView,
                    ExternalAxisController.InteractionListener interactionListener) {
                ((ViewGroup) contentView).addView(mCameraPreview);
            }

            @Override
            public void onPause() {

            }

            @Override
            public void onResume(long resetTime) {

            }

            @Override
            public void onLabelsChanged(List<Label> labels) {

            }

            @Override
            public void onGlobalXAxisChanged(long xMin, long xMax, boolean isPinnedToNow,
                    DataController dataController) {

            }

            @Override
            public double getMinY() {
                return 0;
            }

            @Override
            public double getMaxY() {
                return 0;
            }

            @Override
            public void onStopObserving() {

            }

            @Override
            public void onViewRecycled() {

            }

            @Override
            public void updateAudioSettings(boolean audioEnabled, String sonificationType) {

            }

            @Override
            public void setShowStatsOverlay(boolean showStatsOverlay) {

            }

            @Override
            public void updateStats(List<StreamStat> stats) {

            }

            @Override
            public void setYAxisRange(double minimumYAxisValue, double maximumYAxisValue) {

            }

            @Override
            public void resetView() {

            }

            @Override
            public void setTriggers(List<SensorTrigger> triggers) {

            }

            @Override
            public OptionsPresenter getOptionsPresenter() {
                return new VideoOptionsPresenter();
            }

            @Override
            public void onNewData(long timestamp, Bundle bundle) {
            }

            @Override
            public void onRecordingStateChange(boolean isRecording, long recordingStart) {

            }
        };
    }

    @Override
    public SensorRecorder createRecorder(Context context, SensorObserver observer,
            final SensorStatusListener listener, SensorEnvironment environment) {
        return new AbstractSensorRecorder() {
            @Override
            public void startObserving() {
                if (!isCameraAvailable(mContext)) {
                    listener.onSourceError(getId(), SensorStatusListener.ERROR_FAILED_TO_CONNECT,
                            "Your device does not have a camera.");
                }
                mCamera = getCameraInstance();
                if (mCamera == null) {
                    listener.onSourceError(getId(), SensorStatusListener.ERROR_FAILED_TO_CONNECT,
                            "Failed to open camera.  It may be in use by another application.");
                } else {
                    mCameraPreview.setCamera(mCamera);
                    listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
                }
            }

            @Override
            public void startRecording(String runId) {
                if (prepareVideoRecorder()) {
                    try {
                        mMediaRecorder.start();
                    } catch (IllegalStateException e) {
                        Log.d(TAG,
                                "IllegalStateException preparing MediaRecorder: " + e.getMessage());
                        releaseMediaRecorder();
                        return;
                    }
                } else {
                    Log.e(TAG, "Failed to prepare media recording.");
                }
            }

            @Override
            public void stopRecording(MaybeConsumer<Success> onSuccess) {
                mMediaRecorder.stop();
                releaseMediaRecorder();
            }

            @Override
            public void stopObserving() {
                clearParentViewsIfNecessary();
                releaseCamera();
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
            }

            @Override
            public void applyOptions(ReadableSensorOptions settings) {
                mFps = getFps(settings);
            }
        };
    }

    public static float getFps(ReadableSensorOptions prefs) {
        return prefs.getFloat(VideoOptionsPresenter.PREFS_KEY_VIDEO_RECORDING_FPS,
                DEFAULT_VIDEO_RECORDING_FPS);
    }

    public static boolean isCameraAvailable(Context context) {
        return (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
    }

    private void clearParentViewsIfNecessary() {
        if (mCameraPreview.getParent() != null) {
            ((ViewGroup) mCameraPreview.getParent()).removeAllViews();
        }
    }

    private void releaseCamera() {
        if (mCameraPreview != null) {
            mCameraPreview.removeCamera();
            mCameraPreview = null;
        }
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    public Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(0); // attempt to get the primary (rear-facing) camera.
        } catch (RuntimeException e) {
            // Empty body used to convert an exception into a null return.
        }
        return c; // returns null if camera is unavailable
    }

    /* Create a File for saving an image or video */
    private File getOutputMediaFile() {
        // TODO(dek): check that external storage is mounted using Environment
        // .getExternalStorageState() before doing this.

        File mediaStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                MEDIA_SUBDIRECTORY);

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath(), "VID_" + timeStamp + ".mp4");

        return mediaFile;
    }

    private boolean prepareVideoRecorder() {
        if (mCamera == null) {
            Log.d(TAG, "No camera available, cannot record.");
            return false;
        }
        if (mCameraPreview == null) {
            Log.d(TAG, "No camera preview available, cannot record.");
            return false;
        }
        mCamera.unlock();
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setCamera(mCamera);
        // Don't configure audio.
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        // Use a time lapse profile with high quality.
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH);
        mMediaRecorder.setProfile(profile);

        // TODO(dek): support different settings for video preview size and recording size.
        mMediaRecorder.setVideoFrameRate(VIDEO_PREVIEW_FPS);
        mMediaRecorder.setCaptureRate(mFps);
        mMediaRecorder.setOutputFile(getOutputMediaFile().toString());
        mMediaRecorder.setPreviewDisplay(mCameraPreview.getHolder().getSurface());

        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
        }
    }

}
