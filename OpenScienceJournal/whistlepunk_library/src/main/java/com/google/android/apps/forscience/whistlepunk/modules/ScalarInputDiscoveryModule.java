package com.google.android.apps.forscience.whistlepunk.modules;

import com.google.android.apps.forscience.javalib.Consumer;
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
    public ExternalSensorDiscoverer providesScalarInputDiscoverer() {
        // TODO: return the actual finder when DevOptionsFragment#isThirdPartyDiscoveryEnabled.
        return new ScalarInputDiscoverer(NULL_FINDER);
    }
}
