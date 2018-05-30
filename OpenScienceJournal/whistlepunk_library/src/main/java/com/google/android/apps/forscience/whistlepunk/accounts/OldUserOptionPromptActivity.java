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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;

/** Activity that lets the user choose what to do with their old experiments. */
public class OldUserOptionPromptActivity extends AppCompatActivity {
  private static final String TAG = "OldUserOptionPrompt";
  private static final String EXTRA_ACCOUNT_KEY = "accountKey";

  private AppAccount appAccount;

  static void launch(Context context, AppAccount appAccount) {
    Intent intent = new Intent(context, OldUserOptionPromptActivity.class);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    context.startActivity(intent);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_old_user_option_prompt);

    appAccount = WhistlePunkApplication.getAccount(this, getIntent(), EXTRA_ACCOUNT_KEY);

    findViewById(R.id.drive_view).setOnClickListener(v -> moveAllExperiments());
    findViewById(R.id.delete_view).setOnClickListener(v -> showDeleteAllPrompt());
    findViewById(R.id.select_view).setOnClickListener(v -> pickAndChooseExperiments());

    int unclaimedExperimentCount = AccountsUtils.getUnclaimedExperimentCount(this);
    TextView thirdParagraph = findViewById(R.id.text_third_paragraph);
    thirdParagraph.setText(
        getResources()
            .getQuantityString(
                R.plurals.old_user_option_prompt_third_paragraph,
                unclaimedExperimentCount,
                unclaimedExperimentCount));
  }

  @Override
  public void onBackPressed() {
    launchMainActivity();
  }

  private void moveAllExperiments() {
    // Move all experiments from unclaimed to the current account.
    NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(this);
    DataController dataController =
        AppSingleton.getInstance(this).getDataController(nonSignedInAccount);
    // TODO(lizlooney): handle errors. From saff during code review: Since this is such an
    // important part of data ownership, I think we might want to explicitly use a MaybeConsumer
    // with a Toast onError so the user is notified if for some reason this doesn't work (most
    // likely cause I can think of is disk space pressure).
    dataController.moveAllExperimentsToAnotherAccount(
        appAccount,
        new LoggingConsumer<Success>(TAG, "moveAllExperiments") {
          @Override
          public void success(Success value) {
            launchMainActivity();
          }
        });
  }

  private void showDeleteAllPrompt() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.delete_all_prompt_headline);
    builder.setMessage(R.string.delete_all_prompt_text);
    builder.setNegativeButton(
        android.R.string.cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface d, int which) {
            d.cancel();
          }
        });
    builder.setPositiveButton(
        R.string.delete_all_prompt_yes,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface d, int which) {
            deleteAllExperiments();
            d.dismiss();
          }
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
            launchMainActivity();
          }
        });
  }

  private void pickAndChooseExperiments() {
    launchMainActivity();
  }

  private void launchMainActivity() {
    Intent intent = new Intent(this, MainActivity.class);
    finish();
    startActivity(intent);
  }
}
