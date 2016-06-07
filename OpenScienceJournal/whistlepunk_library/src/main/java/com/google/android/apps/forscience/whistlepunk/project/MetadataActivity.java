package com.google.android.apps.forscience.whistlepunk.project;

import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

/**
 * Activity which should not be usable if we are currently recording.
 */
public class MetadataActivity extends AppCompatActivity {
    private static final String TAG = "MetadataActivity";

    @Override
    protected void onResume() {
        super.onResume();
        AppSingleton.getInstance(this).withRecorderController(TAG,
                new Consumer<RecorderController>() {
                    @Override
                    public void take(final RecorderController recorderController) {
                        recorderController.addRecordingStateListener(TAG,
                                new RecorderController.RecordingStateListener() {
                                    @Override
                                    public void onRecordingStateChanged(
                                            RecordingMetadata currentRecording) {
                                        recorderController.removeRecordingStateListener(TAG);
                                        if (currentRecording != null) {
                                            finish();
                                        }
                                    }
                                });
                    }
                });
    }
}
