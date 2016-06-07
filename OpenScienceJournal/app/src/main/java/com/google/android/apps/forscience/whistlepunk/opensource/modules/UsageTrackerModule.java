package com.google.android.apps.forscience.whistlepunk.opensource.modules;

import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;

import dagger.Module;
import dagger.Provides;

/**
 * Stub UsageTracker which does nothing.
 */
@Module
public class UsageTrackerModule {
    @Provides
    public UsageTracker provideUsageTracker() {
        return new UsageTracker() {
            @Override
            public void setOptOut(boolean optOut) {

            }

            @Override
            public void trackScreenView(String screenName) {

            }

            @Override
            public void trackEvent(String category, String action, String label, long value) {

            }
        };
    }
}
