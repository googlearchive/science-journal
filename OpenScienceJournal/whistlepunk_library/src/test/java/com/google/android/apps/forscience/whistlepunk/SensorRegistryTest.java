package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SensorRegistryTest {
    @Test
    public void addApiSensorsOnlyOnce() {
        SensorRegistry registry = new SensorRegistry();

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        sensors.put("id", new ScalarInputSpec("name", "serviceId", "address"));

        assertEquals(Lists.newArrayList("id"),
                registry.updateExternalSensors(sensors, getProviders()));
        assertEquals(Lists.newArrayList(), registry.updateExternalSensors(sensors, getProviders()));
    }

    public Map<String,ExternalSensorProvider> getProviders() {
        Map<String, ExternalSensorProvider> providers = new HashMap<>();
        providers.put(ScalarInputSpec.TYPE,
                new ScalarInputDiscoverer(null, null,
                        MoreExecutors.directExecutor()).getProvider());
        return providers;
    }
}