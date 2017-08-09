/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.SystemScheduler;
import com.google.common.collect.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class SensorCardPresenterTest {
    private RecorderControllerImpl mRecorderController;
    private SensorRegistry mSensorRegistry;
    private List<String> mStoppedSensorIds = new ArrayList<>();

    @Test
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
        return ExplodingFactory.makeListener();
    }

    @Test
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

    @Test
    public void testCanStillUseCardDefaultsIfNoSensor() {
        SensorCardPresenter scp = createSCP();
        NewOptionsStorage storage = scp.getCardOptions(null, getContext());
        WriteableSensorOptions writeable = storage.load(explodingListener());
        writeable.put("key", "value");
        assertEquals("value", writeable.getReadOnly().getString("key", "default"));
    }

    @Test
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

    @Test
    public void testKeepOrder() {
        SensorCardPresenter scp = createSCP();
        setSensorId(scp, "selected", "displayName");
        scp.updateAvailableSensors(Lists.newArrayList("available1", "available2", "available3"),
                Lists.<String>newArrayList("available1", "selected", "available2",
                        "selectedElsewhere", "available3"));
        assertEquals(Lists.newArrayList("available1", "selected", "available2",
                "available3"), scp.getAvailableSensorIds());
    }

    @Test
    public void testRetry() {
        ManualSensor sensor = new ManualSensor("sensorId", 100, 100);
        getSensorRegistry().addBuiltInSensor(sensor);
        SensorCardPresenter scp = createSCP();
        setSensorId(scp, "sensorId", "Sensor Name");
        scp.startObserving(sensor, sensor.createPresenter(null, null, null), null,
                Experiment.newExperiment(10, "localExperimentId", 0), getSensorRegistry());
        sensor.simulateExternalEventPreventingObservation();
        assertFalse(sensor.isObserving());
        scp.retryConnection(getContext());
        assertTrue(sensor.isObserving());
    }

    @Test
    public void testDestroyWithBlankSensorId() {
        SensorCardPresenter scp = createSCP();
        // Don't stop anything if we've never had a sensorId
        scp.destroy();
        assertEquals(0, mStoppedSensorIds.size());
    }

    @Test public void defensiveCopies() {
        SensorCardPresenter scp = createSCP();
        setSensorId(scp, "rightId", "rightName");
        GoosciSensorLayout.SensorLayout firstLayout = scp.buildLayout();
        setSensorId(scp, "wrongId", "rightName");
        GoosciSensorLayout.SensorLayout secondLayout = scp.buildLayout();
        assertEquals("rightId", firstLayout.sensorId);
        assertEquals("wrongId", secondLayout.sensorId);
    }

    private void setSensorId(SensorCardPresenter scp, String sensorId, String sensorDisplayName) {
        scp.setAppearanceProvider(new FakeAppearanceProvider());
        scp.setUiForConnectingNewSensor(sensorId, sensorDisplayName, "units", false);
    }

    @NonNull
    private SensorCardPresenter createSCP() {
        return createSCP(new GoosciSensorLayout.SensorLayout());
    }

    @NonNull
    private SensorCardPresenter createSCP(GoosciSensorLayout.SensorLayout layout) {
        return new SensorCardPresenter(
                new DataViewOptions(0, getContext(), new ScalarDisplayOptions()),
                new SensorSettingsControllerImpl(getContext()), getRecorderController(),
                layout, "", null, null);
    }

    @NonNull
    private RecorderControllerImpl getRecorderController() {
        if (mRecorderController == null) {
            mRecorderController = new RecorderControllerImpl(getContext(),
                    new MemorySensorEnvironment(null, null, null, null),
                    new RecorderListenerRegistry(), null, null, new SystemScheduler(), Delay.ZERO,
                    new FakeAppearanceProvider()) {
                @Override
                public void stopObserving(String sensorId, String observerId) {
                    mStoppedSensorIds.add(sensorId);
                    super.stopObserving(sensorId, observerId);
                }
            };
        }
        return mRecorderController;
    }

    private SensorRegistry getSensorRegistry() {
        if (mSensorRegistry == null) {
            mSensorRegistry = SensorRegistry.createWithBuiltinSensors(getContext());
        }
        return mSensorRegistry;
    }

    private Context getContext() {
        return RuntimeEnvironment.application.getApplicationContext();
    }
}