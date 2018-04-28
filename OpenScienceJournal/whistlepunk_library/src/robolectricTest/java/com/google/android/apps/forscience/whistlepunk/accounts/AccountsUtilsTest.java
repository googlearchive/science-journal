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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for the AccountsUtils class. */
@RunWith(RobolectricTestRunner.class)
public class AccountsUtilsTest {
  private static final String ACCOUNT_NAME_1 = "account1@gmail.com";
  private static final String ACCOUNT_NAME_2 = "account2@gmail.com";
  private static final String ACCOUNT_NAME_3 = "account3@gmail.com";

  private Context context;
  private SharedPreferences sharedPreferences;

  @Before
  public void setUp() throws Exception {
    context = RuntimeEnvironment.application.getApplicationContext();
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    tearDown(); // Clean up, just in case previous test crashed.
  }

  @After
  public void tearDown() throws Exception {
    for (File child : context.getFilesDir().listFiles()) {
      deleteRecursively(child);
    }
  }

  @Test
  public void removeOtherAccounts() throws Exception {
    // Setup - For each of three accounts, create files representing 5 experiments and set a
    // preference.
    // TODO(lizlooney): Create a database.
    for (String accountName : ImmutableSet.of(ACCOUNT_NAME_1, ACCOUNT_NAME_2, ACCOUNT_NAME_3)) {
      File accountFilesDir = AccountsUtils.getFilesDir(accountName, context);
      File experimentsDir = new File(accountFilesDir, "experiments");
      for (int i = 1; i <= 5; i++) {
        String experimentId = "experiment_id_" + i;
        File experimentDir = new File(experimentsDir, experimentId);
        experimentDir.mkdirs();
        Files.touch(new File(experimentDir, "experiment.proto"));
      }
      assertThat(accountFilesDir.list()).hasLength(1);
      assertThat(experimentsDir.list()).hasLength(5);
      String accountPrefKey =
          AccountsUtils.getPrefKey(accountName, "key_default_experiment_created");
      sharedPreferences.edit().putBoolean(accountPrefKey, true).apply();
      assertThat(sharedPreferences.contains(accountPrefKey)).isTrue();
    }
    File account1FilesDir = AccountsUtils.getFilesDir(ACCOUNT_NAME_1, context);
    File account2FilesDir = AccountsUtils.getFilesDir(ACCOUNT_NAME_2, context);
    File account3FilesDir = AccountsUtils.getFilesDir(ACCOUNT_NAME_3, context);

    // Remove accounts other than accounts 2 and 3.
    AccountsUtils.removeOtherAccounts(context, ImmutableSet.of(ACCOUNT_NAME_2, ACCOUNT_NAME_3));

    // Directory for account 1 no longer exists.
    assertThat(account1FilesDir.exists()).isFalse();
    // Preference for account 1 no longer exists.
    assertThat(
            sharedPreferences.contains(
                AccountsUtils.getPrefKey(ACCOUNT_NAME_1, "key_default_experiment_created")))
        .isFalse();
    // TODO(lizlooney): Check that the database for account 1 no longer exists.

    // Directories for accounts 2 and 3 still have 5 experiments each.
    assertThat(account2FilesDir.list()).hasLength(1);
    assertThat(new File(account2FilesDir, "experiments").list()).hasLength(5);
    assertThat(account3FilesDir.list()).hasLength(1);
    assertThat(new File(account3FilesDir, "experiments").list()).hasLength(5);
    // Preferences for accounts 2 and 3 still there.
    assertThat(
            sharedPreferences.getBoolean(
                AccountsUtils.getPrefKey(ACCOUNT_NAME_2, "key_default_experiment_created"), false))
        .isTrue();
    assertThat(
            sharedPreferences.getBoolean(
                AccountsUtils.getPrefKey(ACCOUNT_NAME_3, "key_default_experiment_created"), false))
        .isTrue();
    // TODO(lizlooney): Check that the databases for accounts 2 and 3 still exist.
  }

  @Test
  public void getFilesDir() throws Exception {
    File account1FilesDir = AccountsUtils.getFilesDir(ACCOUNT_NAME_1, context);
    assertThat(account1FilesDir.exists()).isTrue();
    assertThat(account1FilesDir.isDirectory()).isTrue();
    assertThat(account1FilesDir.getParent()).isEqualTo(context.getFilesDir() + "/accounts");

    // The files directory for a different account should be different.
    File account2FilesDir = AccountsUtils.getFilesDir(ACCOUNT_NAME_2, context);
    assertThat(account1FilesDir).isNotEqualTo(account2FilesDir);
  }

  @Test
  public void getDatabaseFileName() throws Exception {
    String account1DatabaseFileName = AccountsUtils.getDatabaseFileName(ACCOUNT_NAME_1, "runs");
    assertThat(account1DatabaseFileName).startsWith("account.");
    assertThat(account1DatabaseFileName).endsWith(".runs");

    // The database file name for a different account should be different.
    String account2DatabaseFileName = AccountsUtils.getDatabaseFileName(ACCOUNT_NAME_2, "runs");
    assertThat(account1DatabaseFileName).isNotEqualTo(account2DatabaseFileName);
  }

  @Test
  public void getPrefKeyForAccount() throws Exception {
    String account1PrefKey =
        AccountsUtils.getPrefKey(ACCOUNT_NAME_1, "key_default_experiment_created");
    assertThat(account1PrefKey).startsWith("account_");
    assertThat(account1PrefKey).endsWith("_key_default_experiment_created");

    // The pref key for a different account should be different.
    String account2PrefKey =
        AccountsUtils.getPrefKey(ACCOUNT_NAME_2, "key_default_experiment_created");
    assertThat(account1PrefKey).isNotEqualTo(account2PrefKey);
  }

  @Test
  public void getUnclaimedExperimentCount() throws Exception {
    // Create files representing 5 unclaimed experiments.
    File unclaimedExperimentsDir = new File(context.getFilesDir(), "experiments");
    for (int i = 1; i <= 5; i++) {
      String experimentId = "experiment_id_" + i;
      File experimentDir = new File(unclaimedExperimentsDir, experimentId);
      experimentDir.mkdirs();
      Files.touch(new File(experimentDir, "experiment.proto"));
    }

    assertThat(AccountsUtils.getUnclaimedExperimentCount(context)).isEqualTo(5);
  }

  private static void deleteRecursively(File fileOrDirectory) throws Exception {
    if (fileOrDirectory.isDirectory()) {
      for (File child : fileOrDirectory.listFiles()) {
        deleteRecursively(child);
      }
    }
    if (!fileOrDirectory.delete()) {
      throw new Exception("Could not delete " + fileOrDirectory);
    }
  }
}
