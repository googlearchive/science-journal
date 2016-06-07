package com.google.android.apps.forscience.whistlepunk.opensource.modules;

import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryListener;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;

import dagger.Module;
import dagger.Provides;

/**
 * Stub feature discovery module which does nothing.
 */
@Module
public class FeatureDiscoveryModule {
    @Provides
    public FeatureDiscoveryProvider provideFeatureDiscoveryProvider() {
        return new FeatureDiscoveryProvider() {
            @Override
            public boolean isEnabled(Context context, String feature) {
                return false;
            }

            @Override
            public void show(String feature, FragmentManager fragmentManager, View view,
                    FeatureDiscoveryListener listener, Drawable drawable) {

            }
        };
    }
}
