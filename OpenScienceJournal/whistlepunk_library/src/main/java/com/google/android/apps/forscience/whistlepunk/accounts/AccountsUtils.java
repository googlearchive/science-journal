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
import android.preference.PreferenceManager;
import androidx.annotation.VisibleForTesting;
import android.util.Log;
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

  private static final String PREF_KEY_PREFIX = "account";
  private static final String PREF_KEY_DELIMITER = "_";

  private static final String DB_NAME_PREFIX = "account";
  private static final String DB_NAME_DELIMITER = "_";

  private static final String NAMESPACE_DELIMITER = ":";

  private AccountsUtils() {
    // prevent construction
  }

  /**
   * Removes the files, preferences, and databases associated with all accounts whose account key is
   * not present in the given set.
   *
   * <p>Should be called from a background IO thread.
   *
   * @return the set of account keys that were removed
   */
  static Set<String> removeOtherAccounts(Context context, Set<String> accountKeys) {
    Set<String> accountKeysRemoved = new HashSet<>();

    // Remove files for accounts which are no longer present.
    for (File filesDir : getFilesDirsForAllAccounts(context)) {
      String accountKey = getAccountKeyFromFile(context, filesDir);
      if (accountKey != null) {
        if (!accountKeys.contains(accountKey)) {
          accountKeysRemoved.add(accountKey);
          try {
            deleteRecursively(filesDir);
          } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
              Log.e(TAG, "Failed to delete account directory.", e);
            }
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
          accountKeysRemoved.add(accountKey);
          context.deleteDatabase(databaseFileName);
        }
      }
    }

    // Remove preferences for accounts which are no longer present.
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    for (String prefKey : new HashSet<>(sharedPreferences.getAll().keySet())) {
      String accountKey = getAccountKeyFromPrefKey(prefKey);
      if (accountKey != null) {
        if (!accountKeys.contains(accountKey)) {
          accountKeysRemoved.add(accountKey);
          sharedPreferences.edit().remove(prefKey).apply();
        }
      }
    }

    return accountKeysRemoved;
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

  /** Returns the existing files directories for all accounts. */
  private static File[] getFilesDirsForAllAccounts(Context context) {
    File[] files = getAccountsParentDirectory(context).listFiles();
    return (files != null) ? files : new File[0];
  }

  /**
   * Returns the account key for the given namespace and account id. The account key is used to
   * separate file storage, database storage, and preferences for different accounts.
   */
  static String makeAccountKey(String namespace, String accountId) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace is null or empty!");
    Preconditions.checkArgument(
        !namespace.contains(PREF_KEY_DELIMITER),
        "namespace contains illegal character \"%s\" !",
        PREF_KEY_DELIMITER);
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
    return DB_NAME_PREFIX + DB_NAME_DELIMITER + accountKey + DB_NAME_DELIMITER + dbName;
  }

  /**
   * Returns the account key associated with the given preference key or null if the preference key
   * does not belong to an account.
   */
  @VisibleForTesting
  static String getAccountKeyFromPrefKey(String prefKey) {
    String prefixWithDelimiter = PREF_KEY_PREFIX + PREF_KEY_DELIMITER;
    if (prefKey.startsWith(prefixWithDelimiter)) {
      int beginningOfAccountKey = prefixWithDelimiter.length();
      int endOfAccountKey = prefKey.indexOf(PREF_KEY_DELIMITER, beginningOfAccountKey);
      return prefKey.substring(beginningOfAccountKey, endOfAccountKey);
    }
    return null;
  }

  /** Returns the preference key that combines the given accountKey and prefKey. */
  static String getPrefKey(String accountKey, String prefKey) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(accountKey), "accountKey is null or empty!");
    return PREF_KEY_PREFIX + PREF_KEY_DELIMITER + accountKey + PREF_KEY_DELIMITER + prefKey;
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
