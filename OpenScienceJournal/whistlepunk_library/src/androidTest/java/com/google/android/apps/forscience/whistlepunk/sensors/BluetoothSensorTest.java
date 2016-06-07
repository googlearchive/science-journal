package com.google.android.apps.forscience.whistlepunk.sensors;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.MemorySensorHistoryStorage;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorTypeProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FakeBleClient;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;

public class BluetoothSensorTest extends AndroidTestCase {
    private static final BluetoothSensor.BleServiceSpec SPEC =
            BluetoothSensor.ANNING_SERVICE_SPEC;

    public void testGetFrequency() {
        BleSensorSpec sensor = new BleSensorSpec("address", "name");
        sensor.setSensorType(SensorTypeProvider.TYPE_CUSTOM);

        sensor.setCustomFrequencyEnabled(true);
        assertTrue(new BluetoothSensor("sensorId", sensor, SPEC).getDefaultFrequencyChecked());
        sensor.setCustomFrequencyEnabled(false);
        assertFalse(new BluetoothSensor("sensorId", sensor, SPEC).getDefaultFrequencyChecked());
    }

    public void testGetScaleTransform() {
        BleSensorSpec sensor = new BleSensorSpec("address", "name");

        sensor.setSensorType(SensorTypeProvider.TYPE_CUSTOM);
        assertNull(new BluetoothSensor("sensorId", sensor, SPEC).getDefaultScaleTransform());

        GoosciSensorConfig.BleSensorConfig.ScaleTransform transform = new GoosciSensorConfig
                .BleSensorConfig.ScaleTransform();
        sensor.setCustomScaleTransform(transform);
        assertEquals(transform,
                new BluetoothSensor("sensorId", sensor, SPEC).getDefaultScaleTransform());
    }

    public void testConnect() {
        BleSensorSpec sensor = new BleSensorSpec("address", "name");
        FakeBleClient bleClient = new FakeBleClient(getContext());
        bleClient.expectedAddress = "address";
        MemorySensorEnvironment environment = new MemorySensorEnvironment(
                new InMemorySensorDatabase().makeSimpleRecordingController(
                        new MemoryMetadataManager()), bleClient, new MemorySensorHistoryStorage());
        SensorRecorder recorder = new BluetoothSensor("sensorId", sensor, SPEC).createRecorder(
                getContext(), new RecordingSensorObserver(), new StubStatusListener(), environment);
        assertEquals(null, bleClient.mostRecentAddress);
        recorder.startObserving();
        assertEquals("address", bleClient.mostRecentAddress);
    }
}