package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;

import org.junit.Test;

public class ScalarInputSpecTest {
    @Test
    public void appearance() {
        String name = Arbitrary.string();
        SensorAppearance appearance = new ScalarInputSpec(name, "serviceId",
                "address").getSensorAppearance();
        assertNotNull(appearance);
        assertEquals(name, appearance.getName(null));
    }

    @Test public void roundTripConfig() {
        String sensorName = Arbitrary.string();
        String serviceId = Arbitrary.string();
        String address = Arbitrary.string();
        ScalarInputSpec spec = new ScalarInputSpec(sensorName, serviceId, address);
        ScalarInputSpec spec2 = new ScalarInputSpec(sensorName, spec.getConfig());
        assertEquals(sensorName, spec2.getName());
        assertEquals(serviceId, spec2.getServiceId());
        assertEquals(address, spec2.getAddress());
    }
}