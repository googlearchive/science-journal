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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SnackbarManager;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.material.snackbar.Snackbar;

/** Activity that lets the user choose what to do with their old experiments. */
public class OldUserOptionPromptActivity extends AppCompatActivity {
  private static final String TAG = "OldUserOptionPrompt";
  private static final String KEY_SHOULD_LAUNCH =
      "key_should_launch_old_user_option_prompt_activity";
  private final SnackbarManager snackbarManager = new SnackbarManager();

  private int unclaimedExperimentCount;

  public static boolean shouldLaunch(Context context) {
    // Investigate and protect from NullPointerException, reported in b/129352553 and b/133151247.
    try {
      if (context == null) {
        return false;
      }
      if (!WhistlePunkApplication.hasAppServices(context)) {
        return false;
      }

      AccountsProvider accountsProvider =
          WhistlePunkApplication.getAppServices(context).getAccountsProvider();
      if (accountsProvider == null) {
        WhistlePunkApplication.getUsageTracker(context)
            .trackEvent(
                TrackerConstants.CATEGORY_SIGN_IN,
                TrackerConstants.ACTION_ERROR,
                "shouldLaunch: unexpected error, accountsProvider is null",
                0);
        return false;
      }

      if (accountsProvider.isSignedIn()
          && AccountsUtils.getUnclaimedExperimentCount(context) >= 1) {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(KEY_SHOULD_LAUNCH, true);
      }
    } catch (Exception e) {
      WhistlePunkApplication.getUsageTracker(context)
          .trackEvent(
              TrackerConstants.CATEGORY_SIGN_IN,
              TrackerConstants.ACTION_ERROR,
              TrackerConstants.createLabelFromStackTrace(e),
              0);
    }
    return false;
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

    setContentView(R.layout.activity_old_user_option_prompt);
    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    findViewById(R.id.drive_view).setOnClickListener(v -> showMoveAllExperimentsPrompt());
    findViewById(R.id.delete_view).setOnClickListener(v -> showDeleteAllPrompt());
    findViewById(R.id.select_view).setOnClickListener(v -> pickAndChooseExperiments());

    unclaimedExperimentCount = AccountsUtils.getUnclaimedExperimentCount(this);
    TextView text = findViewById(R.id.prompt_text);
    text.setText(
        getResources()
            .getQuantityString(
                R.plurals.old_user_option_prompt_text,
                unclaimedExperimentCount,
                unclaimedExperimentCount));
  }

  private void showMoveAllExperimentsPrompt() {
    long mbFree = FileMetadataUtil.getInstance().getFreeSpaceInMb();
    if (mbFree < 100) {
      Snackbar bar =
          AccessibilityUtils.makeSnackbar(
              findViewById(R.id.prompt_text),
              getResources().getString(R.string.claim_failed_disk_space),
              Snackbar.LENGTH_LONG);
      snackbarManager.showSnackbar(bar);
      return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(
        getResources()
            .getQuantityString(
                R.plurals.claim_all_confirmation_text,
                unclaimedExperimentCount,
                unclaimedExperimentCount));
    builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
    builder.setPositiveButton(
        R.string.claim_all_confirmation_yes,
        (dialog, which) -> {
          moveAllExperimentsToCurrentAccount();
          WhistlePunkApplication.getUsageTracker(this)
              .trackEvent(
                  TrackerConstants.CATEGORY_CLAIMING_DATA,
                  TrackerConstants.ACTION_CLAIM_ALL,
                  null,
                  0);
          dialog.dismiss();
        });
    builder.create().show();
  }

  private void moveAllExperimentsToCurrentAccount() {
    WhistlePunkApplication.getAppServices(this)
        .getAccountsProvider()
        .getObservableCurrentAccount()
        .firstElement()
        .subscribe(this::moveAllExperiments);
  }

  private void moveAllExperiments(AppAccount currentAccount) {
    // Move all experiments from unclaimed to the current account.
    NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(this);
    DataController dataController =
        AppSingleton.getInstance(this).getDataController(nonSignedInAccount);
    // TODO(lizlooney): handle errors. From saff during code review: Since this is such an
    // important part of data ownership, I think we might want to explicitly use a MaybeConsumer
    // with a Toast onError so the user is notified if for some reason this doesn't work (most
    // likely cause I can think of is disk space pressure).
    dataController.moveAllExperimentsToAnotherAccount(
        currentAccount,
        new LoggingConsumer<Success>(TAG, "moveAllExperiments") {
          @Override
          public void success(Success value) {
            setResult(RESULT_OK);
            finish();
          }

          @Override
          public void fail(Exception e) {
            String labelFromStackTrace = TrackerConstants.createLabelFromStackTrace(e);
            UsageTracker usageTracker =
                WhistlePunkApplication.getUsageTracker(OldUserOptionPromptActivity.this);
            usageTracker.trackEvent(
                TrackerConstants.CATEGORY_CLAIMING_DATA,
                TrackerConstants.ACTION_FAILED,
                labelFromStackTrace,
                0);
            usageTracker.trackEvent(
                TrackerConstants.CATEGORY_FAILURE,
                TrackerConstants.ACTION_CLAIM_FAILED,
                labelFromStackTrace,
                0);
            View view = findViewById(android.R.id.content);
            if (view != null) {
              AccessibilityUtils.makeSnackbar(
                      view, getResources().getString(R.string.claim_failed), Snackbar.LENGTH_LONG)
                  .show();
            }
            super.fail(e);
          }
        });
  }

  private void showDeleteAllPrompt() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.delete_all_prompt_headline);
    builder.setMessage(R.string.delete_all_prompt_text);
    builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
    builder.setPositiveButton(
        R.string.delete_all_prompt_yes,
        (dialog, which) -> {
          deleteAllExperiments();
          WhistlePunkApplication.getUsageTracker(this)
              .trackEvent(
                  TrackerConstants.CATEGORY_CLAIMING_DATA,
                  TrackerConstants.ACTION_DELETE_ALL,
                  null,
                  0);
          dialog.dismiss();
        });
    builder.create().show();
  }

  private void deleteAllExperiments() {
    NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(this);
    DataController dataController =
        AppSingleton.getInstance(this).getDataController(nonSignedInAccount);
    dataController.deleteAllExperiments(
        new LoggingConsumer<Success>(TAG, "deleteAllExperiments") {
          @Override
          public void success(Success value) {
            setResult(RESULT_OK);
            finish();
          }
        });
  }

  private void pickAndChooseExperiments() {
    WhistlePunkApplication.getUsageTracker(this)
        .trackEvent(
            TrackerConstants.CATEGORY_CLAIMING_DATA, TrackerConstants.ACTION_SELECT_LATER, null, 0);
    setResult(RESULT_OK);
    finish();
  }
}
