package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.content.Context;
import android.preference.Preference;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

public class ScalarInputDiscoverer implements ExternalSensorDiscoverer {
    public static final String INTENT_ACTION =
            "com.google.android.apps.forscience.whistlepunk.ADDED_SENSOR";
    private static final String TAG = "ThirdPartyDisc";
    private Consumer<AppDiscoveryCallbacks> mServiceFinder;

    /**
     * Mostly for testing's sake, there's an onion's worth of layers here.
     *
     * ScalarInputDiscoverer outsources the actual construction of ISensorDiscoverers to this
     * interface, so that we can run automated tests against ScalarInputDiscoverer without having
     * to guarantee that particular apps are actually installed on the test device.
     */
    public interface AppDiscoveryCallbacks {
        // Called with each service found
        public void onServiceFound(ISensorDiscoverer.Stub service);

        // Called after all services have been found
        public void onDiscoveryDone();
    }

    public ScalarInputDiscoverer(Consumer<AppDiscoveryCallbacks> serviceFinder) {
        mServiceFinder = serviceFinder;
    }

    // TODO: implement all of these!

    @NonNull
    @Override
    public ExternalSensorSpec extractSensorSpec(Preference preference) {
        return new ScalarInputSpec();
    }

    @Override
    public boolean startScanning(SensorPrefCallbacks callbacks, final Context context) {
        return false;
    }

    @Override
    public void stopScanning() {
    }
}
