// ISensorDiscoverer.aidl
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorStatusListener;

interface ISensorConnector {
    void startObserving(String sensorId,
                        ISensorObserver observer,
                        ISensorStatusListener listener,
                        String settingsKey) = 0;
    void stopObserving(String sensorId) = 1;
}
