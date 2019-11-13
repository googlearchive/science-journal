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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;

public class UpdateExperimentActivity extends AppCompatActivity {

  private static final String FRAGMENT_TAG = "fragment";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_update_experiment);
    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
      AppAccount appAccount =
          WhistlePunkApplication.getAccount(
              this, getIntent(), UpdateExperimentFragment.ARG_ACCOUNT_KEY);
      String experimentId =
          getIntent().getExtras().getString(UpdateExperimentFragment.ARG_EXPERIMENT_ID);

      UpdateExperimentFragment fragment =
          UpdateExperimentFragment.newInstance(appAccount, experimentId);
      fragment.setRetainInstance(true);

      getSupportFragmentManager()
          .beginTransaction()
          .add(R.id.container, fragment, FRAGMENT_TAG)
          .commit();
    }
  }

  public static void launch(Context context, AppAccount appAccount, String experimentId) {
    final Intent intent = getLaunchIntent(context, appAccount, experimentId);
    context.startActivity(intent);
  }

  public static Intent getLaunchIntent(
      Context context, AppAccount appAccount, String experimentId) {
    final Intent intent = new Intent(context, UpdateExperimentActivity.class);
    intent.putExtra(UpdateExperimentFragment.ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(UpdateExperimentFragment.ARG_EXPERIMENT_ID, experimentId);
    return intent;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    if (fragment != null && requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
      fragment.onActivityResult(requestCode, resultCode, data);
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }
}
