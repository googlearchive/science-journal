// ISensorDiscoverer.aidl
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

// TODO: doc all these
interface ISensorConsumer {
  void onSensorFound(String sensorId, String name, in PendingIntent settingsIntent) = 0;
}
