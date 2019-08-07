/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.accounts;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;

/** Activity that tells the user to explore their world. */
public class GetStartedActivity extends AppCompatActivity {
  private static final String TAG = "GetStartedActivity";
  private static final String FRAGMENT_TAG = "GetStarted";

  private static final String KEY_SHOULD_LAUNCH = "key_should_launch_get_started_activity";

  public static boolean shouldLaunch(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(KEY_SHOULD_LAUNCH, true);
  }

  static void setShouldLaunch(Context context, boolean shouldLaunch) {
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putBoolean(KEY_SHOULD_LAUNCH, shouldLaunch)
        .commit();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_get_started);
    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    // Before letting the user sign in, get the DataController for the NonSignedInAccount and
    // call DataController.getLastUsedUnarchivedExperiment, which will upgrade the database, if
    // necessary.
    NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(this);
    AppSingleton.getInstance(this)
        .getDataController(nonSignedInAccount)
        .getLastUsedUnarchivedExperiment(
            new LoggingConsumer<Experiment>(
                TAG, "getting last used experiment to force database upgrade") {
              @Override
              public void success(Experiment experiment) {
                // If the activity has already been dismissed, don't bother with the fragment.
                if (isDestroyed()) {
                  return;
                }
                // Let the user sign in.
                if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
                  Fragment fragment = new GetStartedFragment();
                  getSupportFragmentManager()
                      .beginTransaction()
                      .add(R.id.container, fragment, FRAGMENT_TAG)
                      .commit();
                }
              }
            });
  }
}
