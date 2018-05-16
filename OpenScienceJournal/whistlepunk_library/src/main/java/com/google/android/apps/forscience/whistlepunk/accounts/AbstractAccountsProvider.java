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
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** An abstract base class for accounts providers. */
abstract class AbstractAccountsProvider implements AccountsProvider {
  final Context applicationContext;
  final BehaviorSubject<AppAccount> observableCurrentAccount = BehaviorSubject.create();
  private final Object lockCurrentAccount = new Object();
  private AppAccount currentAccount;
  private final Map<String, AppAccount> accountsByKey = new HashMap<>();

  AbstractAccountsProvider(Context context) {
    applicationContext = context.getApplicationContext();

    NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(applicationContext);
    accountsByKey.put(nonSignedInAccount.getAccountKey(), nonSignedInAccount);
  }

  @Override
  public final boolean isSignedIn() {
    return getCurrentAccount().isSignedIn();
  }

  @Override
  public final AppAccount getCurrentAccount() {
    synchronized (lockCurrentAccount) {
      return currentAccount;
    }
  }

  @Override
  public final Observable<AppAccount> getObservableCurrentAccount() {
    return observableCurrentAccount;
  }

  @Override
  public AppAccount getAccountByKey(String accountKey) {
    AppAccount appAccount = accountsByKey.get(accountKey);
    if (appAccount != null) {
      return appAccount;
    }
    throw new IllegalArgumentException("The accountKey is not associated with a known AppAccount");
  }

  /**
   * Sets the current account and publishes it to observers if the current account override has not
   * been set.
   */
  protected void setCurrentAccount(AppAccount currentAccount) {
    synchronized (lockCurrentAccount) {
      // Add the account to the accountsByKey map.
      accountsByKey.put(currentAccount.getAccountKey(), currentAccount);

      this.currentAccount = currentAccount;
      observableCurrentAccount.onNext(currentAccount);
    }
  }

  protected void removeAccounts(Set<String> accountKeysToRemove) {
    accountsByKey.keySet().removeAll(accountKeysToRemove);
  }
}
