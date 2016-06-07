package com.google.android.apps.forscience.whistlepunk.metadata;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig.BleSensorConfig
        .ScaleTransform;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorTypeProvider;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ValueFilter;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

import junit.framework.TestCase;

public class BleSensorTest extends TestCase {
    public void testPresetsVsCustom() {
        BleSensorSpec bleSensor = new BleSensorSpec("address", "name");
        bleSensor.setCustomFrequencyEnabled(true);
        bleSensor.setCustomPin("A7");

        assertEquals(SensorTypeProvider.TYPE_RAW, bleSensor.getSensorType());
        assertEquals(false, bleSensor.getFrequencyEnabled());
        assertEquals("A0", bleSensor.getPin());

        bleSensor.setSensorType(SensorTypeProvider.TYPE_CUSTOM);
        assertEquals(SensorTypeProvider.TYPE_CUSTOM, bleSensor.getSensorType());
        assertEquals(true, bleSensor.getFrequencyEnabled());
        assertEquals("A7", bleSensor.getPin());

        bleSensor.setSensorType(SensorTypeProvider.TYPE_ROTATION);
        assertEquals(SensorTypeProvider.TYPE_ROTATION, bleSensor.getSensorType());
        assertEquals(true, bleSensor.getFrequencyEnabled());
        assertEquals("A0", bleSensor.getPin());

        // Custom pin is unused, but still stored
        assertEquals("A7", bleSensor.getCustomPin());

        // Custom settings have not been overridden
        bleSensor.setSensorType(SensorTypeProvider.TYPE_CUSTOM);
        assertEquals(SensorTypeProvider.TYPE_CUSTOM, bleSensor.getSensorType());
        assertEquals(true, bleSensor.getFrequencyEnabled());
        assertEquals("A7", bleSensor.getPin());
    }

    public void testScaleFilter() {
        BleSensorSpec bleSensor = new BleSensorSpec("address", "name");
        bleSensor.setCustomFrequencyEnabled(false);

        ScaleTransform transform = new ScaleTransform();
        transform.sourceBottom = 0;
        transform.sourceTop = 10;
        transform.destBottom = 0;
        transform.destTop = 100;

        bleSensor.setSensorType(SensorTypeProvider.TYPE_CUSTOM);
        bleSensor.setCustomScaleTransform(transform);
        assertEquals(transform, bleSensor.getScaleTransform());

        bleSensor.setSensorType(SensorTypeProvider.TYPE_ROTATION);
        assertEquals(null, bleSensor.getScaleTransform());

        bleSensor.setSensorType(SensorTypeProvider.TYPE_RAW);
        ValueFilter computedFilter = new BluetoothSensor("sensorId", bleSensor,
                BluetoothSensor.ANNING_SERVICE_SPEC).getDeviceDefaultValueFilter();
        assertEquals(0.0, computedFilter.filterValue(0, 0.0), 0.001);
        assertEquals(50.0, computedFilter.filterValue(0, 512.0), 0.001);
        assertEquals(100.0, computedFilter.filterValue(0, 1024.0), 0.001);
    }
}