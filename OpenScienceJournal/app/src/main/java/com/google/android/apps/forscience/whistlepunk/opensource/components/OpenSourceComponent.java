package com.google.android.apps.forscience.whistlepunk.opensource.components;

import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.opensource.modules.FeatureDiscoveryModule;
import com.google.android.apps.forscience.whistlepunk.opensource.modules.FeedbackModule;
import com.google.android.apps.forscience.whistlepunk.opensource.modules.UsageTrackerModule;

import dagger.Component;

/**
 * Created by justinkoh on 6/7/16.
 */
@Component(modules = {FeatureDiscoveryModule.class, FeedbackModule.class, UsageTrackerModule.class})
public interface OpenSourceComponent {
    void inject(WhistlePunkApplication app);
}
