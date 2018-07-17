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
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;

/** Activity that tells the user to explore their world. */
public class NotSignedInYetActivity extends AppCompatActivity {
  private static final String TAG = "NotSignedInYetActivity";
  private static final String FRAGMENT_TAG = "NotSignedInYet";

  public static boolean maybeLaunch(Context context) {
    AccountsProvider accountsProvider =
        WhistlePunkApplication.getAppServices(context).getAccountsProvider();
    if (accountsProvider.requireSignedInAccount() && !accountsProvider.isSignedIn()) {
      Intent intent = new Intent(context, NotSignedInYetActivity.class);
      context.startActivity(intent);
      return true;
    }

    // Return false to indicate to the caller that we did not launch NotSignedInYetActivity.
    return false;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_not_signed_in_yet);

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
                AppCompatActivity activity = NotSignedInYetActivity.this;
                if (WhistlePunkApplication.getAppServices(activity)
                    .getAccountsProvider()
                    .isSignedIn()) {
                  // While we were upgrading the database, the asynchronous code that fetches
                  // the current account finished and the current account is now signed in.
                  startActivity(new Intent(activity, MainActivity.class));
                  finish();
                } else {
                  // Let the user sign in.
                  if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
                    Fragment fragment = new NotSignedInYetFragment();
                    getSupportFragmentManager()
                        .beginTransaction()
                        .add(R.id.container, fragment, FRAGMENT_TAG)
                        .commit();
                  }
                }
              }
            });
  }
}
