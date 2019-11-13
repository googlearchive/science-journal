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
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import android.widget.Toast;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** An abstract base class for accounts providers. */
abstract class AbstractAccountsProvider implements AccountsProvider {
  final Context applicationContext;
  final UsageTracker usageTracker;
  final BehaviorSubject<AppAccount> observableCurrentAccount;
  private final Object lockCurrentAccount = new Object();
  private AppAccount currentAccount;
  private final Map<String, AppAccount> accountsByKey = new HashMap<>();
  private final Map<String, Object> preferencesToBeCopied = new HashMap<>();
  private final AtomicBoolean showSignInActivityIfNotSignedIn = new AtomicBoolean(true);
  private final AtomicBoolean showScienceJournalIsDisabledAlert = new AtomicBoolean(false);

  protected enum PermissionStatus {
    PERMITTED,
    NOT_PERMITTED,
    USER_AUTH_ACTION_REQUIRED
  }

  AbstractAccountsProvider(Context context) {
    applicationContext = context.getApplicationContext();
    usageTracker = WhistlePunkApplication.getUsageTracker(applicationContext);
    NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(applicationContext);
    addAccount(nonSignedInAccount);
    currentAccount = nonSignedInAccount;
    observableCurrentAccount = BehaviorSubject.createDefault(nonSignedInAccount);
  }

  protected Single<PermissionStatus> isAccountPermitted(Activity activity, AppAccount appAccount) {
    if (!appAccount.isSignedIn()) {
      return Single.just(PermissionStatus.PERMITTED);
    }
    throw new IllegalArgumentException("This should have been handled by subclass.");
  }

  @Override
  public final boolean getShowSignInActivityIfNotSignedIn() {
    return showSignInActivityIfNotSignedIn.get();
  }

  @Override
  public final void setShowSignInActivityIfNotSignedIn(boolean newValue) {
    showSignInActivityIfNotSignedIn.set(newValue);
  }

  @Override
  public final boolean getAndSetShowScienceJournalIsDisabledAlert(boolean newValue) {
    return showScienceJournalIsDisabledAlert.getAndSet(newValue);
  }

  @Override
  public boolean isSignedIn() {
    synchronized (lockCurrentAccount) {
      return currentAccount.isSignedIn();
    }
  }

  @Override
  public void undoSignIn() {
    setCurrentAccount(NonSignedInAccount.getInstance(applicationContext));
  }

  @Override
  public final Observable<AppAccount> getObservableCurrentAccount() {
    return observableCurrentAccount;
  }

  @Override
  public AppAccount getAccountByKey(String accountKey) {
    AppAccount appAccount = accountsByKey.get(accountKey);
    Preconditions.checkArgument(
        appAccount != null, "The accountKey must be associated with a known AppAccount");
    return appAccount;
  }

  @Override
  public Set<AppAccount> getAccounts() {
    return ImmutableSet.copyOf(accountsByKey.values());
  }

  @Override
  public boolean isAppAccount(String accountKey) {
    return accountsByKey.containsKey(accountKey);
  }

  @Override
  public void registerPreferenceToBeCopied(String prefKey, Object defaultValue) {
    Preconditions.checkArgument(prefKey != null, "The prefKey must not be null.");
    Preconditions.checkArgument(
        defaultValue instanceof Boolean
            || defaultValue instanceof Float
            || defaultValue instanceof Integer
            || defaultValue instanceof Long
            || defaultValue instanceof String
            || defaultValue instanceof Set,
        "The defaultValue must be Boolean, Float, Integer, Long, String, or Set<String>.");

    preferencesToBeCopied.put(prefKey, defaultValue);
  }

  @Nullable
  protected final AppAccount getCurrentAccount() {
    synchronized (lockCurrentAccount) {
      return currentAccount;
    }
  }

  /**
   * Sets the current account and publishes it to observers if the current account override has not
   * been set.
   */
  protected final void setCurrentAccount(AppAccount currentAccount) {
    synchronized (lockCurrentAccount) {
      addAccount(currentAccount);
      this.currentAccount = currentAccount;
      afterSetCurrentAccount(currentAccount);
    }
  }

  protected void afterSetCurrentAccount(AppAccount currentAccount) {
    if (currentAccount.isSignedIn()) {
      // Copy the registered preferences from the non-signed-in account to the current account.
      copyRegisteredPreferencesToAccount(currentAccount);
    }

    // Notify observers.
    observableCurrentAccount.onNext(currentAccount);
  }

  protected void addAccount(AppAccount appAccount) {
    // Add the account to the accountsByKey map.
    accountsByKey.put(appAccount.getAccountKey(), appAccount);
  }

  private void copyRegisteredPreferencesToAccount(AppAccount appAccount) {
    if (!appAccount.isSignedIn()) {
      return;
    }

    SharedPreferences sharedPreferencesForAccount =
        AccountsUtils.getSharedPreferences(applicationContext, appAccount);

    boolean alreadyCopied =
        sharedPreferencesForAccount.getBoolean(KEY_OLD_PREFERENCES_COPIED, false);
    if (alreadyCopied) {
      return;
    }

    SharedPreferences.Editor editor = sharedPreferencesForAccount.edit();

    SharedPreferences defaultSharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(applicationContext);

    for (Map.Entry<String, Object> entry : preferencesToBeCopied.entrySet()) {
      String key = entry.getKey();
      if (defaultSharedPreferences.contains(key)) {
        Object defaultValue = entry.getValue();
        if (defaultValue instanceof Boolean) {
          boolean value = defaultSharedPreferences.getBoolean(key, (Boolean) defaultValue);
          editor.putBoolean(key, value);
        } else if (defaultValue instanceof Float) {
          float value = defaultSharedPreferences.getFloat(key, (Float) defaultValue);
          editor.putFloat(key, value);
        } else if (defaultValue instanceof Integer) {
          int value = defaultSharedPreferences.getInt(key, (Integer) defaultValue);
          editor.putInt(key, value);
        } else if (defaultValue instanceof Long) {
          long value = defaultSharedPreferences.getLong(key, (Long) defaultValue);
          editor.putLong(key, value);
        } else if (defaultValue instanceof String) {
          String value = defaultSharedPreferences.getString(key, (String) defaultValue);
          editor.putString(key, value);
        } else if (defaultValue instanceof Set) {
          Set<String> value =
              defaultSharedPreferences.getStringSet(key, (Set<String>) defaultValue);
          editor.putStringSet(key, value);
        }
      }
    }

    editor.putBoolean(KEY_OLD_PREFERENCES_COPIED, true).apply();

    String message = applicationContext.getResources().getString(R.string.device_settings_applied);
    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show();
  }
}
