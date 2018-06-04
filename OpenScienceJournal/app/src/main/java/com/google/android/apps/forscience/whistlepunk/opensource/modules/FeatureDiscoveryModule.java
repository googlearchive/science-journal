/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.opensource.modules;

import android.content.Context;
import androidx.fragment.app.FragmentActivity;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import dagger.Module;
import dagger.Provides;

/** Stub feature discovery module which does nothing. */
@Module
public class FeatureDiscoveryModule {
  @Provides
  public FeatureDiscoveryProvider provideFeatureDiscoveryProvider() {
    return new FeatureDiscoveryProvider() {
      @Override
      public boolean isEnabled(Context context, AppAccount appAccount, String feature) {
        return false;
      }

      @Override
      public void show(
          FragmentActivity activity, AppAccount appAccount, String feature, String tag) {}
    };
  }
}
