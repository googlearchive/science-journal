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

import android.app.Activity;
import androidx.fragment.app.Fragment;
import com.google.android.apps.forscience.whistlepunk.ActivityWithNavigationView;
import io.reactivex.Observable;
import java.util.Set;

/** An interface which provides account management. */
public interface AccountsProvider {
  /**
   * Result code sent back from the AccountSwitcherDialog if the chosen account is not permitted to
   * use Science Journal.
   */
  public static final int RESULT_ACCOUNT_NOT_PERMITTED = 10001;

  /**
   * Shared preference key used to store whether old preferences have been copied to a particular
   * account.
   */
  public static final String KEY_OLD_PREFERENCES_COPIED = "old_preferences_copied";

  /** Returns true if a signed-in account is supported; false otherwise. */
  boolean supportSignedInAccount();

  /** @return whether the sign in activity should be shown if the user is not signed in. */
  boolean getShowSignInActivityIfNotSignedIn();

  /** Sets whether the sign in activity should be shown if the user is not signed in. */
  void setShowSignInActivityIfNotSignedIn(boolean showSignInActivityIfNotSignedIn);

  /**
   * Sets whether the Science-Jornual-is-disabled alert will be shown on the sign in activity and
   * returns the previous value.
   */
  boolean getAndSetShowScienceJournalIsDisabledAlert(boolean newValue);

  /** @return the number of accounts on the device. */
  int getAccountCount();

  /**
   * Installs the account switcher, if necessary. Must be called during onCreate of the main
   * activity, after {@link Activity#setContentView}.
   */
  void installAccountSwitcher(ActivityWithNavigationView activity);

  /**
   * Disconnects the account switcher, if necessary. Must be called during onStop of the main
   * activity.
   */
  void disconnectAccountSwitcher(ActivityWithNavigationView activity);

  /** Called from AccountsChangedReceiver when we receive the LOGIN_ACCOUNTS_CHANGED_ACTION. */
  void onLoginAccountsChanged();

  /** Shows the dialog where the user adds an account. */
  void showAddAccountDialog(Activity activity);

  /** Shows the dialog where the user chooses an account. */
  void showAccountSwitcherDialog(Fragment fragment, int requestCode);

  /** Returns true if the current account is a signed-in account. */
  boolean isSignedIn();

  /**
   * Sets the current account to the NonSignedInAccount. This should only be used in two situations:
   *
   * <ol>
   *   <li>when the user presses the back button in OldUserOptionPromptActivity.
   *   <li>when the user chooses an account that is not permitted to use Science Journal
   * </ol>
   */
  void undoSignIn();

  /**
   * Returns an {@link Observable} that publishes the current account.
   *
   * <p>This is the preferred way to access the current account.
   *
   * @return an {@link Observable} that publishes the current account.
   */
  Observable<AppAccount> getObservableCurrentAccount();

  /** @return the account with the given key, or null if no known account has the given key. */
  AppAccount getAccountByKey(String accountKey);

  /** @return the set of known accounts, including the NonSignedInAccount. */
  Set<AppAccount> getAccounts();

  /** @return True if the given account is a known app account, including the NonSignedInAccount. */
  boolean isAppAccount(String accountKey);

  /**
   * Registers that the given preference should be copied from the non-signed-in account to the
   * signed-in account when the signed-in account is first used.
   */
  void registerPreferenceToBeCopied(String prefKey, Object defaultValue);
}
