package com.google.android.apps.forscience.whistlepunk.modules;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.DevOptionsFragment;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarSensorServiceFinder;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

@Module
public class ScalarInputDiscoveryModule {
    private static final Consumer<ScalarInputDiscoverer.AppDiscoveryCallbacks> NULL_FINDER =
            new Consumer<ScalarInputDiscoverer.AppDiscoveryCallbacks>() {
                @Override
                public void take(ScalarInputDiscoverer.AppDiscoveryCallbacks c) {
                    // Didn't find any.
                    c.onDiscoveryDone();
                }
            };

    @Provides
    @IntoMap
    @StringKey(ScalarInputSpec.TYPE)
    public ExternalSensorDiscoverer providesScalarInputDiscoverer(Context context) {
        // TODO: return the actual finder when DevOptionsFragment#isThirdPartyDiscoveryEnabled.
        return new ScalarInputDiscoverer(chooseFinder(context));
    }

    @NonNull
    private Consumer<ScalarInputDiscoverer.AppDiscoveryCallbacks> chooseFinder(Context context) {
        if (DevOptionsFragment.isThirdPartyDiscoveryEnabled(context)) {
            return new ScalarSensorServiceFinder(context);
        } else {
            return NULL_FINDER;
        }
    }
}
