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

import android.accounts.Account;
import androidx.annotation.Nullable;
import java.io.File;

/** An interface which represents an account. */
public interface AppAccount {
  /** Returns the account for this AppAccount, or null if there is no account. */
  @Nullable
  Account getAccount();

  /**
   * Returns the name of this account, or empty string if the account is the non-signed in account.
   * The name of an account may change over time. For example, for a google account, the name is an
   * email address and may be changed.
   */
  String getAccountName();

  /**
   * Returns the key of this account. The key should include a namespace prefix and should not ever
   * change. The key will be used to separate file and database storage as well as preferences. The
   * accountKey is also used as the key in the {@link AbstractAccountsProvider#accountsByKey} map.
   */
  String getAccountKey();

  /** Returns true if this account is a signed-in account. */
  boolean isSignedIn();

  /** Returns the root directory for this account. */
  File getFilesDir();

  /** Returns the file name of the database with the given name for this account. */
  String getDatabaseFileName(String name);

  /** Returns the name of the SharedPreferences for this account. */
  String getSharedPreferencesName();

  /**
   * Returns a lock object that must be used to synchronize reading and writing of the local
   * experiment library file for this account.
   */
  Object getLockForExperimentLibraryFile();

  /**
   * Returns a lock object that must be used to synchronize reading and writing of local experiment
   * files in this account.
   */
  Object getLockForExperimentProtoFile();

  /** Increments the count of how many cloud syncs have completed. */
  void incrementSyncCompleteCount();

  /** Returns the count of how many cloud syncs have completed. */
  int getSyncCompleteCount();
}
