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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.subjects.BehaviorSubject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** An abstract base class for accounts providers. */
abstract class AbstractAccountsProvider implements AccountsProvider {
  final Context applicationContext;
  final Single<Boolean> singleSupportSignedInAccount;
  final Single<Boolean> singleRequireSignedInAccount;
  final BehaviorSubject<AppAccount> observableCurrentAccount = BehaviorSubject.create();
  private final Object lockCurrentAccount = new Object();
  private AppAccount currentAccount;
  private final Map<String, AppAccount> accountsByKey = new HashMap<>();
  private final Map<String, Object> accountBasedPreferenceKeys = new HashMap<>();

  AbstractAccountsProvider(Context context) {
    applicationContext = context.getApplicationContext();

    singleSupportSignedInAccount =
        Single.<Boolean>create(emitter -> determineSupportSignedInAccount(emitter)).cache();
    singleRequireSignedInAccount =
        Single.<Boolean>create(emitter -> determineRequireSignedInAccount(emitter)).cache();

    addAccount(NonSignedInAccount.getInstance(applicationContext));
  }

  protected abstract void determineSupportSignedInAccount(SingleEmitter<Boolean> emitter);

  protected abstract void determineRequireSignedInAccount(SingleEmitter<Boolean> emitter);

  @Override
  public final Single<Boolean> supportSignedInAccount() {
    return singleSupportSignedInAccount;
  }

  @Override
  public final Single<Boolean> requireSignedInAccount() {
    return singleRequireSignedInAccount;
  }

  @Override
  public final boolean isSignedIn() {
    synchronized (lockCurrentAccount) {
      return currentAccount != null && currentAccount.isSignedIn();
    }
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
  public void registerAccountBasedPreferenceKey(String prefKey, Object defaultValue) {
    Preconditions.checkArgument(prefKey != null, "The prefKey must not be null.");
    Preconditions.checkArgument(
        defaultValue instanceof Boolean
            || defaultValue instanceof Float
            || defaultValue instanceof Integer
            || defaultValue instanceof Long
            || defaultValue instanceof String
            || defaultValue instanceof Set,
        "The defaultValue must be Boolean, Float, Integer, Long, String, or Set<String>.");

    accountBasedPreferenceKeys.put(prefKey, defaultValue);
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
  protected void setCurrentAccount(AppAccount currentAccount) {
    synchronized (lockCurrentAccount) {
      addAccount(currentAccount);

      this.currentAccount = currentAccount;

      // Copy the account-based preferences from the non-signed-in account to the current account.
      copyAccountBasedPreferencesToAccount(currentAccount);

      observableCurrentAccount.onNext(currentAccount);
    }
  }

  protected void addAccount(AppAccount appAccount) {
    // Add the account to the accountsByKey map.
    accountsByKey.put(appAccount.getAccountKey(), appAccount);
  }

  private void copyAccountBasedPreferencesToAccount(AppAccount appAccount) {
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

    for (Map.Entry<String, Object> entry : accountBasedPreferenceKeys.entrySet()) {
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
  }
}
