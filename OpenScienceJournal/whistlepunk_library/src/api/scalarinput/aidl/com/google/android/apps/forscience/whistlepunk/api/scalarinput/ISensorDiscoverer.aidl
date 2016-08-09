// ISensorDiscoverer.aidl
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;

interface ISensorDiscoverer {
    // TODO: add additional methods
    String getName() = 0;
    void scanDevices(IDeviceConsumer c) = 1;
    void scanSensors(String deviceId, ISensorConsumer c) = 2;
}
