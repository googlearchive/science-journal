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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.NativeBleDiscoverer;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link SimpleMetaDataManager}
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class SimpleMetaDataManagerTest {

    private MetaDataManager mMetaDataManager;
    private TestSystemClock mTestSystemClock;
    private static File sFilePath;

    public static class TestSystemClock implements Clock {

        long testTime = 42;

        @Override
        public long getNow() {
            return testTime;
        }

        public void advanceClock() {
            testTime++;
        }
    };

    @Before
    public void setUp() {
        mMetaDataManager = makeMetaDataManager();
    }

    private static Context getContext() {
        return Shadows.shadowOf(RuntimeEnvironment.application).getApplicationContext();
    }

    @NonNull
    private SimpleMetaDataManager makeMetaDataManager() {
        mTestSystemClock = new TestSystemClock();
        sFilePath = getContext().getDatabasePath("test.main.db");
        return new SimpleMetaDataManager(getContext(), "test.main.db", mTestSystemClock);
    }

    @AfterClass
    public static void tearDown() {
        if (sFilePath != null) {
            sFilePath.delete();
            sFilePath = null;
        }
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testNewLabel() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        String testLabelString = "test label";
        TextLabel textLabel = new TextLabel(testLabelString, "textId", Arbitrary.string(), 1);
        mMetaDataManager.addLabel(experiment, textLabel);
        File tmpFile;
        try {
             tmpFile = File.createTempFile("testfile_" + experiment.getExperimentId(), "png");
        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not create temp file" + e.getMessage());
            return;
        }
        assertTrue(tmpFile.exists());
        String testPicturePath = tmpFile.getAbsolutePath();
        String testPictureCaption = "life, the universe, and everything";
        PictureLabel pictureLabel = new PictureLabel(testPicturePath, testPictureCaption,
                "pictureId", Arbitrary.string(), 2);
        mMetaDataManager.addLabel(experiment, pictureLabel);

        List<Label> labels = mMetaDataManager.getLabelsForExperiment(experiment);
        assertEquals(2, labels.size());

        boolean foundText = false;
        boolean foundPicture = false;
        for (Label foundLabel : labels) {
            if (foundLabel instanceof TextLabel) {
                assertEquals(testLabelString, ((TextLabel) foundLabel).getText());
                assertEquals("textId", foundLabel.getLabelId());
                foundText = true;
            }
            if (foundLabel instanceof PictureLabel) {
                assertEquals(testPicturePath, ((PictureLabel) foundLabel).getFilePath());
                assertEquals("pictureId", foundLabel.getLabelId());
                assertEquals(testPictureCaption, ((PictureLabel) foundLabel).getCaption());
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

    @Test
    public void testLabelsWithStartId() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        final TextLabel before = new TextLabel("beforeText", "beforeId", "beforeStartId", 1);
        final TextLabel during1 = new TextLabel("during1Text", "during1Id", "duringStartId", 2);
        final TextLabel during2 = new TextLabel("during2Text", "during2Id", "duringStartId", 3);
        final TextLabel after = new TextLabel("afterText", "afterId", "afterStartId", 4);
        mMetaDataManager.addLabel(experiment, before);
        mMetaDataManager.addLabel(experiment, during1);
        mMetaDataManager.addLabel(experiment, during2);
        mMetaDataManager.addLabel(experiment, after);
        final List<Label> labels = mMetaDataManager.getLabelsWithStartId("duringStartId");
        assertEqualLabels(during1, labels.get(0));
        assertEqualLabels(during2, labels.get(1));
    }

    @Test
    public void testExperimentStartIds() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        ApplicationLabel startId1 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START,
                "startId1", "startId1", 0);
        ApplicationLabel stopId1 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId1", "startId1", 1);
        final ApplicationLabel startId2 = new ApplicationLabel(
                ApplicationLabel.TYPE_RECORDING_START, "startId2", "startId2", 2);
        final TextLabel textLabel = new TextLabel("during2Text", "during2Id", "startId2", 3);
        ApplicationLabel stopId2 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId2", "startId2", 4);
        final ApplicationLabel startId3 = new ApplicationLabel(
                ApplicationLabel.TYPE_RECORDING_START, "startId3", "startId3", 5);
        final ApplicationLabel stopId3 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId3", "startId3", 6);
        mMetaDataManager.addLabel(experiment, startId1);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addLabel(experiment, stopId1);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addLabel(experiment, startId2);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addLabel(experiment, textLabel);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addLabel(experiment, stopId2);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addLabel(experiment, startId3);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addLabel(experiment, stopId3);
        mTestSystemClock.advanceClock();
        final List<String> experimentRunIds = mMetaDataManager.getExperimentRunIds(
                experiment.getExperimentId(), true);
        assertEquals(Lists.newArrayList(), experimentRunIds);
        mMetaDataManager.newRun(experiment, startId1.getRunId(),
                new ArrayList<GoosciSensorLayout.SensorLayout>());
        mTestSystemClock.advanceClock();
        mMetaDataManager.newRun(experiment, startId2.getRunId(),
                new ArrayList<GoosciSensorLayout.SensorLayout>());
        mTestSystemClock.advanceClock();
        mMetaDataManager.newRun(experiment, startId3.getRunId(),
                new ArrayList<GoosciSensorLayout.SensorLayout>());
        mTestSystemClock.advanceClock();
        final List<String> experimentRunIds2 = mMetaDataManager.getExperimentRunIds(
                experiment.getExperimentId(), true);
        assertEquals(Lists.newArrayList("startId3", "startId2", "startId1"), experimentRunIds2);
    }

    private void assertEqualLabels(TextLabel expected, Label actual) {
        assertTrue(actual instanceof TextLabel);
        TextLabel actualText = (TextLabel) actual;
        assertEquals(expected.getText(), actualText.getText());
        assertEquals(expected.getLabelId(), actualText.getLabelId());
        assertEquals(expected.getRunId(), actualText.getRunId());
    }

    @Test
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
        map.put(BleSensorSpec.TYPE, new NativeBleDiscoverer().getProvider());
        map.put(ScalarInputSpec.TYPE, new ScalarInputDiscoverer(null, null).getProvider());
        return map;
    }

    @Test
    public void testSensorsDifferentTypes() {
        Map<String, ExternalSensorProvider> providerMap = null;
        Map<String, ExternalSensorSpec> sensors = mMetaDataManager.getExternalSensors(providerMap);
        assertEquals(0, sensors.size());

        final String testAddress = "11:22:33:44:55";
        final String testName = "testName";
        final BleSensorSpec bleSpec = new BleSensorSpec(testAddress, testName);
        ExternalSensorSpec wackedSpec = new ExternalSensorSpec() {
            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {

            }

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
            protected void loadFromConfig(byte[] data) {

            }
        };

        String bleId = mMetaDataManager.addOrGetExternalSensor(bleSpec, providerMap);
        String wackedId = mMetaDataManager.addOrGetExternalSensor(wackedSpec, providerMap);

        assertEquals("bluetooth_le-11:22:33:44:55-testName-0", bleId);
        assertEquals("wacked-11:22:33:44:55-testName-0", wackedId);
    }

    @Test
    public void testGetExternalSensorsWithScalarInput() {
        Map<String, ExternalSensorProvider> providerMap = getProviderMap();
        assertEquals(0, mMetaDataManager.getExternalSensors(providerMap).size());
        mMetaDataManager.addOrGetExternalSensor(new ScalarInputSpec("name", "serviceId", "address"),
                providerMap);
        Map<String, ExternalSensorSpec> newSensors = mMetaDataManager.getExternalSensors(
                providerMap);
        assertEquals(1, newSensors.size());
        String id = "ScalarInput-address-name-0";
        assertEquals(id, newSensors.keySet().iterator().next());
        ExternalSensorSpec spec = newSensors.get(id);
        assertEquals(ScalarInputSpec.TYPE, spec.getType());
        assertEquals("name", spec.getName());
        assertEquals("address", spec.getAddress());
    }

    @Test
    public void testSensorToExperiment() {
        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        Map<String, ExternalSensorProvider> providerMap = getProviderMap();
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        mMetaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

        Map<String, ExternalSensorSpec> sensors = mMetaDataManager.getExperimentExternalSensors(
                experiment.getExperimentId(), providerMap);
        assertEquals(1, sensors.size());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.removeSensorFromExperiment(databaseTag, experiment.getExperimentId());

        sensors = mMetaDataManager.getExperimentExternalSensors(experiment.getExperimentId(),
                providerMap);
        assertEquals(0, sensors.size());
    }

    @Test
    public void testRemoveExternalSensorWithExperiment() {
        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        Map<String, ExternalSensorProvider> providerMap = getProviderMap();
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        mMetaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

        Map<String, ExternalSensorSpec> sensors = mMetaDataManager.getExperimentExternalSensors(
                experiment.getExperimentId(), providerMap);
        assertEquals(1, sensors.size());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.removeExternalSensor(databaseTag);

        // This should be gone now.
        sensors = mMetaDataManager.getExperimentExternalSensors(experiment.getExperimentId(),
                providerMap);
        assertEquals(0, sensors.size());
    }

    @Test
    public void testRemoveExternalSensorWithProject() {
        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        Map<String, ExternalSensorProvider> providerMap = getProviderMap();
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);

        mMetaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

        Map<String, ExternalSensorSpec> sensors = mMetaDataManager.getExperimentExternalSensors(
                experiment.getExperimentId(), providerMap);
        assertEquals(1, sensors.size());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.deleteProject(project);

        // This should be gone now.
        sensors = mMetaDataManager.getExperimentExternalSensors(experiment.getExperimentId(),
                providerMap);
        assertEquals(0, sensors.size());
    }

    @Test
    public void testStoreStats() {
        final RunStats stats = new RunStats();

        final List<String> strings = Arbitrary.distinctStrings(3);
        final String key1 = strings.get(0);
        final String key2 = strings.get(1);
        final String key3 = strings.get(2);

        stats.putStat(key1, 1);
        stats.putStat(key2, 2);
        stats.putStat(key3, 3);

        mMetaDataManager.setStats("startLabelId", "sensorId", stats);
        mMetaDataManager.close();

        final RunStats runStats = makeMetaDataManager().getStats("startLabelId", "sensorId");
        assertEquals(Sets.newHashSet(key1, key2, key3), runStats.getKeys());

        assertEquals(1.0, runStats.getStat(key1), 0.001);
        assertEquals(2.0, runStats.getStat(key2), 0.001);
        assertEquals(3.0, runStats.getStat(key3), 0.001);
    }

    @Test
    public void testRunStorage() {
        Project project = mMetaDataManager.newProject();
        Experiment experiment = mMetaDataManager.newExperiment(project);
        final ApplicationLabel startLabel = newStartLabel("startId", 1);
        GoosciSensorLayout.SensorLayout layout1 = new GoosciSensorLayout.SensorLayout();
        layout1.sensorId = "sensor1";
        GoosciSensorLayout.SensorLayout layout2 = new GoosciSensorLayout.SensorLayout();
        layout2.sensorId = "sensor2";
        final ArrayList<GoosciSensorLayout.SensorLayout> sensorLayouts =
                Lists.newArrayList(layout1, layout2);
        final ArrayList<String> sensorIds = Lists.newArrayList("sensor1", "sensor2");
        Run saved = mMetaDataManager.newRun(experiment, startLabel.getRunId(), sensorLayouts);
        Run loaded = mMetaDataManager.getRun(startLabel.getLabelId());
        assertEquals(startLabel.getLabelId(), saved.getId());
        assertEquals(startLabel.getLabelId(), loaded.getId());
        assertEquals(sensorIds, saved.getSensorIds());
        assertEquals(sensorIds, loaded.getSensorIds());

        // Test that runs are deleted.
        mMetaDataManager.deleteRun(startLabel.getLabelId());
        loaded = mMetaDataManager.getRun(startLabel.getLabelId());
        assertNull(loaded);
    }

    @Test
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
        mMetaDataManager.addLabel(experiment, startLabel);
        mMetaDataManager.newRun(experiment, startLabel.getRunId(), sensorLayouts);
        mMetaDataManager.setExperimentSensorLayouts(experiment.getExperimentId(), sensorLayouts);

        mMetaDataManager.deleteExperiment(experiment);

        assertNull(mMetaDataManager.getExperimentById(experiment.getExperimentId()));
        // Test that runs are deleted when deleting experiments.
        assertNull(mMetaDataManager.getRun(startLabel.getLabelId()));
        // Test that sensor layouts are gone.
        assertEquals(0, mMetaDataManager.getExperimentSensorLayouts(experiment.getExperimentId())
                .size());
    }

    @Test
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
        mMetaDataManager.addLabel(experiment, startLabel);
        Run saved = mMetaDataManager.newRun(experiment, startLabel.getRunId(), sensorLayouts);

        mMetaDataManager.deleteProject(project);

        assertNull(mMetaDataManager.getProjectById(project.getProjectId()));

        // Test that experiments and runs are deleted when deleting projects.
        assertNull(mMetaDataManager.getExperimentById(experiment.getExperimentId()));
        assertNull(mMetaDataManager.getRun(startLabel.getLabelId()));
        assertEquals(0, mMetaDataManager.getLabelsWithStartId(startLabel.getLabelId()).size());
    }

    @Test
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
