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
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.List;

public class DataControllerTest extends AndroidTestCase {
    public static String TAG = "DataControllerTest";
    // TODO: continue moving tests (as possible) to DataControllerUnitTest

    public void testLayouts() {
        final DataController dc = makeSimpleController();

        final List<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>();
        final GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = Arbitrary.string();
        layouts.add(layout);

        StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        dc.createExperiment(cExperiment);
        Experiment experiment = cExperiment.getValue();
        experiment.setSensorLayouts(layouts);
        dc.updateExperiment(experiment.getExperimentId(),
                TestConsumers.<Success>expectingSuccess());
        dc.getExperimentById(experiment.getExperimentId(),
                new LoggingConsumer<Experiment>(TAG, "test get experiment") {
                    @Override
                    public void success(Experiment updated) {
                        List<GoosciSensorLayout.SensorLayout> retrievedLayouts =
                                updated.getSensorLayouts();
                        assertEquals(1, retrievedLayouts.size());
                        assertEquals(layout.sensorId, retrievedLayouts.get(0).sensorId);
                    }
                });
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

    public void testNewExperimentError() {
        MemoryMetadataManager failingMetadata = new MemoryMetadataManager() {
            @Override
            public Experiment newExperiment() {
                throw new RuntimeException("Failed to make experiment");
            }
        };
        final DataController dc = new InMemorySensorDatabase().makeSimpleController(
                failingMetadata);
        final StoringFailureListener listener = new StoringFailureListener();

        dc.createExperiment(TestConsumers.<Experiment>expectingFailure(listener));
        assertEquals("Failed to make experiment", listener.exception.getMessage());
    }

    public void testReplaceChangesLayout() {
        final DataController dc = makeSimpleController();
        StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        dc.createExperiment(cExperiment);
        Experiment experiment = cExperiment.getValue();
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "oldSensorId";
        List<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>(1);
        layouts.add(layout);
        experiment.setSensorLayouts(layouts);
        dc.updateExperiment(experiment.getExperimentId(),
                TestConsumers.<Success>expectingSuccess());
        dc.addSensorToExperiment(experiment.getExperimentId(), "oldSensorId",
                TestConsumers.<Success>expectingSuccess());

        StoringConsumer<String> cid = new StoringConsumer<>();
        dc.addOrGetExternalSensor(new BleSensorSpec("address", "name"), cid);
        final String newSensorId = cid.getValue();
        dc.replaceSensorInExperiment(experiment.getExperimentId(), "oldSensorId", newSensorId,
                TestConsumers.<Success>expectingSuccess());
        dc.getExternalSensorsByExperiment(experiment.getExperimentId(),
                TestConsumers.expectingSuccess(new Consumer<ExperimentSensors>() {
                    @Override
                    public void take(ExperimentSensors sensors) {
                        assertEquals(Sets.newHashSet(newSensorId),
                                ConnectableSensor.makeMap(sensors).keySet());
                    }
                }));
        dc.getExperimentById(experiment.getExperimentId(),
                new LoggingConsumer<Experiment>(TAG, "get updated experiment") {
                    @Override
                    public void success(Experiment updated) {
                        assertEquals(1, updated.getSensorLayouts().size());
                        assertEquals(newSensorId,
                                updated.getSensorLayouts().get(0).sensorId);
                    }
                });
    }

    public void testRemoveChangesLayout() {
        final DataController dc = makeSimpleController();
        StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        dc.createExperiment(cExperiment);
        Experiment experiment = cExperiment.getValue();
        dc.addSensorToExperiment(experiment.getExperimentId(), "oldSensorId",
                TestConsumers.<Success>expectingSuccess());

        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "oldSensorId";
        List<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>(1);
        layouts.add(layout);
        experiment.setSensorLayouts(layouts);
        dc.updateExperiment(experiment.getExperimentId(),
                TestConsumers.<Success>expectingSuccess());

        dc.removeSensorFromExperiment(experiment.getExperimentId(), "oldSensorId",
                TestConsumers.<Success>expectingSuccess());
        dc.getExperimentById(experiment.getExperimentId(),
                new LoggingConsumer<Experiment>(TAG, "get updated experiment") {
                    @Override
                    public void success(Experiment updated) {
                        assertEquals(1, updated.getSensorLayouts().size());
                        assertEquals("",
                                updated.getSensorLayouts().get(0).sensorId);
                    }
                });
    }

    public void testGenerateLabelId() {
        IncrementableMonotonicClock clock = new IncrementableMonotonicClock();
        DataController dc = new DataControllerImpl(null, null, null, null, null, clock, null,
                new ConnectableSensor.Connector(null));
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