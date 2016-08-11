// ISensorDiscoverer.aidl
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

interface ISensorStatusListener {
    void onSensorConnecting(String id) = 0;
    void onSensorConnected(String id) = 1;
    void onSensorDisconnected(String id) = 2;
    void onSensorError(String id, String errorMessage) = 3;
}
