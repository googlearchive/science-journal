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
    private int mRecorderListenerId = RecorderController.NO_LISTENER_ID;

    @Override
    protected void onResume() {
        super.onResume();

        AppSingleton.getInstance(this).withRecorderController(TAG,
                new Consumer<RecorderController>() {
                    @Override
                    public void take(final RecorderController recorderController) {
                        RecorderController.RecordingStateListener listener =
                                new RecorderController.RecordingStateListener() {
                                    @Override
                                    public void onRecordingStateChanged(
                                            RecordingMetadata currentRecording) {
                                        if (mRecorderListenerId
                                            != RecorderController.NO_LISTENER_ID) {
                                            recorderController.removeRecordingStateListener(
                                                    mRecorderListenerId);
                                            mRecorderListenerId = RecorderController.NO_LISTENER_ID;
                                        }
                                        if (currentRecording != null) {
                                            finish();
                                        }
                                    }

                                    @Override
                                    public void onRecordingStartFailed(
                                            @RecorderController.RecordingStartErrorType int type,
                                            Exception e) {

                                    }

                                    @Override
                                    public void onRecordingStopFailed(
                                            @RecorderController.RecordingStopErrorType int type) {

                                    }
                                };

                        mRecorderListenerId =
                                recorderController.addRecordingStateListener(listener);
                    }
                });
    }
}
