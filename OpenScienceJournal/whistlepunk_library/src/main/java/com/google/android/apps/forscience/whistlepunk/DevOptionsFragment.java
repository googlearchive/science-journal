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

package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;

/** Holder for Developer Testing Options */
public class DevOptionsFragment extends PreferenceFragment {
  private static final String TAG = "DevOptionsFragment";

  @VisibleForTesting public static final String KEY_SINE_WAVE_SENSOR = "enable_sine_wave_sensor";

  private static final String KEY_DEV_TOOLS = "dev_tools";
  private static final String KEY_LEAK_CANARY = "leak_canary";
  private static final String KEY_STRICT_MODE = "strict_mode";
  public static final String KEY_DEV_SONIFICATION_TYPES = "enable_dev_sonification_types";
  public static final String KEY_AMBIENT_TEMPERATURE_SENSOR = "enable_ambient_temp_sensor";
  private static final String KEY_PERF_DEBUG_SCREEN = "show_perf_tracker_debug";
  public static final String KEY_SMOOTH_SCROLL = "enable_smooth_scrolling_to_bottom";
  private static final String KEY_REQUIRE_GOOGLE_ACCOUNT = "require_google_account";
  private static final String KEY_AGE_STATUS = "age_status";

  public static DevOptionsFragment newInstance() {
    return new DevOptionsFragment();
  }

  static boolean shouldHideTestingOptions(Context context) {
    if (!isDebugVersion()) {
      return true;
    }
    return Build.TYPE.equals("user") && !BuildConfig.DEBUG;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Context context = getActivity();

    addPreferencesFromResource(R.xml.dev_options);

    CheckBoxPreference leakPref = (CheckBoxPreference) findPreference(KEY_LEAK_CANARY);
    if (isDebugVersion()) {
      leakPref.setChecked(isLeakCanaryEnabled(context));
      leakPref.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            final SharedPreferences prefs = getPrefs(preference.getContext());
            prefs.edit().putBoolean(KEY_LEAK_CANARY, (Boolean) newValue).apply();
            return true;
          });
    } else {
      getPreferenceScreen().removePreference(leakPref);
    }

    Preference prefRequireGoogleAccount = findPreference(KEY_REQUIRE_GOOGLE_ACCOUNT);
    // Enable the preference if require Google account is off.
    // Disable the preference if require Google account is on.
    prefRequireGoogleAccount.setEnabled(
        !prefRequireGoogleAccount
            .getSharedPreferences()
            .getBoolean(KEY_REQUIRE_GOOGLE_ACCOUNT, false));
    prefRequireGoogleAccount.setOnPreferenceClickListener(this::promptBeforeRequireGoogleAccount);

    Preference prefTrackerPref = findPreference(KEY_PERF_DEBUG_SCREEN);
    prefTrackerPref.setOnPreferenceClickListener(
        preference -> {
          WhistlePunkApplication.getPerfTrackerProvider(context)
              .startPerfTrackerEventDebugActivity(context);
          return true;
        });

    Preference ageStatusPreference = findPreference(KEY_AGE_STATUS);
    ageStatusPreference.setTitle(
        AgeVerifier.isOver13(AgeVerifier.getUserAge(context))
            ? context.getResources().getString(R.string.age_status_over_13)
            : context.getResources().getString(R.string.age_status_under_13));
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  private boolean promptBeforeRequireGoogleAccount(Preference prefRequireGoogleAccount) {
    AlertDialog.Builder builder = new AlertDialog.Builder(prefRequireGoogleAccount.getContext());
    builder.setTitle(R.string.require_google_account_alert_title);
    builder.setMessage(R.string.require_google_account_alert_message);
    builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
    builder.setPositiveButton(
        android.R.string.ok,
        (dialog, which) -> {
          prefRequireGoogleAccount
              .getEditor()
              .putBoolean(KEY_REQUIRE_GOOGLE_ACCOUNT, true)
              .commit();
          dialog.dismiss();
          System.exit(0);
        });
    builder.create().show();
    return true;
  }

  @VisibleForTesting
  public static SharedPreferences getPrefs(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context);
  }

  public static boolean isSineWaveEnabled(Context context) {
    return getBoolean(KEY_SINE_WAVE_SENSOR, false, context);
  }

  public static boolean isDevToolsEnabled(Context context) {
    return getBoolean(KEY_DEV_TOOLS, false, context);
  }

  public static boolean isLeakCanaryEnabled(Context context) {
    // Enable by default for non user builds, otherwise respect the preference.
    return getBoolean(KEY_LEAK_CANARY, !Build.TYPE.equals("user"), context);
  }

  public static boolean isStrictModeEnabled(Context context) {
    return getBoolean(KEY_STRICT_MODE, false, context);
  }

  public static boolean isDebugVersion() {
    return BuildConfig.DEBUG;
  }

  public static boolean getEnableAdditionalSonificationTypes(Context context) {
    return getBoolean(KEY_DEV_SONIFICATION_TYPES, false, context);
  }

  public static boolean isAmbientTemperatureSensorEnabled(Context context) {
    return getBoolean(KEY_AMBIENT_TEMPERATURE_SENSOR, false, context);
  }

  public static boolean isSmoothScrollingToBottomEnabled(Context context) {
    return getBoolean(KEY_SMOOTH_SCROLL, true, context);
  }

  public static boolean requireGoogleAccount(Context context) {
    return getBoolean(KEY_REQUIRE_GOOGLE_ACCOUNT, false, context);
  }

  private static boolean getBoolean(String key, boolean defaultBool, Context context) {
    if (!isDebugVersion()) {
      return defaultBool;
    }
    return getPrefs(context).getBoolean(key, defaultBool);
  }
}
