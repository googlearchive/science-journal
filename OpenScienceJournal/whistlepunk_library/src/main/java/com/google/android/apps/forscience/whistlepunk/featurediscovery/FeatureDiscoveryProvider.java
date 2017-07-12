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

package com.google.android.apps.forscience.whistlepunk.featurediscovery;

import android.content.Context;
import android.support.v4.app.FragmentActivity;

/**
 * An object which can show feature discovery to the user.
 */
public interface FeatureDiscoveryProvider {

    public static final String FEATURE_NEW_EXTERNAL_SENSOR = "fd_new_external_sensor";

    /**
     * Amount of time in ms to delay showing the feature discovery dialog.
     */
    public static final long FEATURE_DISCOVERY_SHOW_DELAY_MS = 500;

    /**
     * Returns {@code true} if the given feature is available for discovery. It's expected that
     * the provider will only show features the first time to the user.
     */
    public boolean isEnabled(Context context, String feature);

    /**
     * Shows the feature discovery view to the user.
     *
     * @param activity    activity hosting the view
     * @param feature     which feature to show
     * @param tag         tag set on the view using View#setTag, used to find the view later
     */
    public void show(FragmentActivity activity, String feature, String tag);
}
