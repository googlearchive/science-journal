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

package com.google.android.apps.forscience.whistlepunk.opensource.components;

import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.modules.CloudSyncModule;
import com.google.android.apps.forscience.whistlepunk.modules.ContextModule;
import com.google.android.apps.forscience.whistlepunk.modules.InputDeviceModule;
import com.google.android.apps.forscience.whistlepunk.modules.NativeBleDiscovererModule;
import com.google.android.apps.forscience.whistlepunk.modules.NonSignedInAccountsModule;
import com.google.android.apps.forscience.whistlepunk.modules.ScalarInputDiscoveryModule;
import com.google.android.apps.forscience.whistlepunk.opensource.licenses.LicenseModule;
import com.google.android.apps.forscience.whistlepunk.opensource.modules.FeatureDiscoveryModule;
import com.google.android.apps.forscience.whistlepunk.opensource.modules.FeedbackModule;
import com.google.android.apps.forscience.whistlepunk.opensource.modules.PerfTrackerModule;
import com.google.android.apps.forscience.whistlepunk.opensource.modules.UsageTrackerModule;
import dagger.Component;

@Component(
    modules = {
      FeatureDiscoveryModule.class,
      FeedbackModule.class,
      UsageTrackerModule.class,
      NativeBleDiscovererModule.class,
      InputDeviceModule.class,
      ScalarInputDiscoveryModule.class,
      ContextModule.class,
      PerfTrackerModule.class,
      NonSignedInAccountsModule.class,
      LicenseModule.class,
      CloudSyncModule.class
    })
public interface OpenSourceComponent {
  void inject(WhistlePunkApplication app);
}
