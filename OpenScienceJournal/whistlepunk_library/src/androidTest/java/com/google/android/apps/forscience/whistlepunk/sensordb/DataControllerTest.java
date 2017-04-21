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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import android.content.Context;
import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.DataControllerImpl;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.NativeBleDiscoverer;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataControllerTest extends AndroidTestCase {
    // TODO: continue moving tests (as possible) to DataControllerUnitTest

    public void testLayouts() {
        final DataController dc = makeSimpleController();

        String experimentId = Arbitrary.string();
        List<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>();
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = Arbitrary.string();
        layouts.add(layout);
        dc.setSensorLayouts(experimentId, layouts, TestConsumers.<Success>expectingSuccess());

        StoringConsumer<List<GoosciSensorLayout.SensorLayout>> cLayouts = new StoringConsumer<>();
        dc.getSensorLayouts(experimentId, cLayouts);
        List<GoosciSensorLayout.SensorLayout> retrievedLayouts = cLayouts.getValue();

        assertEquals(1, retrievedLayouts.size());
        assertEquals(layout.sensorId, retrievedLayouts.get(0).sensorId);
    }

    private DataController makeSimpleController() {
        return new InMemorySensorDatabase().makeSimpleController(new MemoryMetadataManager(),
                bleProviderMap(getContext()));
    }

    public void testEnsureSensor() {
        BleSensorSpec spec = new BleSensorSpec("address", "name");
        final DataController dc = makeSimpleController();
        StoringConsumer<String> cSensorId = new StoringConsumer<>();
        String expectedId = ExternalSensorSpec.getSensorId(spec, 0);

        dc.addOrGetExternalSensor(spec, cSensorId);
        assertEquals(expectedId, cSensorId.getValue());

        dc.addOrGetExternalSensor(spec, cSensorId);
        assertEquals(expectedId, cSensorId.getValue());

        StoringConsumer<ExternalSensorSpec> cSpec = new StoringConsumer<>();
        dc.getExternalSensorById(expectedId, cSpec);
        assertEquals(spec.toString(), cSpec.getValue().toString());
    }

    @NonNull
    public static ImmutableMap<String, ExternalSensorProvider> bleProviderMap(Context context) {
        return ImmutableMap.<String, ExternalSensorProvider>of(BleSensorSpec.TYPE,
                new NativeBleDiscoverer(context).getProvider());
    }

    public void testAddDataError() {
        InMemorySensorDatabase failingDb = new InMemorySensorDatabase() {
            @Override
            public void addScalarReading(String databaseTag, int resolutionTier,
                    long timestampMillis, double value) {
                throw new RuntimeException("Could not add value " + value);
            }
        };
        RecordingDataController rdc = failingDb.makeSimpleRecordingController(
                new MemoryMetadataManager());
        StoringFailureListener listener = new StoringFailureListener();
        rdc.setDataErrorListenerForSensor("sensorId", listener);
        rdc.addScalarReading("sensorId", 0, 0, 3.0);

        // Different sensorId: won't be stored
        rdc.addScalarReading("notSensorId", 0, 0, 4.0);

        rdc.clearDataErrorListenerForSensor("sensorId");

        // Listener cleared: won't be stored
        rdc.addScalarReading("sensorId", 0, 0, 5.0);

        assertEquals("Could not add value 3.0", listener.exception.getMessage());
    }

    public void testUpdateTrialError() {
        MemoryMetadataManager failingMetadata = new MemoryMetadataManager() {
            @Override
            public void updateTrial(Trial trial) {
                throw new RuntimeException("Failed to store trial");
            }
        };
        final DataController dc = new InMemorySensorDatabase().makeSimpleController(
                failingMetadata);
        final StoringFailureListener listener = new StoringFailureListener();
        String sensorId = Arbitrary.string();

        Experiment experiment = new Experiment(10);
        dc.startTrial(experiment, Collections.<GoosciSensorLayout.SensorLayout>emptyList(),
                new LoggingConsumer<Trial>("DataControllerTest", "new trial") {
                    @Override
                    public void success(Trial value) {
                        value.setTitle("changed title");
                        dc.updateTrial(value, TestConsumers.<Success>expectingFailure(listener));
                    }
                });

        assertEquals("Failed to store trial", listener.exception.getMessage());
    }

    public void testReplaceChangesLayout() {
        final DataController dc = makeSimpleController();
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "oldSensorId";
        dc.addSensorToExperiment("experimentId", "oldSensorId",
                TestConsumers.<Success>expectingSuccess());
        dc.setSensorLayouts("experimentId", Lists.newArrayList(layout),
                TestConsumers.<Success>expectingSuccess());

        StoringConsumer<String> cid = new StoringConsumer<>();
        dc.addOrGetExternalSensor(new BleSensorSpec("address", "name"), cid);
        final String newSensorId = cid.getValue();
        dc.replaceSensorInExperiment("experimentId", "oldSensorId", newSensorId,
                TestConsumers.<Success>expectingSuccess());
        dc.getExternalSensorsByExperiment("experimentId", TestConsumers.expectingSuccess(
                new Consumer<ExperimentSensors>() {
                    @Override
                    public void take(ExperimentSensors sensors) {
                        assertEquals(Sets.newHashSet(newSensorId),
                                ConnectableSensor.makeMap(sensors).keySet());
                    }
                }));
        dc.getSensorLayouts("experimentId", TestConsumers.expectingSuccess(
                new Consumer<List<GoosciSensorLayout.SensorLayout>>() {
                    @Override
                    public void take(List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
                        assertEquals(1, sensorLayouts.size());
                        assertEquals(newSensorId, sensorLayouts.get(0).sensorId);
                    }
                }));
    }

    public void testRemoveChangesLayout() {
        final DataController dc = makeSimpleController();
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "oldSensorId";
        dc.addSensorToExperiment("experimentId", "oldSensorId",
                TestConsumers.<Success>expectingSuccess());
        dc.setSensorLayouts("experimentId", Lists.newArrayList(layout),
                TestConsumers.<Success>expectingSuccess());
        dc.removeSensorFromExperiment("experimentId", "oldSensorId",
                TestConsumers.<Success>expectingSuccess());

        dc.getSensorLayouts("experimentId", TestConsumers.expectingSuccess(
                new Consumer<List<GoosciSensorLayout.SensorLayout>>() {
                    @Override
                    public void take(List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
                        assertEquals(1, sensorLayouts.size());
                        assertEquals("", sensorLayouts.get(0).sensorId);
                    }
                }));
    }

    public void testGenerateLabelId() {
        IncrementableMonotonicClock clock = new IncrementableMonotonicClock();
        DataController dc = new DataControllerImpl(null, null, null, null, null,
                clock, null);
        clock.increment();

        String firstLabelId = dc.generateNewLabelId();
        String secondLabelId = dc.generateNewLabelId();
        String thirdLabelId = dc.generateNewLabelId();
        assertNotSame(firstLabelId, secondLabelId);
        assertNotSame(secondLabelId, thirdLabelId);

        clock.increment();
        String fourthLabelId = dc.generateNewLabelId();
        assertNotSame(thirdLabelId, fourthLabelId);
    }
}