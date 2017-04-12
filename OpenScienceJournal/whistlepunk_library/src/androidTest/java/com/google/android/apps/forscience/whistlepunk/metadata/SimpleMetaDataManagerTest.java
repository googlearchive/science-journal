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

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.support.annotation.NonNull;
import android.test.AndroidTestCase;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.NativeBleDiscoverer;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TextLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.common.collect.Lists;
import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link SimpleMetaDataManager}
 */
public class SimpleMetaDataManagerTest extends AndroidTestCase {

    private MetaDataManager mMetaDataManager;
    private TestSystemClock mTestSystemClock;

    public class TestSystemClock implements Clock {

        long testTime = 42;

        @Override
        public long getNow() {
            return testTime;
        }

        public void advanceClock() {
            testTime++;
        }
    };

    public void setUp() {
        mMetaDataManager = makeMetaDataManager();
    }

    @NonNull
    private SimpleMetaDataManager makeMetaDataManager() {
        mTestSystemClock = new TestSystemClock();
        return new SimpleMetaDataManager(getContext(), "test.main.db", mTestSystemClock);
    }

    public void tearDown() {
        getContext().getDatabasePath("test.main.db").delete();
    }

    public void testNewProject() {
        Project project = mMetaDataManager.newProject();
        assertNotNull(project);
        assertFalse(TextUtils.isEmpty(project.getProjectId()));
        assertNotSame(project.getId(), -1);

        List<Project> projects = mMetaDataManager.getProjects(10, false);
        boolean found = false;
        for (Project retrievedProject : projects) {
            if (retrievedProject.getProjectId().equals(project.getProjectId())) {
                found = true;
                break;
            }
        }
        assertTrue("New project not found in list of retrieved projects", found);

        // Delete to clean up the DB.
        mMetaDataManager.deleteProject(project);

        projects = mMetaDataManager.getProjects(10, false);
        found = false;
        for (Project retrievedProject : projects) {
            if (retrievedProject.getProjectId().equals(project.getProjectId())) {
                found = true;
                break;
            }
        }
        assertFalse("Project was not deleted", found);
    }

    public void testUpdateProject() {
        Project project = mMetaDataManager.newProject();
        project.setTitle("My title");
        project.setDescription("My description");
        project.setCoverPhoto("file://testcoverphoto");

        mMetaDataManager.updateProject(project);

        Project retrievedProject = mMetaDataManager.getProjectById(project.getProjectId());

        assertNotNull(retrievedProject);
        assertEquals("My title", retrievedProject.getTitle());
        assertEquals("My description", retrievedProject.getDescription());
        assertEquals("file://testcoverphoto", retrievedProject.getCoverPhoto());

        retrievedProject.setArchived(true);
        mMetaDataManager.updateProject(retrievedProject);
        Project archivedProject = mMetaDataManager.getProjectById(project.getProjectId());

        assertTrue(archivedProject.isArchived());
    }

    public void testNewExperiment() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        assertNotNull(experiment);
        assertFalse(TextUtils.isEmpty(experiment.getExperimentId()));
        assertNotSame(experiment.getId(), -1);
        assertTrue(experiment.getTimestamp() > 0);

        List<Experiment> experiments = mMetaDataManager.getExperimentsForProject(project, false);
        assertEquals(1, experiments.size());
        assertEquals(experiment.getExperimentId(), experiments.get(0).getExperimentId());
        assertTrue(experiments.get(0).getTimestamp() > 0);

        // Test adding a few experiments
        int count = 10;
        // Start at 1, we already added one.
        for (int index = 1; index < count; index++) {
            mMetaDataManager.newExperiment(project);
        }

        experiments = mMetaDataManager.getExperimentsForProject(project, false);
        assertEquals(count, experiments.size());

        mMetaDataManager.deleteProject(project);

