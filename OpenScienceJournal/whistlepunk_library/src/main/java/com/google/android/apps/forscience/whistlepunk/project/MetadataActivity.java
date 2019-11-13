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

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewActivity;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewDeprecatedActivity;

/**
 * Activity which should not be usable if we are currently recording.
 *
 * @deprecated Only used by {@link RunReviewDeprecatedActivity} Logic is duplicated in {@link
 *     RunReviewActivity}
 */
@Deprecated
public abstract class MetadataActivity extends AppCompatActivity {
  private static final String TAG = "MetadataActivity";

  protected abstract AppAccount getAppAccount();

  @Override
  protected void onResume() {
    super.onResume();

    final RecorderController recorderController =
        AppSingleton.getInstance(this).getRecorderController(getAppAccount());

    recorderController
        .watchRecordingStatus()
        .firstElement()
        .subscribe(
            status -> {
              boolean recording = status.isRecording();
              if (recording) {
                finish();
              }
            });
  }
}
