package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.audiogen.SimpleJsynAudioGenerator;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.TestData;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig.BleSensorConfig
        .ScaleTransform;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorTypeProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;
import com.google.common.collect.Lists;

import java.util.ArrayList;

public class ScalarSensorTest extends AndroidTestCase {
    private final MemoryMetadataManager mMetadata = new MemoryMetadataManager();
    private InMemorySensorDatabase mDb = new InMemorySensorDatabase();
    private final RecordingDataController mRecordingController = mDb.makeSimpleRecordingController(
            mMetadata);

    public void testDontStoreWhenObserving() {
        ManualSensor sensor = new ManualSensor("test", Long.MAX_VALUE, 2);
        RecordingSensorObserver observer = new RecordingSensorObserver();
        SensorRecorder recorder = sensor.createRecorder(getContext(),
                mRecordingController, observer);

        recorder.startObserving();
        sensor.pushValue(0, 0);
        recorder.startRecording("runId");
        sensor.pushValue(1, 1);
        sensor.pushValue(2, 2);
        recorder.stopRecording();
        sensor.pushValue(3, 3);
        sensor.pushValue(4, 4);
        sensor.pushValue(5, 5);
        sensor.pushValue(6, 6);
        sensor.pushValue(7, 7);
        recorder.stopObserving();

        ArrayList<InMemorySensorDatabase.Reading> expectedRecorded = Lists.newArrayList(
                new InMemorySensorDatabase.Reading("test", 1, 1),
                new InMemorySensorDatabase.Reading("test", 2, 2));
        assertEquals(expectedRecorded, mDb.getReadings(0));

        // We should just have two values at tier 1, too (none from after we stopped recording,
        // but even a partial buffer should have representative points)
        assertEquals(expectedRecorded, mDb.getReadings(1));

        ArrayList<ScalarReading> expectedObserved = Lists.newArrayList(new ScalarReading(0, 0),
                new ScalarReading(1, 1), new ScalarReading(2, 2), new ScalarReading(3, 3),
                new ScalarReading(4, 4), new ScalarReading(5, 5), new ScalarReading(6, 6),
                new ScalarReading(7, 7));

        assertEquals(expectedObserved, observer.getReadings());
    }

    public void testThrowawayData() {
        ManualSensor sensor = new ManualSensor("test", 10, 100);
        InMemorySensorDatabase db = new InMemorySensorDatabase();
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        final RecordingDataController rc = db.makeSimpleRecordingController(mmm);

        SensorPresenter presenter = sensor.createRecordingPresenter(this.getContext(), rc, "runId");

        sensor.pushValue(0, 0);
        sensor.pushValue(10, 10);
        sensor.pushValue(20, 20);
        sensor.pushValue(30, 30);
        sensor.pushValue(40, 40);

        DataController dc = db.makeSimpleController(mmm);
        presenter.onXAxisChanged(35, 45, true, dc);

        TestData expectedLive = new TestData();
        expectedLive.addPoint(30, 30);
        expectedLive.addPoint(40, 40);

        assertLineData(expectedLive, sensor);

        // Scroll back, make sure values load in (we are recording)
        presenter.onXAxisChanged(15, 25, false, dc);
        TestData expectedScrollback = new TestData();
        expectedScrollback.addPoint(20, 20);
        expectedScrollback.addPoint(30, 30);
        expectedScrollback.addPoint(40, 40);

        assertLineData(expectedScrollback, sensor);
    }

