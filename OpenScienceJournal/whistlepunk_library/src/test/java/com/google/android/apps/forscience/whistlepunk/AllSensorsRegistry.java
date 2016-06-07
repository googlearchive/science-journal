package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;

class AllSensorsRegistry extends SensorRegistry {
    @Override
    public void withSensorChoice(String id, Consumer<SensorChoice> consumer) {
        consumer.take(new ManualSensor(id, 100, 100));
    }
}
