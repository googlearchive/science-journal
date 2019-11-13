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
import androidx.fragment.app.Fragment;
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderService;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.project.MetadataActivity;

/** @deprecated Moving to {@link RunReviewActivity}. */
@Deprecated
public class RunReviewDeprecatedActivity extends MetadataActivity {
  private static final String FRAGMENT_TAG = "fragment";
  private boolean fromRecord;
  private AppAccount appAccount;

  /**
   * Launches a new run review activity
   *
   * @param startLabelId The ID of the start run label.
   * @param activeSensorIndex The index of the sensor which ought to be displayed first.
   * @param fromRecord Whether we reached the RunReview activity from recording directly or from
   *     another part of the app.
   * @param options Options bundle for launch
   */
  public static void launch(
      Context context,
      AppAccount appAccount,
      String startLabelId,
      String experimentId,
      int activeSensorIndex,
      boolean fromRecord,
      boolean createTask,
      boolean claimExperimentsMode,
      Bundle options) {
    final Intent intent =
        createLaunchIntent(
            context,
            appAccount,
            startLabelId,
            experimentId,
            activeSensorIndex,
            fromRecord,
            createTask,
            claimExperimentsMode);
    context.startActivity(intent, options);
  }

  /**
   * Returns a new run review activity intent
   *
   * @param startLabelId The ID of the start run label.
   * @param activeSensorIndex The index of the sensor which ought to be displayed first.
   * @param fromRecord Whether we reached the RunReview activity from recording directly or from
   *     another part of the app.
   */
  public static Intent createLaunchIntent(
      Context context,
      AppAccount appAccount,
      String startLabelId,
      String experimentId,
      int activeSensorIndex,
      boolean fromRecord,
      boolean createTask,
      boolean claimExperimentsMode) {
    final Intent intent = new Intent(context, RunReviewDeprecatedActivity.class);
    intent.putExtra(RunReviewFragment.ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(RunReviewFragment.ARG_EXPERIMENT_ID, experimentId);
    intent.putExtra(RunReviewFragment.ARG_START_LABEL_ID, startLabelId);
    intent.putExtra(RunReviewFragment.ARG_SENSOR_INDEX, activeSensorIndex);
    intent.putExtra(RunReviewActivity.EXTRA_FROM_RECORD, fromRecord);
    intent.putExtra(RunReviewActivity.EXTRA_CREATE_TASK, createTask);
    intent.putExtra(RunReviewFragment.ARG_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WhistlePunkApplication.getPerfTrackerProvider(this).onActivityInit();
    setContentView(R.layout.activity_run_review);
    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    fromRecord = getIntent().getExtras().getBoolean(RunReviewActivity.EXTRA_FROM_RECORD, false);
    appAccount =
        WhistlePunkApplication.getAccount(this, getIntent(), RunReviewFragment.ARG_ACCOUNT_KEY);

    if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
      RunReviewFragment fragment =
          RunReviewFragment.newInstance(
              appAccount,
              getIntent().getExtras().getString(RunReviewFragment.ARG_EXPERIMENT_ID),
              getIntent().getExtras().getString(RunReviewFragment.ARG_START_LABEL_ID),
              getIntent().getExtras().getInt(RunReviewFragment.ARG_SENSOR_INDEX),
              getIntent().getExtras().getBoolean(RunReviewActivity.EXTRA_CREATE_TASK, true),
              getIntent().getExtras().getBoolean(RunReviewFragment.ARG_CLAIM_EXPERIMENTS_MODE));
      getSupportFragmentManager()
          .beginTransaction()
          .add(R.id.container, fragment, FRAGMENT_TAG)
          .commit();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    RecorderService.clearRecordingCompletedNotification(getApplicationContext());
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String permissions[], int[] grantResults) {
    PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
    if (fragment != null) {
      fragment.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onBackPressed() {
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
    if (fragment != null) {
      // If the edit time dialog is showing, make it hide on back pressed.
      EditLabelTimeDialog editLabelTimeDialog =
          (EditLabelTimeDialog)
              fragment.getChildFragmentManager().findFragmentByTag(EditLabelTimeDialog.TAG);
      if (editLabelTimeDialog != null) {
        editLabelTimeDialog.dismiss();
        return;
      }
    }
    super.onBackPressed();
  }

  @Override
  protected AppAccount getAppAccount() {
    return appAccount;
  }

  boolean isFromRecord() {
    return fromRecord;
  }
}
