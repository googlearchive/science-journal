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
import android.content.res.Resources.Theme;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.ContextThemeWrapper;
import com.google.android.apps.forscience.whistlepunk.AddMoreObservationNotesFragment;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.NoteTakingActivity;
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.RecorderService;
import com.google.android.apps.forscience.whistlepunk.RunReviewOverlay.OnTimestampChangeListener;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaItem;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;

/** Displays the experiment trial, notes for the trial, and the action bar to add more notes. */
public class RunReviewActivity extends NoteTakingActivity implements OnTimestampChangeListener {
  public static final String EXTRA_FROM_RECORD = "from_record_activity";
  public static final String EXTRA_CREATE_TASK = "create_task";
  private boolean fromRecord;
  private RunReviewFragment fragment;
  private AddMoreObservationNotesFragment moreObservationNotesFragment;

  /**
   * Launches a new recording review activity
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
    // TODO(saff): fancy schmancy material transition here (see specs)
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
   * Returns a new recording review activity intent
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
    final Intent intent = new Intent(context, RunReviewActivity.class);
    intent.putExtra(NoteTakingActivity.EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(NoteTakingActivity.EXTRA_EXPERIMENT_ID, experimentId);
    intent.putExtra(NoteTakingActivity.EXTRA_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
    intent.putExtra(EXTRA_FROM_RECORD, fromRecord);
    intent.putExtra(EXTRA_CREATE_TASK, createTask);
    intent.putExtra(RunReviewFragment.ARG_START_LABEL_ID, startLabelId);
    intent.putExtra(RunReviewFragment.ARG_SENSOR_INDEX, activeSensorIndex);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WhistlePunkApplication.getPerfTrackerProvider(this).onActivityInit();

    fromRecord = getIntent().getExtras().getBoolean(EXTRA_FROM_RECORD, false);
  }

  @Override
  protected Fragment getDefaultFragment() {
    if (fragment == null) {
      fragment =
          (RunReviewFragment) getSupportFragmentManager().findFragmentByTag(DEFAULT_FRAGMENT_TAG);
    }
    if (fragment == null) {
      fragment =
          RunReviewFragment.newInstance(
              appAccount,
              getIntent().getExtras().getString(NoteTakingActivity.EXTRA_EXPERIMENT_ID),
              getIntent().getExtras().getString(RunReviewFragment.ARG_START_LABEL_ID),
              getIntent().getExtras().getInt(RunReviewFragment.ARG_SENSOR_INDEX),
              getIntent().getExtras().getBoolean(EXTRA_CREATE_TASK, true),
              getIntent().getExtras().getBoolean(NoteTakingActivity.EXTRA_CLAIM_EXPERIMENTS_MODE));
    }
    return fragment;
  }

  @Override
  protected String getDefaultToolFragmentTag() {
    return DEFAULT_ADD_MORE_OBSERVATIONS_TAG;
  }

  @Override
  protected void onResume() {
    super.onResume();

    final RecorderController recorderController =
        AppSingleton.getInstance(this).getRecorderController(appAccount);

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

    RecorderService.clearRecordingCompletedNotification(getApplicationContext());
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String permissions[], int[] grantResults) {
    PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public Theme getActivityTheme() {
    return new ContextThemeWrapper(this, R.style.BlueActionAreaIcon).getTheme();
  }

  @Override
  public void onBackPressed() {
    if (!handleOnBackPressed()) {
      super.onBackPressed();
    }
  }

  private boolean handleOnBackPressed() {
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
    if (fragment != null) {
      // If the edit time dialog is showing, make it hide on back pressed.
      EditLabelTimeDialog editLabelTimeDialog =
          (EditLabelTimeDialog)
              fragment.getChildFragmentManager().findFragmentByTag(EditLabelTimeDialog.TAG);
      if (editLabelTimeDialog != null) {
        editLabelTimeDialog.dismiss();
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean handleDefaultFragmentOnBackPressed() {
    return handleOnBackPressed();
  }

  @Override
  protected String getTrialIdForLabel() {
    return getIntent().getExtras().getString(RunReviewFragment.ARG_START_LABEL_ID);
  }

  @Override
  protected void onLabelAdded(String trialId, Label label) {
    fragment.reloadAndScrollToLabel(label);
  }

  @Override
  protected long getTimestamp(Context context) {
    return ((RunReviewFragment) getDefaultFragment()).getTimestamp();
  }

  boolean isFromRecord() {
    return fromRecord;
  }

  @Override
  public ActionAreaItem[] getActionAreaItems() {
    ActionAreaItem[] actionAreaItems = {
      ActionAreaItem.NOTE, ActionAreaItem.CAMERA, ActionAreaItem.GALLERY
    };
    return actionAreaItems;
  }

  @Override
  protected Fragment newInstanceAddMoreObservationNotes() {
    moreObservationNotesFragment =
        AddMoreObservationNotesFragment.newInstance(true /* isRunReview */);
    return moreObservationNotesFragment;
  }

  @Override
  public void onTimestampChanged(long timestamp) {
    if (moreObservationNotesFragment != null) {
      moreObservationNotesFragment.updateTime(timestamp, fragment.getStartTimestamp());
    }
  }
}
