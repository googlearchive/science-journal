package com.google.android.apps.forscience.whistlepunk.filemetadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;

import org.junit.Test;

/**
 * Tests for the Trial class.
 */
public class TrialTest {

    @Test
    public void testTimestamps() {
        GoosciSensorLayout.SensorLayout[] layouts = new GoosciSensorLayout.SensorLayout[]{
                new GoosciSensorLayout.SensorLayout()};
        layouts[0].sensorId = "sensorId";
        Trial trial = new Trial(1000, layouts, "filename");
        trial.setRecordingEndTime(2000);

        assertEquals(trial.getOriginalFirstTimestamp(), 1000);
        assertEquals(trial.getOriginalLastTimestamp(), 2000);
        assertEquals(trial.getFirstTimestamp(), 1000);
        assertEquals(trial.getLastTimestamp(), 2000);
        assertEquals(trial.elapsedSeconds(), 1);
        assertTrue(trial.isValid());
    }

    @Test
    public void testTimestampsWithCrop() {
        GoosciSensorLayout.SensorLayout[] layouts = new GoosciSensorLayout.SensorLayout[]{
                new GoosciSensorLayout.SensorLayout()};
        layouts[0].sensorId = "sensorId";
        Trial trial = new Trial(1000, layouts, "filename");
        trial.setRecordingEndTime(4000);

        GoosciTrial.Range cropRange = new GoosciTrial.Range();
        cropRange.startMs = 2000;
        cropRange.endMs = 3000;
        trial.setCropRange(cropRange);

        assertEquals(trial.getOriginalFirstTimestamp(), 1000);
        assertEquals(trial.getOriginalLastTimestamp(), 4000);
        assertEquals(trial.getFirstTimestamp(), 2000);
        assertEquals(trial.getLastTimestamp(), 3000);
        assertEquals(trial.elapsedSeconds(), 1);
        assertTrue(trial.isValid());
    }

    @Test
    public void testInvalidTrial() {
        GoosciSensorLayout.SensorLayout[] layouts = new GoosciSensorLayout.SensorLayout[]{
                new GoosciSensorLayout.SensorLayout()};
        layouts[0].sensorId = "sensorId";
        Trial trial = new Trial(1000, layouts, "filename");
        assertFalse(trial.isValid());

        trial.setRecordingEndTime(999);
        assertFalse(trial.isValid());
        assertEquals(trial.elapsedSeconds(), 0);

        trial.setRecordingEndTime(2000);
        assertTrue(trial.isValid());
        assertEquals(trial.elapsedSeconds(), 1);
    }

    @Test
    public void testGetSensorInfo() {
        GoosciSensorLayout.SensorLayout[] layouts = new GoosciSensorLayout.SensorLayout[]{
                new GoosciSensorLayout.SensorLayout()};
        layouts[0].sensorId = "sensorId";
        Trial trial = new Trial(1000, layouts, "filename");
        trial.setRecordingEndTime(2000);
        assertTrue(trial.getSensorTags().contains("sensorId"));
    }
}
