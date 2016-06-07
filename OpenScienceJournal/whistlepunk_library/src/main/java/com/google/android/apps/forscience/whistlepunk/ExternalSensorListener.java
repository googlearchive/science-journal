package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

import java.util.Map;

public interface ExternalSensorListener {
    void replaceExternalSensors(Map<String, ExternalSensorSpec> sensors);
}
