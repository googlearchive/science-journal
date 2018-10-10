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
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import java.util.Set;

/** An interface which provides account management. */
public interface AccountsProvider {
  /**
   * Shared preference key used to store whether old preferences have been copied to a particular
   * account.
   */
  public static final String KEY_OLD_PREFERENCES_COPIED = "old_preferences_copied";

  /** Indicates whether a signed-in account is supported. */
  Single<Boolean> supportSignedInAccount();

  /** Indicates whether a signed-in account is required to use Science Journal. */
  Single<Boolean> requireSignedInAccount();

  /** @return whether the sign in activity should be shown if the user is not signed in. */
  boolean getShowSignInActivityIfNotSignedIn();

  /** Sets whether the sign in activity should be shown if the user is not signed in. */
  void setShowSignInActivityIfNotSignedIn(boolean showSignInActivityIfNotSignedIn);

  /** @return the number of accounts on the device. */
  int getAccountCount();

  /**
   * Installs the account switcher, if necessary. Must be called during onCreate of the main
   * activity, after {@link Activity#setContentView}.
   *
   * @return a {@link Disposable} that should be disposed when the given activity is destroyed.
   */
  Disposable installAccountSwitcher(ActivityWithNavigationView activity);

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

  /** @return the set of known accounts, including the NonSignedInAccount. */
  Set<AppAccount> getAccounts();

  /**
   * Registers the given preference key as an account-based preference. All preferences that should
   * be copied from the non-signed-in account to a signed-in account must be registered.
   */
  void registerAccountBasedPreferenceKey(String prefKey, Object defaultValue);
}
