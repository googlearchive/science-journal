/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupManager;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsUtils;
import com.google.common.collect.ObjectArrays;
import java.io.IOException;

/**
 * Backup agent to back up settings and whether the user has seen the tutorial and feature discovery
 * instances yet.
 */
public class SimpleBackupAgent extends BackupAgentHelper {
  // A key to uniquely identify the set of backup data
  static final String PREFS_BACKUP_KEY = "default_prefs";
  private static final String TAG = "SimpleBackupAgent";

  // Allocate a helper and add it to the backup agent.
  // This currently backs up all the default SharedPreferences as well as any account specific
  // SharedPreferences files.
  @Override
  public void onCreate() {
    Context context = getApplicationContext();
    String defaultPrefsName = getDefaultSharedPreferencesName(context);
    String[] accountsPrefsNames = AccountsUtils.getSharedPreferencesNamesForAllAccounts(context);
    SharedPreferencesBackupHelper helper =
        new SharedPreferencesBackupHelper(
            this, ObjectArrays.concat(defaultPrefsName, accountsPrefsNames));
    addHelper(PREFS_BACKUP_KEY, helper);
  }

  private String getDefaultSharedPreferencesName(Context context) {
    if (AndroidVersionUtils.isApiLevelAtLeastNougat()) {
      return PreferenceManager.getDefaultSharedPreferencesName(context);
    } else {
      // The function getDefaultSharedPreferencesName was private until API N, but implemented
      // the same way.
      return context.getPackageName() + "_preferences";
    }
  }

  /** Schedule backups each time the preferences are modified. */
  public static SharedPreferences.OnSharedPreferenceChangeListener
      registerOnSharedPreferencesChangeListener(Context applicationContext) {
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (sharedPreferences, s) ->
            BackupManager.dataChanged("com.google.android.apps.forscience.whistlepunk");
    PreferenceManager.getDefaultSharedPreferences(applicationContext)
        .registerOnSharedPreferenceChangeListener(listener);
    return listener;
  }

  @Override
  public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
      throws IOException {
    super.onRestore(data, appVersionCode, newState);
  }
}
