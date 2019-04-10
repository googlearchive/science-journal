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

package com.google.android.apps.forscience.whistlepunk.intro;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.VisibleForTesting;
import android.support.design.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import java.util.Calendar;

public class AgeVerifier extends AppCompatActivity {

  /** Set to true to debug age verifier after going through it once. */
  private static final boolean DEBUG_AGE_VERIFIER = false;

  /**
   * Boolean key which stores whether the user was over 13 at the time they entered their birthday.
   */
  private static final String KEY_USER_OVER_13 = "user_over_13";

  private SharedPreferences preferences;
  private DatePicker datePicker;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_age_verifier);

    datePicker = (DatePicker) findViewById(R.id.date_picker);
    preferences = PreferenceManager.getDefaultSharedPreferences(this);

    Calendar calendar = Calendar.getInstance();
    // Pick a min date.
    calendar.set(1900, 0, 1);
    datePicker.setMinDate(calendar.getTimeInMillis());
    // Max date is today.
    long time = System.currentTimeMillis();
    datePicker.setMaxDate(time);
    calendar.setTimeInMillis(time);
    // If no time set, set the calendar 1 year back so that people can still access all the
    // months and days. Otherwise, use the remembered date.
    datePicker.updateDate(
        calendar.get(Calendar.YEAR) - 1,
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH));

    Button getStarted = (Button) findViewById(R.id.get_started);
    getStarted.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth());
            preferences
                .edit()
                .putBoolean(KEY_USER_OVER_13, isOver13(calendar.getTimeInMillis()))
                .apply();
            setResult(RESULT_OK);
            finish();
          }
        });
    WhistlePunkApplication.getPerfTrackerProvider(this).onAppInteractive();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (AppSingleton.getInstance(this).getAndClearMostRecentOpenWasImport()) {
      AccessibilityUtils.makeSnackbar(
              datePicker,
              getResources().getString(R.string.import_age_verification),
              Snackbar.LENGTH_SHORT)
          .show();
    }
  }

  public static boolean shouldShowUserAge(Context context) {
    if (DEBUG_AGE_VERIFIER) {
      return true;
    }
    // If the user is signed-in, we don't need to ask the user's age.
    if (WhistlePunkApplication.getAppServices(context).getAccountsProvider().isSignedIn()) {
      return false;
    }
    return !PreferenceManager.getDefaultSharedPreferences(context).contains(KEY_USER_OVER_13);
  }

  public static boolean isUserOver13(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(KEY_USER_OVER_13, false);
  }

  @VisibleForTesting
  public static boolean isOver13(long birthTime) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(System.currentTimeMillis());
    calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR) - 13);

    return birthTime <= calendar.getTimeInMillis();
  }

  public static void forgetAge(Context context) {
    /** Long key which previously stored the user age input. */
    final String oldKeyUserAge = "user_age";

    /** Boolean key which previously stored if the user has entered their birthdate. */
    final String oldKeyUserAgeSet = "user_age_set";

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (sharedPreferences.contains(oldKeyUserAge)) {
      SharedPreferences.Editor editor = sharedPreferences.edit();
      editor.putBoolean(KEY_USER_OVER_13, isOver13(sharedPreferences.getLong(oldKeyUserAge, 0)));
      editor.remove(oldKeyUserAge);
      editor.remove(oldKeyUserAgeSet);
      editor.commit();
    }
  }
}
