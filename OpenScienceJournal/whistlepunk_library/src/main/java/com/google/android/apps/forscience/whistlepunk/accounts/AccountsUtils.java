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

import static com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager.EXPERIMENTS_DIRECTORY;
import static com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager.EXPERIMENT_FILE;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.VisibleForTesting;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Account utility methods. Most of these methods are intended to be used only within the accounts
 * package, but a few methods are public.
 */
public class AccountsUtils {
  private static final String TAG = "AccountsUtils";

  /**
   * Name of the subdirectory within the app's files directory that holds the subdirectories
   * associated with the accounts.
   */
  private static final String ACCOUNTS_PARENT_DIRECTORY_NAME = "accounts";

  /**
   * Name of the subdirectory within the app's data directory that holds the SharedPreferences
   * files.
   */
  private static final String SHARED_PREFS_DIRECTORY_NAME = "shared_prefs";

  private static final String SHARED_PREFS_NAME_PREFIX = "account";
  private static final String SHARED_PREFS_NAME_DELIMITER = "_";

  private static final String DB_NAME_PREFIX = "account";
  private static final String DB_NAME_DELIMITER = "_";

  private static final String NAMESPACE_DELIMITER = ":";

  private AccountsUtils() {
    // prevent construction
  }

  /**
   * Removes the files, SharedPreferences files, and databases associated with all accounts whose
   * account key is not present in the given set.
   *
   * <p>Should be called from a background IO thread.
   */
  static void removeOtherAccounts(Context context, Set<String> accountKeys) {
    Set<String> missingAccountKeys = new HashSet<>();

    // Remove files for accounts which are no longer present.
    for (File filesDir : getFilesDirsForAllAccounts(context)) {
      String accountKey = getAccountKeyFromFile(context, filesDir);
      if (!accountKeys.contains(accountKey)) {
        missingAccountKeys.add(accountKey);
        try {
          deleteRecursively(filesDir);
        } catch (IOException e) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Failed to delete account directory.", e);
          }
        }
      }
    }

    // Remove databases for accounts which are no longer present.
    String[] databaseList = context.databaseList();
    for (String databaseFileName : databaseList) {
      String accountKey = getAccountKeyFromDatabaseFileName(databaseFileName);
      if (accountKey != null) {
        if (!accountKeys.contains(accountKey)) {
          missingAccountKeys.add(accountKey);
          context.deleteDatabase(databaseFileName);
        }
      }
    }

    // Remove preferences for accounts which are no longer present.
    for (File sharedPreferencesFile : getSharedPreferencesFilesForAllAccounts(context)) {
      String accountKey =
          getAccountKeyFromSharedPreferencesFileName(sharedPreferencesFile.getName());
      if (!accountKeys.contains(accountKey)) {
        missingAccountKeys.add(accountKey);
        // Clear the SharedPreferences.
        SharedPreferences sharedPreferences =
            getSharedPreferences(context, getSharedPreferencesName(sharedPreferencesFile));
        sharedPreferences.edit().clear().commit();
        // Delete the SharedPreferences file.
        sharedPreferencesFile.delete();
      }
    }

    if (!missingAccountKeys.isEmpty()) {
      UsageTracker usageTracker = WhistlePunkApplication.getUsageTracker(context);
      for (int i = 0; i < missingAccountKeys.size(); i++) {
        usageTracker.trackEvent(
            TrackerConstants.CATEGORY_SIGN_IN, TrackerConstants.ACTION_REMOVED_ACCOUNT, null, 0);
      }
    }
  }

  private static void deleteRecursively(File fileOrDirectory) throws IOException {
    if (fileOrDirectory.isDirectory()) {
      for (File child : fileOrDirectory.listFiles()) {
        deleteRecursively(child);
      }
    }
    if (!fileOrDirectory.delete()) {
      throw new IOException("Could not delete " + fileOrDirectory);
    }
  }

  /** Returns the parent directory that contains the file directories for all accounts. */
  private static File getAccountsParentDirectory(Context context) {
    return new File(context.getFilesDir(), ACCOUNTS_PARENT_DIRECTORY_NAME);
  }

  /** Returns the existing files directories for all signed-in accounts. */
  private static File[] getFilesDirsForAllAccounts(Context context) {
    try {
      File parentDir = getAccountsParentDirectory(context);
      if (parentDir.exists() && parentDir.isDirectory()) {
        // Filter out files that don't have an account key.
        File[] files = parentDir.listFiles((f) -> getAccountKeyFromFile(context, f) != null);
        if (files != null) {
          return files;
        }
      }
    } catch (Exception e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Failed to get files directories for all accounts.", e);
      }
    }

    return new File[0];
  }

  /**
   * Returns the account key for the given namespace and account id. The account key is used to
   * separate file storage, database storage, and preferences for different accounts.
   */
  static String makeAccountKey(String namespace, String accountId) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace is null or empty!");
    Preconditions.checkArgument(
        !namespace.contains(SHARED_PREFS_NAME_DELIMITER),
        "namespace contains illegal character \"%s\" !",
        SHARED_PREFS_NAME_DELIMITER);
    Preconditions.checkArgument(
        !namespace.contains(DB_NAME_DELIMITER),
        "namespace contains illegal character \"%s\" !",
        DB_NAME_DELIMITER);
    Preconditions.checkArgument(
        !namespace.contains(NAMESPACE_DELIMITER),
        "namespace contains illegal character \"%s\" !",
        NAMESPACE_DELIMITER);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(accountId), "accountId is null or empty!");

    return namespace + NAMESPACE_DELIMITER + accountId;
  }

  /**
   * Returns the account key associated with the given file or null if the file does not belong to
   * an account.
   */
  @VisibleForTesting
  static String getAccountKeyFromFile(Context context, File file) {
    File accountsParentDir = getAccountsParentDirectory(context);
    while (file != null) {
      File parent = file.getParentFile();
      if (parent.equals(accountsParentDir)) {
        return file.getName();
      }
      file = parent;
    }
    return null;
  }

  /**
   * Returns the files directory for the account with the given account key. If the directory
   * doesn't already exist, it is created.
   */
  static File getFilesDir(String accountKey, Context context) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(accountKey), "accountKey is null or empty!");
    File accountDir = new File(getAccountsParentDirectory(context), accountKey);
    if (!accountDir.exists()) {
      if (!accountDir.mkdirs()) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, String.format("Failed to create directory %s", accountDir.getAbsolutePath()));
        }
        // TODO(lizlooney): what to do now?
      }
    }
    return accountDir;
  }

  /**
   * Returns the account key associated with the given database file name or null if the database
   * file name does not belong to an account.
   */
  @VisibleForTesting
  static String getAccountKeyFromDatabaseFileName(String databaseFileName) {
    String prefixWithDelimiter = DB_NAME_PREFIX + DB_NAME_DELIMITER;
    if (databaseFileName.startsWith(prefixWithDelimiter)) {
      int beginningOfAccountKey = prefixWithDelimiter.length();
      int endOfAccountKey = databaseFileName.indexOf(DB_NAME_DELIMITER, beginningOfAccountKey);
      return databaseFileName.substring(beginningOfAccountKey, endOfAccountKey);
    }
    return null;
  }

  /** Returns the database file name that combines the given accountKey and dbName. */
  static String getDatabaseFileName(String accountKey, String dbName) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(accountKey), "accountKey is null or empty!");
    Preconditions.checkArgument(!Strings.isNullOrEmpty(dbName), "dbName is null or empty!");
    Preconditions.checkArgument(
        getAccountKeyFromDatabaseFileName(dbName) == null,
        "dbName must not already be associated with an AppAccount");
    return DB_NAME_PREFIX + DB_NAME_DELIMITER + accountKey + DB_NAME_DELIMITER + dbName;
  }

  /**
   * Returns the account key associated with the given SharedPreferences file name or null if the
   * SharedPreferences file name does not belong to an account.
   */
  @VisibleForTesting
  static String getAccountKeyFromSharedPreferencesFileName(String sharedPreferencesFileName) {
    String prefixWithDelimiter = SHARED_PREFS_NAME_PREFIX + SHARED_PREFS_NAME_DELIMITER;
    if (sharedPreferencesFileName.startsWith(prefixWithDelimiter)) {
      int beginningOfAccountKey = prefixWithDelimiter.length();
      int endOfAccountKey =
          sharedPreferencesFileName.indexOf(SHARED_PREFS_NAME_DELIMITER, beginningOfAccountKey);
      return sharedPreferencesFileName.substring(beginningOfAccountKey, endOfAccountKey);
    }
    return null;
  }

  /** Returns the name of the SharedPreferences for the given accountKey. */
  static String getSharedPreferencesName(String accountKey) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(accountKey), "accountKey is null or empty!");
    return SHARED_PREFS_NAME_PREFIX
        + SHARED_PREFS_NAME_DELIMITER
        + accountKey
        + SHARED_PREFS_NAME_DELIMITER;
  }

  private static String getSharedPreferencesName(File file) {
    String name = file.getName();
    // Remove ".xml".
    if (name.endsWith(".xml")) {
      name = name.substring(0, name.length() - 4);
    }
    return name;
  }

  /** Returns the existing SharedPreferences files for all signed-in accounts. */
  private static File[] getSharedPreferencesFilesForAllAccounts(Context context) {
    try {
      File parentDir = new File(context.getApplicationInfo().dataDir, SHARED_PREFS_DIRECTORY_NAME);
      if (parentDir.exists() && parentDir.isDirectory()) {
        // Filter out files that don't have an account key.
        File[] files =
            parentDir.listFiles((d, n) -> getAccountKeyFromSharedPreferencesFileName(n) != null);
        if (files != null) {
          return files;
        }
      }
    } catch (Exception e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Failed to get SharedPreferences names for all accounts.", e);
      }
    }

    return new File[0];
  }

  /** Returns the names of existing SharedPreferences for all signed-in accounts. */
  public static String[] getSharedPreferencesNamesForAllAccounts(Context context) {
    File[] files = getSharedPreferencesFilesForAllAccounts(context);
    String[] names = new String[files.length];
    for (int i = 0; i < files.length; i++) {
      names[i] = getSharedPreferencesName(files[i]);
    }
    return names;
  }

  private static SharedPreferences getSharedPreferences(
      Context context, String sharedPreferencesName) {
    return context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE);
  }

  /** Returns the SharedPreferences for the given AppAccount. */
  public static SharedPreferences getSharedPreferences(Context context, AppAccount appAccount) {
    return getSharedPreferences(context, appAccount.getSharedPreferencesName());
  }

  public static int getUnclaimedExperimentCount(Context context) {
    int count = 0;
    File experimentsDir = new File(context.getFilesDir(), EXPERIMENTS_DIRECTORY);
    File[] files = experimentsDir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory() && new File(file, EXPERIMENT_FILE).isFile()) {
          count++;
        }
      }
    }
    return count;
  }
}
