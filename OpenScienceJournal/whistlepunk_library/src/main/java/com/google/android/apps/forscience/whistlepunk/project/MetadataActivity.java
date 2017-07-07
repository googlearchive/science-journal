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

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.RecorderController;

/**
 * Activity which should not be usable if we are currently recording.
 */
public class MetadataActivity extends AppCompatActivity {
    private static final String TAG = "MetadataActivity";

    @Override
    protected void onResume() {
        super.onResume();

        final RecorderController recorderController =
                AppSingleton.getInstance(this).getRecorderController();

        recorderController.watchRecordingStatus().firstElement().subscribe(status -> {
            boolean recording = status.isRecording();
            if (recording) {
                finish();
            }
        });
    }
}
