package com.google.android.apps.forscience.whistlepunk;

import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.devicemanager.NativeBleDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensors.BarometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.SineWavePseudoSensor;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

public class SensorRegistryTest extends DevOptionsTestCase {
    @NonNull
    @Override
    protected String getRememberedPrefKey() {
        return DevOptionsFragment.KEY_SINE_WAVE_SENSOR;
    }

    public void testImmediatelyReset() {
        SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(getContext());

        setPrefValue(false);
        reg.refreshBuiltinSensors(getContext());
        assertEquals(false, reg.getAllSources().contains(SineWavePseudoSensor.ID));

        setPrefValue(true);
        reg.refreshBuiltinSensors(getContext());
        assertEquals(true, reg.getAllSources().contains(SineWavePseudoSensor.ID));

        // double-check removal
        setPrefValue(false);
        reg.refreshBuiltinSensors(getContext());
        assertEquals(false, reg.getAllSources().contains(SineWavePseudoSensor.ID));
    }

    public void testDontClearExternalSensors() {
        SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(getContext());

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String bleSensorId = "bleSensor1";
        sensors.put(bleSensorId, new BleSensorSpec("address", "name"));

        HashMap<String, ExternalSensorProvider> providers = new HashMap<>();
        providers.put(BleSensorSpec.TYPE, new NativeBleDiscoverer().getProvider());

        reg.updateExternalSensors(sensors, providers);
        assertEquals(true, reg.getAllSources().contains(bleSensorId));
        reg.refreshBuiltinSensors(getContext());
        assertEquals(true, reg.getAllSources().contains(bleSensorId));
    }

    public void testNotifyListener() {
        SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(getContext());
        TestSensorRegistryListener listener = new TestSensorRegistryListener();
        assertEquals(0, listener.numRefreshes);
        reg.setSensorRegistryListener(listener);
        reg.refreshBuiltinSensors(getContext());
        assertEquals(1, listener.numRefreshes);
    }

    private static class TestSensorRegistryListener implements SensorRegistryListener {
        public int numRefreshes = 0;

        @Override
        public void updateExternalSensors(Map<String, ExternalSensorSpec> sensors) {

        }

        @Override
        public void refreshBuiltinSensors() {
            numRefreshes++;
        }
    }
}