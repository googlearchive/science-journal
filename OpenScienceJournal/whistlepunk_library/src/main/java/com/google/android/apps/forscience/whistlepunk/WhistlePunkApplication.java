package com.google.android.apps.forscience.whistlepunk;

import android.app.Application;
import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewExporter;
import com.squareup.leakcanary.RefWatcher;

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

    public static RefWatcher getRefWatcher(Context context) {
        WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
        return app.mRefWatcher;
    }

    public static UsageTracker getUsageTracker(Context context) {
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

    @Override
    public void onCreate() {
        super.onCreate();
        mRefWatcher = installLeakCanary();
        onCreateInjector();
        RunReviewExporter.cleanOldFiles(this);
    }

    protected RefWatcher installLeakCanary() {
        return RefWatcher.DISABLED;
    }

    protected abstract void onCreateInjector();

}
