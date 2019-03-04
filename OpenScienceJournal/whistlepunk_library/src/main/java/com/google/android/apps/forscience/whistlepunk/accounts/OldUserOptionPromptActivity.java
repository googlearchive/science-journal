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
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;

/** Activity that lets the user choose what to do with their old experiments. */
public class OldUserOptionPromptActivity extends AppCompatActivity {
  private static final String TAG = "OldUserOptionPrompt";
  private static final String KEY_SHOULD_LAUNCH =
      "key_should_launch_old_user_option_prompt_activity";

  private int unclaimedExperimentCount;

  public static boolean shouldLaunch(Context context) {
    AccountsProvider accountsProvider =
        WhistlePunkApplication.getAppServices(context).getAccountsProvider();
    if (accountsProvider.isSignedIn() && AccountsUtils.getUnclaimedExperimentCount(context) >= 1) {
      return PreferenceManager.getDefaultSharedPreferences(context)
          .getBoolean(KEY_SHOULD_LAUNCH, true);
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
