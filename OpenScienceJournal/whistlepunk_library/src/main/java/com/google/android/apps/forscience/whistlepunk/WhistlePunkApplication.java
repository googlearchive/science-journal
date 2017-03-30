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

package com.google.android.apps.forscience.whistlepunk;

import android.app.Application;
import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewExporter;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import java.util.Map;

import javax.inject.Inject;

/**
 * Application subclass holding shared objects.
 */
public abstract class WhistlePunkApplication extends Application {

    private RefWatcher mRefWatcher;

    @Inject
    UsageTracker mUsageTracker;

    @Inject
    FeatureDiscoveryProvider mFeatureDiscoveryProvider;

    @Inject
    FeedbackProvider mFeedbackProvider;

    @Inject
    Map<String, ExternalSensorDiscoverer> mSensorDiscoverers;

    public static RefWatcher getRefWatcher(Context context) {
        WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
        return app.mRefWatcher;
    }

    public static UsageTracker getUsageTracker(Context context) {
        if (context == null) {
            return UsageTracker.STUB;
        }
        WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
        return app.mUsageTracker;
    }

    public static FeatureDiscoveryProvider getFeatureDiscoveryProvider(Context context) {
        WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
        return app.mFeatureDiscoveryProvider;
    }

    public static FeedbackProvider getFeedbackProvider(Context context) {
        WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
        return app.mFeedbackProvider;
    }

    public static Map<String, ExternalSensorDiscoverer> getExternalSensorDiscoverers(
            Context context) {
        WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
        return app.mSensorDiscoverers;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        mRefWatcher = installLeakCanary();
        onCreateInjector();
        RunReviewExporter.cleanOldFiles(this);
        enableStrictMode();
        setupBackupAgent();
    }

    protected void setupBackupAgent() {
        // Register your backup agent to receive settings change events here.
        // Learn more at https://developer.android.com/guide/topics/data/keyvaluebackup.html#BackupAgentHelper.
    }

    protected RefWatcher installLeakCanary() {
        return RefWatcher.DISABLED;
    }

    protected abstract void onCreateInjector();

    protected void enableStrictMode() {}
}
