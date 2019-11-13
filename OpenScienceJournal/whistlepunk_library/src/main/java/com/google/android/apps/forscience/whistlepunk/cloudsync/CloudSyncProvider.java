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

package com.google.android.apps.forscience.whistlepunk.cloudsync;

import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;

/** An interface which provides cloud sync management. */
public interface CloudSyncProvider {
  /**
   * Creates, if necessary, and returns a CloudSyncManager configured for the AppAccount.
   *
   * @param appAccount the account that should be associated with data synced through the returned
   *     service.
   * @return a CloudSyncManager configured for the AppAccount.
   */
  CloudSyncManager getServiceForAccount(AppAccount appAccount);
}
