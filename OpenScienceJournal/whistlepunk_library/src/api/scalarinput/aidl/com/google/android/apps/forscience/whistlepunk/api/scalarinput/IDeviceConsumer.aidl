// ISensorDiscoverer.aidl
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

interface IDeviceConsumer {
  void onDeviceFound(String deviceId, String name, in PendingIntent settingsIntent) = 0;
}
