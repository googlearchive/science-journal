package com.google.android.apps.forscience.whistlepunk;

import org.junit.Test;

public class WhistlePunkApplicationTest {
    @Test
    public void usageTrackerForNullContext() {
        WhistlePunkApplication app = new WhistlePunkApplication() {
            @Override
            protected void onCreateInjector() {
                // do nothing
            }
        };

        // Make sure this doesn't throw.
        app.getUsageTracker(null).trackScreenView("screen");
    }
}