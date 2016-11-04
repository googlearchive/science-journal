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
package com.google.android.apps.forscience.whistlepunk.modules;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.DevOptionsFragment;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.AppDiscoveryCallbacks;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarSensorServiceFinder;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

@Module
public class ScalarInputDiscoveryModule {
    private static final String TAG = "SIDModule";

    private static final Consumer<AppDiscoveryCallbacks> NULL_FINDER =
            new Consumer<AppDiscoveryCallbacks>() {
                @Override
                public void take(AppDiscoveryCallbacks c) {
                    // Didn't find any.
                    c.onDiscoveryDone();
                }
            };

    @Provides
    @IntoMap
    @StringKey(ScalarInputSpec.TYPE)
    public ExternalSensorDiscoverer providesScalarInputDiscoverer(Context context) {
        // TODO: return the actual finder when DevOptionsFragment#isThirdPartyDiscoveryEnabled.
        return new ScalarInputDiscoverer(chooseFinder(context), context);
    }

    @NonNull
    private Consumer<AppDiscoveryCallbacks> chooseFinder(Context context) {
        return new ScalarSensorServiceFinder(context);
    }
}
