package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class DeviceOptionsViewControllerTest extends AndroidTestCase {
    public void testCommit() {
        DataController dc = new InMemorySensorDatabase().makeSimpleController(
                new MemoryMetadataManager());

        BleSensorSpec oldSpec = new BleSensorSpec("address", "name");
        StoringConsumer<String> cOldSensorId = new StoringConsumer<>();
        dc.addOrGetExternalSensor(oldSpec, cOldSensorId);
        assertEquals(ExternalSensorSpec.getSensorId(oldSpec, 0), cOldSensorId.getValue());
        String experimentId = "experimentId";
        dc.addSensorToExperiment(experimentId, ExternalSensorSpec.getSensorId(oldSpec, 0),
                TestConsumers.<Success>expectingSuccess());

        final BleSensorSpec newSpec = new BleSensorSpec("address", "name");
        newSpec.setSensorType(SensorTypeProvider.TYPE_ROTATION);

        DeviceOptionsViewController c = new TestController(dc, newSpec, experimentId);
        c.setSensor(ExternalSensorSpec.getSensorId(oldSpec, 0), oldSpec, null);

        RecordingDeviceOptionsListener listener = new RecordingDeviceOptionsListener();

        c.commit(listener);
        assertEquals(ExternalSensorSpec.getSensorId(oldSpec, 0), listener.mostRecentOldSensorId);
        assertEquals(ExternalSensorSpec.getSensorId(oldSpec, 1), listener.mostRecentNewSensorId);

        dc.getExternalSensorsByExperiment(experimentId,
                TestConsumers.<Map<String, ExternalSensorSpec>>expecting(
                        ImmutableMap.of(ExternalSensorSpec.getSensorId(oldSpec, 1),
                                (ExternalSensorSpec) newSpec)));

        c.getOptions().setSensorType(SensorTypeProvider.TYPE_CUSTOM);
        c.commit(listener);
        assertEquals(ExternalSensorSpec.getSensorId(oldSpec, 1), listener.mostRecentOldSensorId);
        assertEquals(ExternalSensorSpec.getSensorId(oldSpec, 2), listener.mostRecentNewSensorId);

        // And does nothing if new value is the same
        RecordingDeviceOptionsListener newListener = new RecordingDeviceOptionsListener();
        c.commit(newListener);
        assertNull(newListener.mostRecentNewSensorId);
    }

    private static class RecordingDeviceOptionsListener implements DeviceOptionsDialog
            .DeviceOptionsListener {
        public String mostRecentOldSensorId;
        public String mostRecentNewSensorId;

        @Override
        public void onExperimentSensorReplaced(String oldSensorId, String newSensorId) {
            mostRecentOldSensorId = oldSensorId;
            mostRecentNewSensorId = newSensorId;
        }

        @Override
        public void onRemoveDeviceFromExperiment(String experimentId, String address) {

        }
    }

    private class TestController extends DeviceOptionsViewController {
        private final BleSensorSpec mNewSpec;

        public TestController(DataController dc, BleSensorSpec newSpec, String experimentId) {
            super(DeviceOptionsViewControllerTest.this.getContext(), dc, experimentId);
            mNewSpec = newSpec;
        }

        @Override
        public BleSensorSpec getOptions() {
            return mNewSpec;
        }
    }
}