        // Test that experiments were deleted.
        experiments = mMetaDataManager.getExperimentsForProject(project, false);
        assertEquals(0, experiments.size());
    }

    public void testUpdateExperiment() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        experiment.setTitle("new title");
        experiment.setDescription("my description");
        experiment.setLastUsedTime(123);

        mMetaDataManager.updateExperiment(experiment);

        Experiment retrieve = mMetaDataManager.getExperimentById(experiment.getExperimentId());
        assertEquals("new title", retrieve.getTitle());
        assertEquals("my description", retrieve.getDescription());
        assertEquals(123, retrieve.getLastUsedTime());
        assertFalse(retrieve.isArchived());
    }

    public void testArchiveExperiment() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        experiment.setTitle("new title");
        experiment.setDescription("my description");
        experiment.setLastUsedTime(123);
        experiment.setArchived(false);
        mMetaDataManager.updateExperiment(experiment);

        List<Experiment> experiments = mMetaDataManager.getExperimentsForProject(project, false);
        assertEquals(1, experiments.size());
        assertEquals(experiment.getExperimentId(), experiments.get(0).getExperimentId());

        // Now archive this.
        experiment.setArchived(true);
        mMetaDataManager.updateExperiment(experiment);
        experiments = mMetaDataManager.getExperimentsForProject(project, false);
        assertEquals(0, experiments.size());

        // Now search include archived experiments
        experiments = mMetaDataManager.getExperimentsForProject(project, true);
        assertEquals(1, experiments.size());
        assertEquals(experiment.getExperimentId(), experiments.get(0).getExperimentId());
    }

    public void testNewLabel() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        String testLabelString = "test label";
        Label textLabel = Label.newLabelWithValue(1, TextLabelValue.fromText(testLabelString));
        mMetaDataManager.addLabel(experiment.getExperimentId(),
                RecorderController.NOT_RECORDING_RUN_ID, textLabel);
        File tmpFile;
        try {
             tmpFile = File.createTempFile("testfile_" + experiment.getExperimentId(), "png");
        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not create temp file" + e.getMessage());
            return;
        }
        assertTrue(tmpFile.exists());
        // This mimics what PictureUtils does: adds a file scheme to the path.
        String testPicturePath = "file:" + tmpFile.getAbsolutePath();
        String testPictureCaption = "life, the universe, and everything";
        Label pictureLabel = Label.newLabelWithValue(2, PictureLabelValue.fromPicture(
                testPicturePath, testPictureCaption));
        mMetaDataManager.addLabel(experiment.getExperimentId(),
                RecorderController.NOT_RECORDING_RUN_ID, pictureLabel);

        List<Label> labels = mMetaDataManager.getLabelsForExperiment(experiment);
        assertEquals(2, labels.size());

        boolean foundText = false;
        boolean foundPicture = false;
        for (Label foundLabel : labels) {
            if (foundLabel.hasValueType(GoosciLabelValue.LabelValue.TEXT)) {
                assertEquals(testLabelString, ((TextLabelValue) foundLabel.getLabelValue(
                        GoosciLabelValue.LabelValue.TEXT)).getText());
                assertEquals(textLabel.getLabelId(), foundLabel.getLabelId());
                foundText = true;
            }
            if (foundLabel.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
                assertEquals(testPicturePath, ((PictureLabelValue) foundLabel.getLabelValue(
                        GoosciLabelValue.LabelValue.PICTURE)).getFilePath());
                assertEquals(pictureLabel.getLabelId(), foundLabel.getLabelId());
                assertEquals(testPictureCaption, ((PictureLabelValue) foundLabel.getLabelValue(
                        GoosciLabelValue.LabelValue.PICTURE)).getCaption());
                foundPicture = true;
            }
            assertTrue(foundLabel.getTimeStamp() > 0);
        }
        assertTrue("Text label was not saved.", foundText);
        assertTrue("Picture label was not saved.", foundPicture);

        // Test that labels get deleted.
        mMetaDataManager.deleteProject(project);
        labels = mMetaDataManager.getLabelsForExperiment(experiment);
        assertEquals(0, labels.size());

        // Test that the picture file got deleted.
        assertFalse(tmpFile.exists());
    }

    public void testLabelsWithStartId() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        final Label before = Label.newLabel(1);
        final Label during1 = Label.newLabel(2);
        final Label during2 = Label.newLabel(3);
        final Label after = Label.newLabel(4);
        before.setLabelValue(TextLabelValue.fromText("before"));
        during1.setLabelValue(TextLabelValue.fromText("during1"));
        during2.setLabelValue(TextLabelValue.fromText("during2"));
        after.setLabelValue(TextLabelValue.fromText("after"));
        mMetaDataManager.addLabel(experiment.getExperimentId(),
                RecorderController.NOT_RECORDING_RUN_ID, before);
        mMetaDataManager.addLabel(experiment.getExperimentId(), "duringStartId", during1);
        mMetaDataManager.addLabel(experiment.getExperimentId(), "duringStartId", during2);
        mMetaDataManager.addLabel(experiment.getExperimentId(),
                RecorderController.NOT_RECORDING_RUN_ID, after);
        final List<Label> labels = mMetaDataManager.getLabelsForTrial("duringStartId");
        assertEqualLabels(during1, labels.get(0));
        assertEqualLabels(during2, labels.get(1));
    }

    public void testExperimentStartIds() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        ApplicationLabel startId1 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START,
                "startId1", "startId1", 0);
        ApplicationLabel stopId1 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId1", "startId1", 1);
        final ApplicationLabel startId2 = new ApplicationLabel(
                ApplicationLabel.TYPE_RECORDING_START, "startId2", "startId2", 2);
        final Label label = Label.newLabel(3);
        label.setLabelValue(TextLabelValue.fromText("text"));
        ApplicationLabel stopId2 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId2", "startId2", 4);
        final ApplicationLabel startId3 = new ApplicationLabel(
                ApplicationLabel.TYPE_RECORDING_START, "startId3", "startId3", 5);
        final ApplicationLabel stopId3 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId3", "startId3", 6);
        String experimentId = experiment.getExperimentId();
        mMetaDataManager.addApplicationLabel(experimentId, startId1);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addApplicationLabel(experimentId, stopId1);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addApplicationLabel(experimentId, startId2);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addLabel(experimentId, RecorderController.NOT_RECORDING_RUN_ID, label);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addApplicationLabel(experimentId, stopId2);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addApplicationLabel(experimentId, startId3);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addApplicationLabel(experimentId, stopId3);
        mTestSystemClock.advanceClock();
        final List<String> experimentRunIds = mMetaDataManager.getExperimentRunIds(
                experiment.getExperimentId(), true);
        assertEquals(Lists.newArrayList(), experimentRunIds);
        mMetaDataManager.newTrial(experiment, startId1.getTrialId(), 0,
                new ArrayList<GoosciSensorLayout.SensorLayout>());
        mTestSystemClock.advanceClock();
        mMetaDataManager.newTrial(experiment, startId2.getTrialId(), 2,
                new ArrayList<GoosciSensorLayout.SensorLayout>());
        mTestSystemClock.advanceClock();
        mMetaDataManager.newTrial(experiment, startId3.getTrialId(), 5,
                new ArrayList<GoosciSensorLayout.SensorLayout>());
        mTestSystemClock.advanceClock();
        final List<String> experimentRunIds2 = mMetaDataManager.getExperimentRunIds(
                experiment.getExperimentId(), true);
        assertEquals(Lists.newArrayList("startId3", "startId2", "startId1"), experimentRunIds2);
    }

    private void assertEqualLabels(Label expected, Label actual) {
        assertTrue(MessageNano.messageNanoEquals(expected.getLabelProto(), actual.getLabelProto()));
    }

    public void testAddRemoveExternalSensor() {
        Map<String,ExternalSensorProvider> providerMap = getProviderMap();
        Map<String, ExternalSensorSpec> sensors = mMetaDataManager.getExternalSensors(providerMap);
        assertEquals(0, sensors.size());

        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        sensors = mMetaDataManager.getExternalSensors(providerMap);

        assertEquals(1, sensors.size());
        ExternalSensorSpec spec = sensors.values().iterator().next();
        assertTrue(spec instanceof BleSensorSpec);
        BleSensorSpec dbSensor = (BleSensorSpec) spec;
        assertEquals(testAddress, dbSensor.getAddress());
        assertEquals(testName, dbSensor.getName());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.removeExternalSensor(databaseTag);

        sensors = mMetaDataManager.getExternalSensors(providerMap);
        assertEquals(0, sensors.size());
    }

    private Map<String, ExternalSensorProvider> getProviderMap() {
        HashMap<String, ExternalSensorProvider> map = new HashMap<>();
        map.put(BleSensorSpec.TYPE, new NativeBleDiscoverer(getContext()).getProvider());
        map.put(ScalarInputSpec.TYPE, new ScalarInputDiscoverer(null, null,
                WhistlePunkApplication.getUsageTracker(null)).getProvider());
        return map;
    }

    public void testSensorsDifferentTypes() {
        Map<String, ExternalSensorProvider> providerMap = null;
        Map<String, ExternalSensorSpec> sensors = mMetaDataManager.getExternalSensors(providerMap);
        assertEquals(0, sensors.size());

        final String testAddress = "11:22:33:44:55";
        final String testName = "testName";
        final BleSensorSpec bleSpec = new BleSensorSpec(testAddress, testName);
        ExternalSensorSpec wackedSpec = new ExternalSensorSpec() {
            @Override
            public String getName() {
                return testName;
            }

            @Override
            public String getType() {
                return "wacked";
            }

            @Override
            public String getAddress() {
                return testAddress;
            }

            @Override
            public SensorAppearance getSensorAppearance() {
                return null;
            }

            @Override
            public byte[] getConfig() {
                return bleSpec.getConfig();
            }

            @Override
            public boolean shouldShowOptionsOnConnect() {
                return false;
            }
        };

        String bleId = mMetaDataManager.addOrGetExternalSensor(bleSpec, providerMap);
        String wackedId = mMetaDataManager.addOrGetExternalSensor(wackedSpec, providerMap);

        assertEquals("bluetooth_le-11:22:33:44:55-testName-0", bleId);
        assertEquals("wacked-11:22:33:44:55-testName-0", wackedId);
    }

    public void testGetExternalSensorsWithScalarInput() {
        Map<String, ExternalSensorProvider> providerMap = getProviderMap();
        assertEquals(0, mMetaDataManager.getExternalSensors(providerMap).size());
        mMetaDataManager.addOrGetExternalSensor(
                new ScalarInputSpec("name", "serviceId", "address", null, null, "deviceId"),
                providerMap);
        Map<String, ExternalSensorSpec> newSensors = mMetaDataManager.getExternalSensors(
                providerMap);
        assertEquals(1, newSensors.size());
        String id = "ScalarInput-serviceId&address-name-0";
        assertEquals(id, newSensors.keySet().iterator().next());
        ExternalSensorSpec spec = newSensors.get(id);
        assertEquals(ScalarInputSpec.TYPE, spec.getType());
        assertEquals("name", spec.getName());
        assertEquals("address", ((ScalarInputSpec)spec).getSensorAddressInService());
    }

    public void testSensorToExperiment() {
        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        Map<String, ExternalSensorProvider> providerMap = getProviderMap();
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        mMetaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

        Map<String, ExternalSensorSpec> sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentExternalSensors(experiment.getExperimentId(),
                        providerMap));
        assertEquals(1, sensors.size());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.removeSensorFromExperiment(databaseTag, experiment.getExperimentId());

        sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentExternalSensors(experiment.getExperimentId(),
                        providerMap));
        assertEquals(0, sensors.size());
    }

    public void testRemoveExternalSensorWithExperiment() {
        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        Map<String, ExternalSensorProvider> providerMap = getProviderMap();
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        mMetaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

        Map<String, ExternalSensorSpec> sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentExternalSensors(experiment.getExperimentId(),
                        providerMap));
        assertEquals(1, sensors.size());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.removeExternalSensor(databaseTag);

        // This should be gone now.
        sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentExternalSensors(experiment.getExperimentId(),
                        providerMap));
        assertEquals(0, sensors.size());
    }

    public void testRemoveExternalSensorWithProject() {
        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        Map<String, ExternalSensorProvider> providerMap = getProviderMap();
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        mMetaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

        Map<String, ExternalSensorSpec> sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentExternalSensors(
                        experiment.getExperimentId(), providerMap));
        assertEquals(1, sensors.size());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.deleteProject(project);

        // This should be gone now.
        sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentExternalSensors(experiment.getExperimentId(),
                        providerMap));
        assertEquals(0, sensors.size());
    }

    public void testStoreStats() {
        final TrialStats stats = new TrialStats("sensorId");

        stats.putStat(GoosciTrial.SensorStat.MINIMUM, 1);
        stats.putStat(GoosciTrial.SensorStat.AVERAGE, 2);
        stats.putStat(GoosciTrial.SensorStat.MAXIMUM, 3);

        mMetaDataManager.setStats("startLabelId", "sensorId", stats);
        mMetaDataManager.close();

        final TrialStats trialStats = makeMetaDataManager().getStats("startLabelId", "sensorId");

        assertEquals(1.0, trialStats.getStatValue(GoosciTrial.SensorStat.MINIMUM, -1), 0.001);
        assertEquals(2.0, trialStats.getStatValue(GoosciTrial.SensorStat.AVERAGE, -1), 0.001);
        assertEquals(3.0, trialStats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, -1), 0.001);
    }

    public void testRunStorage() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        final ApplicationLabel startLabel = newStartLabel("startId", 1);
        GoosciSensorLayout.SensorLayout layout1 = new GoosciSensorLayout.SensorLayout();
        layout1.sensorId = "sensor1";
        layout1.maximumYAxisValue = 5;
        GoosciSensorLayout.SensorLayout layout2 = new GoosciSensorLayout.SensorLayout();
        layout2.sensorId = "sensor2";
        final ArrayList<GoosciSensorLayout.SensorLayout> sensorLayouts =
                Lists.newArrayList(layout1, layout2);
        final ArrayList<String> sensorIds = Lists.newArrayList("sensor1", "sensor2");
        Trial saved = mMetaDataManager.newTrial(experiment, startLabel.getTrialId(),
                startLabel.getTimeStamp(), sensorLayouts);
        Trial loaded = mMetaDataManager.getTrial(startLabel.getLabelId(), Arrays.asList(startLabel),
                Collections.<Label>emptyList());
        assertEquals(startLabel.getLabelId(), saved.getTrialId());
        assertEquals(startLabel.getLabelId(), loaded.getTrialId());
        assertEquals(sensorIds, saved.getSensorIds());
        assertEquals(sensorIds, loaded.getSensorIds());
        assertEquals(5, saved.getSensorLayouts().get(0).maximumYAxisValue, 0.1);
        assertEquals(5, loaded.getSensorLayouts().get(0).maximumYAxisValue, 0.1);

        layout1.maximumYAxisValue = 15;
        mMetaDataManager.updateTrialLayouts(startLabel.getLabelId(), sensorLayouts);
        Trial updated = mMetaDataManager.getTrial(startLabel.getLabelId(),
                Arrays.asList(startLabel), Collections.<Label>emptyList());
        assertEquals(15, updated.getSensorLayouts().get(0).maximumYAxisValue, 0.1);

        // Test that runs are deleted.
        mMetaDataManager.deleteTrial(startLabel.getLabelId());
        loaded = mMetaDataManager.getTrial(startLabel.getLabelId(), Arrays.asList(startLabel),
                Collections.<Label>emptyList());
        assertNull(loaded);
    }

    public void testExperimentDelete() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        final ApplicationLabel startLabel = newStartLabel("startId", 1);
        GoosciSensorLayout.SensorLayout layout1 = new GoosciSensorLayout.SensorLayout();
        layout1.sensorId = "sensor1";
        GoosciSensorLayout.SensorLayout layout2 = new GoosciSensorLayout.SensorLayout();
        layout2.sensorId = "sensor2";
        final ArrayList<GoosciSensorLayout.SensorLayout> sensorLayouts =
                Lists.newArrayList(layout1, layout2);
        mMetaDataManager.addApplicationLabel(experiment.getExperimentId(), startLabel);
        mMetaDataManager.newTrial(experiment, startLabel.getTrialId(), startLabel.getTimeStamp(),
                sensorLayouts);
        mMetaDataManager.setExperimentSensorLayouts(experiment.getExperimentId(), sensorLayouts);

        mMetaDataManager.deleteExperiment(experiment);

        assertNull(mMetaDataManager.getExperimentById(experiment.getExperimentId()));
        // Test that runs are deleted when deleting experiments.
        assertNull(mMetaDataManager.getTrial(startLabel.getLabelId(), Arrays.asList(startLabel),
                Collections.<Label>emptyList()));
        // Test that sensor layouts are gone.
        assertEquals(0, mMetaDataManager.getExperimentSensorLayouts(experiment.getExperimentId())
                .size());
    }

    public void testProjectDelete() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        final ApplicationLabel startLabel = newStartLabel("startId", 1);
        GoosciSensorLayout.SensorLayout layout1 = new GoosciSensorLayout.SensorLayout();
        layout1.sensorId = "sensor1";
        GoosciSensorLayout.SensorLayout layout2 = new GoosciSensorLayout.SensorLayout();
        layout2.sensorId = "sensor2";
        final ArrayList<GoosciSensorLayout.SensorLayout> sensorLayouts =
                Lists.newArrayList(layout1, layout2);
        mMetaDataManager.addApplicationLabel(experiment.getExperimentId(), startLabel);
        Trial saved = mMetaDataManager.newTrial(experiment, startLabel.getTrialId(),
                startLabel.getTimeStamp(), sensorLayouts);

        mMetaDataManager.deleteProject(project);

        assertNull(mMetaDataManager.getProjectById(project.getProjectId()));

        // Test that experiments and runs are deleted when deleting projects.
        assertNull(mMetaDataManager.getExperimentById(experiment.getExperimentId()));
        assertNull(mMetaDataManager.getTrial(startLabel.getLabelId(), Arrays.asList(startLabel),
                Collections.<Label>emptyList()));
        assertEquals(0, mMetaDataManager.getLabelsForTrial(startLabel.getLabelId()).size());
    }

    public void testExperimentSensorLayout() {
        GoosciSensorLayout.SensorLayout layout1 = new GoosciSensorLayout.SensorLayout();
        layout1.sensorId = "sensorId1";
        GoosciSensorLayout.SensorLayout layout2 = new GoosciSensorLayout.SensorLayout();
        layout2.sensorId = "sensorId2";
        String experimentId = Arbitrary.string();
        mMetaDataManager.setExperimentSensorLayouts(experimentId, Lists.newArrayList(layout1,
                layout2));

        assertEquals(Lists.newArrayList(layout1.sensorId, layout2.sensorId),
                getIds(mMetaDataManager.getExperimentSensorLayouts(experimentId)));

        GoosciSensorLayout.SensorLayout layout3 = new GoosciSensorLayout.SensorLayout();
        layout3.sensorId = "sensorId3";
        mMetaDataManager.setExperimentSensorLayouts(experimentId, Lists.newArrayList(layout3));

        assertEquals(Lists.newArrayList(layout3.sensorId),
                getIds(mMetaDataManager.getExperimentSensorLayouts(experimentId)));

        // Try storing duplicate
        mMetaDataManager.setExperimentSensorLayouts(experimentId, Lists.newArrayList(layout3,
                layout3));
        // duplicates are removed
        assertEquals(Lists.newArrayList(layout3.sensorId),
                getIds(mMetaDataManager.getExperimentSensorLayouts(experimentId)));
    }

    public void testExternalSensorOrder() {
        Map<String,ExternalSensorProvider> providerMap = getProviderMap();
        String id1 = mMetaDataManager.addOrGetExternalSensor(new BleSensorSpec("address1", "name1"),
                providerMap);
        String id2 = mMetaDataManager.addOrGetExternalSensor(new BleSensorSpec("address2", "name2"),
                providerMap);
        String id3 = mMetaDataManager.addOrGetExternalSensor(new BleSensorSpec("address3", "name3"),
                providerMap);
        mMetaDataManager.addSensorToExperiment(id1, "experimentId");
        mMetaDataManager.addSensorToExperiment(id2, "experimentId");
        mMetaDataManager.addSensorToExperiment(id3, "experimentId");
        List<ConnectableSensor> sensors = mMetaDataManager.getExperimentExternalSensors(
                "experimentId", providerMap).getIncludedSensors();
        assertEquals(id1, sensors.get(0).getConnectedSensorId());
        assertEquals(id2, sensors.get(1).getConnectedSensorId());
        assertEquals(id3, sensors.get(2).getConnectedSensorId());
    }

    public void testExternalSensorDuplication() {
        Map<String,ExternalSensorProvider> providerMap = getProviderMap();
        String id1 = mMetaDataManager.addOrGetExternalSensor(new BleSensorSpec("address1", "name1"),
                providerMap);
        mMetaDataManager.addSensorToExperiment(id1, "experimentId");
        mMetaDataManager.addSensorToExperiment(id1, "experimentId");
        List<ConnectableSensor> sensors = mMetaDataManager.getExperimentExternalSensors(
                "experimentId", providerMap).getIncludedSensors();
        assertEquals(1, sensors.size());
    }

    public void testMyDevices() {
        assertEquals(0, mMetaDataManager.getMyDevices().size());
        InputDeviceSpec device1 = new InputDeviceSpec(ScalarInputSpec.TYPE, "deviceAddress1",
                "deviceName1");
        InputDeviceSpec device2 = new InputDeviceSpec(ScalarInputSpec.TYPE, "deviceAddress2",
                "deviceName2");

        mMetaDataManager.addMyDevice(device1);
        assertEquals(1, mMetaDataManager.getMyDevices().size());

        mMetaDataManager.addMyDevice(device1);
        assertEquals(1, mMetaDataManager.getMyDevices().size());

        mMetaDataManager.addMyDevice(device2);
        assertEquals(2, mMetaDataManager.getMyDevices().size());

        assertTrue(device1.isSameSensor(mMetaDataManager.getMyDevices().get(0)));
        assertTrue(device2.isSameSensor(mMetaDataManager.getMyDevices().get(1)));

        mMetaDataManager.removeMyDevice(device1);
        assertEquals(1, mMetaDataManager.getMyDevices().size());

        mMetaDataManager.removeMyDevice(device2);
        assertEquals(0, mMetaDataManager.getMyDevices().size());
    }

    private List<String> getIds(List<GoosciSensorLayout.SensorLayout> layouts) {
        List<String> ids = new ArrayList<>();
        for (GoosciSensorLayout.SensorLayout layout : layouts) {
            ids.add(layout.sensorId);
        }
        return ids;
    }

    @NonNull
    private ApplicationLabel newStartLabel(String id, int timestampMillis) {
        return new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, id, id, timestampMillis);
    }
}
