package com.google.android.apps.forscience.whistlepunk.sensorapi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ManualSensorTest {
    @Test public void id() {
        // silly test just to make sure that we're compiling and running ManualSensor correctly
        ManualSensor sensor = new ManualSensor("sensorId", 100, 100);
        assertEquals("sensorId", sensor.getId());
    }
}