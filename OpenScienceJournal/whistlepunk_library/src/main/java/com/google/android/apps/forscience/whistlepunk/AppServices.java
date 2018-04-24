/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.squareup.leakcanary.RefWatcher;

public interface AppServices {
    AppServices STUB = new AppServices() {
        @Override
        public RefWatcher getRefWatcher() {
            return RefWatcher.DISABLED;
        }

        @Override
        public UsageTracker getUsageTracker() {
            return UsageTracker.STUB;
        }

        @Override
        public FeatureDiscoveryProvider getFeatureDiscoveryProvider() {
            return FeatureDiscoveryProvider.STUB;
        }

        @Override
        public FeedbackProvider getFeedbackProvider() {
            return FeedbackProvider.STUB;
        }

        @Override
        public ActivityNavigator getNavigator() {
            return ActivityNavigator.STUB;
        }

        @Override
        public AccountsProvider getAccountsProvider() {
            return null;
        }
    };

    RefWatcher getRefWatcher();

    UsageTracker getUsageTracker();

    FeatureDiscoveryProvider getFeatureDiscoveryProvider();

    FeedbackProvider getFeedbackProvider();

    ActivityNavigator getNavigator();

    AccountsProvider getAccountsProvider();
}
