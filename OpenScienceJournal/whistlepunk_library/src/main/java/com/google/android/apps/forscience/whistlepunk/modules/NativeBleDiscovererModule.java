package com.google.android.apps.forscience.whistlepunk.modules;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.devicemanager.NativeBleDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

@Module
public class NativeBleDiscovererModule {
    @Provides
    @IntoMap
    @StringKey(BleSensorSpec.TYPE)
    public ExternalSensorDiscoverer providesNativeDiscoverer() {
        return new NativeBleDiscoverer();
    }
}
