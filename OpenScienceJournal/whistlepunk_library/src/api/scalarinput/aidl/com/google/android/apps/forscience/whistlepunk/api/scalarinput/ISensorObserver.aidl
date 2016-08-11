// ISensorDiscoverer.aidl
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

interface ISensorObserver {
    void onNewData(long timestamp, float data) = 0;
}