    public void testThrowawayDataWithSizeThreshold() {
        ManualSensor sensor = new ManualSensor("test", 10, 100);
        InMemorySensorDatabase db = new InMemorySensorDatabase();
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        final RecordingDataController rc = db.makeSimpleRecordingController(mmm);

        SensorPresenter presenter = sensor.createRecordingPresenter(this.getContext(), rc, "runId",
                /* graph data throwaway threshold size of 3 */ 3);
        DataController dc = db.makeSimpleController(mmm);

        // This is a work-around for onXAxisChanged doing double-loading in tests, because
        // mAnythingLoaded is false and the LineGraphPresenter already has data.
        presenter.onXAxisChanged(-20, 0, true, dc);

        sensor.pushValue(0, 0);
        sensor.pushValue(5, 5);
        sensor.pushValue(10, 10);
        sensor.pushValue(15, 15);
        sensor.pushValue(20, 20);

        presenter.onXAxisChanged(17, 27, true, dc);

        // Don't expect any data removal yet as we are still under the data threshold.
        TestData expectedLive = new TestData();
        expectedLive.addPoint(0, 0);
        expectedLive.addPoint(5, 5);
        expectedLive.addPoint(10, 10);
        expectedLive.addPoint(15, 15);
        expectedLive.addPoint(20, 20);

        assertLineData(expectedLive, sensor);

        // Scroll back, but without adding new data points we don't expect data removal.
        presenter.onXAxisChanged(7, 17, false, dc);
        assertLineData(expectedLive, sensor);

        // Now push the number of data points above the threshold and outside of the window.
        sensor.pushValue(25, 25);
        sensor.pushValue(30, 30);
        presenter.onXAxisChanged(27, 37, false, dc);

        // Now the number of data points is above threshold and truncating should occur.
        TestData expectedUpdated = new TestData();
        expectedUpdated.addPoint(20, 20);
        expectedUpdated.addPoint(25, 25);
        expectedUpdated.addPoint(30, 30);
        assertLineData(expectedUpdated, sensor);
    }

    public void testTruncateWhenAddingRightOfScreen() {
        ManualSensor sensor = new ManualSensor("test", 10, 100);
        InMemorySensorDatabase db = new InMemorySensorDatabase();
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        final RecordingDataController rc = db.makeSimpleRecordingController(mmm);

        SensorPresenter presenter = sensor.createRecordingPresenter(this.getContext(), rc, "runId");

        sensor.pushValue(0, 0);
        sensor.pushValue(10, 10);
        sensor.pushValue(20, 20);
        sensor.pushValue(30, 30);
        sensor.pushValue(40, 40);
        sensor.pushValue(50, 50);

        // Scroll back
        DataController dc = db.makeSimpleController(mmm);
        presenter.onXAxisChanged(35, 45, false, dc);

        // New data keeps coming off-screen
        sensor.pushValue(60, 60);
        sensor.pushValue(70, 70);
        sensor.pushValue(80, 80);
        sensor.pushValue(90, 90);
        sensor.pushValue(100, 100);

        TestData expectedLive = new TestData();
        // Currently-shown data
        expectedLive.addPoint(30, 30);
        expectedLive.addPoint(40, 40);
        expectedLive.addPoint(50, 50);

        // 60 and 70 have been dropped
        // Cache for return-to-now

        expectedLive.addPoint(80, 80);
        expectedLive.addPoint(90, 90);
        expectedLive.addPoint(100, 100);

        assertLineData(expectedLive, sensor);
    }

    protected void assertLineData(TestData testData, ManualSensor sensor) {
        testData.checkRawData(sensor.getRawData());
        testData.checkContents(sensor.getLineData().getRange(0, Double.MAX_VALUE, false));
    }

    public void testComputeFilterOnlyScale() {
        ScaleTransform transform = new ScaleTransform();
        transform.sourceBottom = 0;
        transform.sourceTop = 10;
        transform.destBottom = 0;
        transform.destTop = 100;
        ValueFilter valueFilter = ScalarSensor.computeValueFilter(1000, 0, false, transform);
        assertEquals(30.0, valueFilter.filterValue(0, 3.0), 0.001);
    }

    public void testComputeFilterOnlyFrequency() {
        double latest = feed20HzSignal(ScalarSensor.computeValueFilter(100, 0, true, null));
        assertEquals(20.0 * 60, latest, 0.01);
    }

    public void testComputeFilterCombined() {
        ScaleTransform transform = rpmToHertz();
        double latest = feed20HzSignal(ScalarSensor.computeValueFilter(100, 0, true, transform));
        assertEquals(20.0, latest, 0.01);
    }

    private double feed20HzSignal(ValueFilter filter) {
        filter.filterValue(0, 0);
        filter.filterValue(25, 1);
        filter.filterValue(50, 0);
        filter.filterValue(75, 1);
        return filter.filterValue(100, 0);
    }

