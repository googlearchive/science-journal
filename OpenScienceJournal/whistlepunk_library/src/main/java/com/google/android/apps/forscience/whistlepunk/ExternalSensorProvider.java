package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;

// TODO: clarify why this is a different interface from ExternalSensorDiscoverer
public interface ExternalSensorProvider {
    public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec);

    String getProviderId();

    ExternalSensorSpec buildSensorSpec(String name, byte[] config);
}
