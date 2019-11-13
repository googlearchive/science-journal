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

package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;

public class UpdateRunActivity extends AppCompatActivity {

  private static final String FRAGMENT_TAG = "fragment";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_update_run);
    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
      AppAccount appAccount =
          WhistlePunkApplication.getAccount(this, getIntent(), UpdateRunFragment.ARG_ACCOUNT_KEY);
      String runId = getIntent().getExtras().getString(UpdateRunFragment.ARG_RUN_ID);
      String experimentId = getIntent().getExtras().getString(UpdateRunFragment.ARG_EXP_ID);
      UpdateRunFragment fragment = UpdateRunFragment.newInstance(appAccount, runId, experimentId);
      fragment.setRetainInstance(true);

      getSupportFragmentManager()
          .beginTransaction()
          .add(R.id.container, fragment, FRAGMENT_TAG)
          .commit();
    }
  }

  public static void launch(
      Context context, AppAccount appAccount, String runId, String experimentId) {
    final Intent intent = new Intent(context, UpdateRunActivity.class);
    intent.putExtra(UpdateRunFragment.ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(UpdateRunFragment.ARG_RUN_ID, runId);
    intent.putExtra(UpdateRunFragment.ARG_EXP_ID, experimentId);
    context.startActivity(intent);
  }
}