    public void testTranslateFilter() {
        ScaleTransform transform = new ScaleTransform();
        transform.sourceBottom = 0;
        transform.sourceTop = 10;
        transform.destBottom = 90;
        transform.destTop = 100;
        ValueFilter buffer = ScalarSensor.computeValueFilter(100, 0, false, transform);
        assertEquals(3090, buffer.filterValue(0, 3000), 0.01);
    }

    public void testApplyOptions() {
        BleSensorSpec bleSensor = new BleSensorSpec("address", "name");
        bleSensor.setSensorType(SensorTypeProvider.TYPE_CUSTOM);
        bleSensor.setCustomFrequencyEnabled(true);
        bleSensor.setCustomScaleTransform(rpmToHertz());
        BluetoothSensor bluetoothSensor = new BluetoothSensor("sensorId", bleSensor,
                BluetoothSensor.ANNING_SERVICE_SPEC);
        ScalarDisplayOptions scalarOptions = new ScalarDisplayOptions();
        bluetoothSensor.createOptionsPresenter().applyOptions(new BlankReadableSensorOptions());
        ValueFilter filter = bluetoothSensor.getDeviceDefaultValueFilter();
        filter.filterValue(0, 0);
        filter.filterValue(250, 100);
        filter.filterValue(500, 0);
        filter.filterValue(750, 100);
        double filtered = filter.filterValue(1000, 0);
        assertEquals(2.0, filtered, 0.01);
    }

    public void testZoomBetweenTiers() {
        ManualSensor sensor = new ManualSensor("test", 1000, 5);
        SensorRecorder recorder = createRecorder(sensor);
        recorder.startRecording("runId");
        for (int i = 0; i < 20; i++) {
            sensor.pushValue(i, i);
        }

        ArrayList<InMemorySensorDatabase.Reading> expected = Lists.newArrayList(
                new InMemorySensorDatabase.Reading("test", 0, 0),
                new InMemorySensorDatabase.Reading("test", 9, 9),
                new InMemorySensorDatabase.Reading("test", 10, 10),
                new InMemorySensorDatabase.Reading("test", 19, 19));
        assertEquals(expected, mDb.getReadings(1));
    }

    private SensorRecorder createRecorder(ManualSensor sensor) {
        return sensor.createRecorder(getContext(), mRecordingController,
                new RecordingSensorObserver());
    }

    public void testZoomUpTwoTiers() {
        ManualSensor sensor = new ManualSensor("test", 1000, 5);
        SensorRecorder recorder = createRecorder(sensor);
        recorder.startRecording("runId");
        for (int i = 0; i < 100; i++) {
            sensor.pushValue(i, i);
        }

        ArrayList<InMemorySensorDatabase.Reading> expected = Lists.newArrayList(
                new InMemorySensorDatabase.Reading("test", 0, 0),
                new InMemorySensorDatabase.Reading("test", 49, 49),
                new InMemorySensorDatabase.Reading("test", 50, 50),
                new InMemorySensorDatabase.Reading("test", 99, 99));
        assertEquals(expected, mDb.getReadings(2));
        recorder.stopRecording();
        RunStats stats = mMetadata.getStats("runId", "test");
        assertEquals(100.0, stats.getStat(StatsAccumulator.KEY_NUM_DATA_POINTS), 0.001);
        assertEquals(3.0, stats.getStat(ZoomRecorder.STATS_KEY_TIER_COUNT), 0.001);

        recorder.startRecording("runId2");
        sensor.pushValue(0, 0);
        recorder.stopRecording();
        RunStats stats2 = mMetadata.getStats("runId2", "test");
        assertEquals(1.0, stats2.getStat(StatsAccumulator.KEY_NUM_DATA_POINTS), 0.001);
        assertEquals(1.0, stats2.getStat(ZoomRecorder.STATS_KEY_TIER_COUNT), 0.001);
    }

    @NonNull
    private ScaleTransform rpmToHertz() {
        ScaleTransform transform = new ScaleTransform();
        transform.sourceBottom = 0;
        transform.sourceTop = 60;
        transform.destBottom = 0;
        transform.destTop = 1;
        return transform;
    }
}