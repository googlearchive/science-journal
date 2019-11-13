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
package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.Intent;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;

/**
 * Encapsulates decisions about which specific activity classes implement which features, allowing
 * different builds to use different specific activities.
 */
public interface ActivityNavigator {
  ActivityNavigator STUB =
      new ActivityNavigator() {
        @Override
        public Intent launchIntentForPanesActivity(
            Context context,
            AppAccount appAccount,
            String experimentId,
            boolean claimExperimentsMode) {
          return null;
        }
      };

  Intent launchIntentForPanesActivity(
      Context context, AppAccount appAccount, String experimentId, boolean claimExperimentsMode);
}
