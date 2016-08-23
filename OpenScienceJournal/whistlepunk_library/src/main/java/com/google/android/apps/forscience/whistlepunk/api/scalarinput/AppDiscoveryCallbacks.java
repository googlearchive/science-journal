package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

/**
 * Mostly for testing's sake, there's an onion's worth of layers here.
 *
 * ScalarInputDiscoverer outsources the actual construction of ISensorDiscoverers to this
 * interface, so that we can run automated tests against ScalarInputDiscoverer without having
 * to guarantee that particular apps are actually installed on the test device.
 */
public interface AppDiscoveryCallbacks {
    // Called with each service found
    public void onServiceFound(String serviceId, ISensorDiscoverer service);

    // Called after all services have been found
    public void onDiscoveryDone();
}
