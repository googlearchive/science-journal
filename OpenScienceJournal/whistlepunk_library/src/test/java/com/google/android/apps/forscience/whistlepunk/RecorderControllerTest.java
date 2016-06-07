package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FakeBleClient;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;

import org.junit.Test;

public class RecorderControllerTest {
    @Test
    public void multipleObservers() {
        final String sensorId = "sensorId";
        final ManualSensor sensor = new ManualSensor(sensorId, 100, 100);
        SensorRegistry registry = new SensorRegistry() {
            @Override
            public void withSensorChoice(String id, Consumer<SensorChoice> consumer) {
                assertEquals(sensorId, id);
                consumer.take(sensor);
            }
        };
        MemorySensorEnvironment env = new MemorySensorEnvironment(
                new InMemorySensorDatabase().makeSimpleRecordingController(
                        new MemoryMetadataManager()), new FakeBleClient(null),
                new MemorySensorHistoryStorage());
        RecorderControllerImpl rc = new RecorderControllerImpl(null, registry, env);
        RecordingSensorObserver observer1 = new RecordingSensorObserver();
        RecordingSensorObserver observer2 = new RecordingSensorObserver();

        sensor.pushValue(0, 0);
        String id1 = rc.startObserving(sensorId, observer1, new StubStatusListener(), null);
        sensor.pushValue(1, 1);
        String id2 = rc.startObserving(sensorId, observer2, new StubStatusListener(), null);
        sensor.pushValue(2, 2);
        rc.stopObserving(sensorId, id1);
        sensor.pushValue(3, 3);
        rc.stopObserving(sensorId, id2);
        sensor.pushValue(4, 4);

        TestData.allPointsBetween(1, 2, 1).checkObserver(observer1);
        TestData.allPointsBetween(2, 3, 1).checkObserver(observer2);
    }

}