package com.google.android.apps.forscience.whistlepunk;

import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;

public class SensorCardPresenterTest extends AndroidTestCase {
    public void testExtrasIncludedInLayout() {
        SensorCardPresenter scp = createSCP();
        String key = Arbitrary.string();
        String value = Arbitrary.string();
        scp.getCardOptions(new DecibelSensor(), getContext()).load(explodingListener()).put(key,
                value);
        GoosciSensorLayout.SensorLayout.ExtrasEntry[] extras = scp.buildLayout().extras;
        assertEquals(1, extras.length);
        assertEquals(key, extras[0].key);
        assertEquals(value, extras[0].value);
    }

    private FailureListener explodingListener() {
        return new ExplodingFactory().makeListenerForOperation("");
    }

    public void testUseSensorDefaults() {
        DecibelSensor sensor = new DecibelSensor();
        String key = Arbitrary.string();
        String value = Arbitrary.string();
        sensor.getStorageForSensorDefaultOptions(getContext()).load(explodingListener()).put(key,
                value);
        SensorCardPresenter scp = createSCP();
        ReadableSensorOptions read = scp.getCardOptions(sensor, getContext()).load(
                explodingListener()).getReadOnly();
        assertEquals(value, read.getString(key, null));
    }

    public void testExtrasOverrideSensorDefaults() {
        DecibelSensor sensor = new DecibelSensor();
        String key = Arbitrary.string();
        String value = "fromSensorDefault";
        sensor.getStorageForSensorDefaultOptions(getContext()).load(explodingListener()).put(key,
                value);

        LocalSensorOptionsStorage localStorage = new LocalSensorOptionsStorage();
        localStorage.load().put(key, "fromCardSettings");
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.extras = localStorage.exportAsLayoutExtras();

        SensorCardPresenter scp = createSCP(layout);
        ReadableSensorOptions read = scp.getCardOptions(sensor, getContext()).load(
                explodingListener()).getReadOnly();
        assertEquals("fromCardSettings", read.getString(key, null));
    }

    @NonNull
    private SensorCardPresenter createSCP() {
        return createSCP(new GoosciSensorLayout.SensorLayout());
    }

    @NonNull
    private SensorCardPresenter createSCP(GoosciSensorLayout.SensorLayout layout) {
        return new SensorCardPresenter(
                new DataViewOptions(0, new ScalarDisplayOptions()),
                new SensorSettingsControllerImpl(getContext()),
                new RecorderControllerImpl(getContext()),
                layout);
    }
}