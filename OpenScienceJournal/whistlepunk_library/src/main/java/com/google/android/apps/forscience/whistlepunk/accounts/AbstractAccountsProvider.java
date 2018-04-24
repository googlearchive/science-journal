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

/**
 * An abstract base class for accounts providers.
 */
abstract class AbstractAccountsProvider implements AccountsProvider {
  final Context applicationContext;
  final BehaviorSubject<AppAccount> observableCurrentAccount = BehaviorSubject.create();
  private final Object lockCurrentAccount = new Object();
  private AppAccount currentAccount;
  private AppAccount currentAccountOverride;

  AbstractAccountsProvider(Context context) {
    applicationContext = context.getApplicationContext();
  }

  @Override
  public final boolean isSignedIn() {
    return getCurrentAccount().isSignedIn();
  }

  @Override
  public final AppAccount getCurrentAccount() {
    synchronized (lockCurrentAccount) {
      return (currentAccountOverride != null)
          ? currentAccountOverride
          : currentAccount;
    }
  }

  @Override
  public final Observable<AppAccount> getObservableCurrentAccount() {
    return observableCurrentAccount;
  }

  @Override
  public void setClaimExperimentsMode(boolean claimExperimentsMode) {
    synchronized (lockCurrentAccount) {
      if (claimExperimentsMode) {
        currentAccountOverride = NonSignedInAccount.getInstance(applicationContext);
        observableCurrentAccount.onNext(currentAccountOverride);
      } else {
        currentAccountOverride = null;
        observableCurrentAccount.onNext(currentAccount);
      }
    }
  }

  @Override
  public boolean getClaimExperimentsMode() {
    synchronized (lockCurrentAccount) {
      return currentAccountOverride != null;
    }
  }

  @Override
  public AppAccount getCurrentAccountIgnoringClaimExperimentsMode() {
    synchronized (lockCurrentAccount) {
      return currentAccount;
    }
  }

  /**
   * Sets the current account and publishes it to observers if the current account override has
   * not been set.
   */
  protected void setCurrentAccount(AppAccount currentAccount) {
    synchronized (lockCurrentAccount) {
      this.currentAccount = currentAccount;
      if (currentAccountOverride == null) {
        observableCurrentAccount.onNext(currentAccount);
      }
    }
  }
}
