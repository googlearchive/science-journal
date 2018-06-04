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
import android.preference.PreferenceFragment;
import androidx.fragment.app.Fragment;
import com.google.android.apps.forscience.whistlepunk.ActivityWithNavigationView;
import io.reactivex.Observable;

/** An interface which provides account management. */
public interface AccountsProvider {
  /** @return true if a signed-in account is supported; false otherwise */
  boolean supportSignedInAccount();

  /** @return true if a signed-in account is required to use Science Journal; false otherwise */
  boolean requireSignedInAccount();

  /** @return the number of accounts on the device. */
  int getAccountCount();

  /**
   * Connects the account switcher, if necessary. Must be called during onCreate of the main
   * activity, after {@link Activity#setContentView}.
   */
  void connectAccountSwitcher(ActivityWithNavigationView activity);

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
   * Returns an {@link Observable} that publishes the current account.
   *
   * <p>This is the preferred way to access the current account.
   *
   * @return an {@link Observable} that publishes the current account.
   */
  Observable<AppAccount> getObservableCurrentAccount();

  /** @return the account with the given key, or null if no known account has the given key. */
  AppAccount getAccountByKey(String accountKey);

  /**
   * Registers the given preference key as an account-based preference. Only preferences that are
   * used in preference screens need to be registered.
   */
  void registerAccountBasedPreferenceKey(String prefKey, Boolean defaultValue);

  /**
   * Adjusts keys and values of Preferences in the given PreferenceFragment that have been registerd
   * as account-based preferences.
   */
  void adjustPreferenceFragment(PreferenceFragment preferenceFragment);
}
