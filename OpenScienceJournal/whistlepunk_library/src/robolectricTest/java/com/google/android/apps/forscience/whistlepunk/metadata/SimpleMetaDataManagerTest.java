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

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.NativeBleDiscoverer;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.common.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link SimpleMetaDataManager}
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class SimpleMetaDataManagerTest {

    private SimpleMetaDataManager mMetaDataManager;
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
    }

    @Before
    public void setUp() {
        mMetaDataManager = makeMetaDataManager();
    }

    @NonNull
    private SimpleMetaDataManager makeMetaDataManager() {
        mTestSystemClock = new TestSystemClock();
        return new SimpleMetaDataManager(getContext(), "test.main.db", mTestSystemClock);
    }

    @After
    public void tearDown() {
        getContext().getDatabasePath("test.main.db").delete();
        File sharedMetadataFile = FileMetadataManager.getUserMetadataFile(getContext());
        sharedMetadataFile.delete();
    }

    @Test
    public void testNewExperiment() {
        Experiment experiment = mMetaDataManager.newDatabaseExperiment();
        assertNotNull(experiment);
        assertFalse(TextUtils.isEmpty(experiment.getExperimentId()));
        assertTrue(experiment.getCreationTimeMs() > 0);

        List<GoosciUserMetadata.ExperimentOverview> experiments =
                mMetaDataManager.getDatabaseExperimentOverviews(false);
        assertEquals(1, experiments.size());
        assertEquals(experiment.getExperimentId(), experiments.get(0).experimentId);
        assertFalse(experiments.get(0).isArchived);

        // Test adding a few experiments
        int count = 10;
        // Start at 1, we already added one.
        for (int index = 1; index < count; index++) {
            mMetaDataManager.newDatabaseExperiment();
        }

        experiments = mMetaDataManager.getDatabaseExperimentOverviews(false);
        assertEquals(count, experiments.size());
    }

    @Test
    public void testNewLabel() {
        Experiment experiment = mMetaDataManager.newExperiment();

        String testLabelString = "test label";
        GoosciTextLabelValue.TextLabelValue textLabelValue =
                new GoosciTextLabelValue.TextLabelValue();
        textLabelValue.text = testLabelString;

        Label textLabel = Label.newLabelWithValue(1, GoosciLabel.Label.TEXT, textLabelValue, null);
        experiment.addLabel(textLabel);
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

        GoosciCaption.Caption caption = new GoosciCaption.Caption();
        caption.text = testPictureCaption;

        GoosciPictureLabelValue.PictureLabelValue labelValue = new GoosciPictureLabelValue
                .PictureLabelValue();
        labelValue.filePath = testPicturePath;

        Label pictureLabel = Label.newLabelWithValue(2, GoosciLabel.Label.PICTURE, labelValue,
                caption);
        experiment.addLabel(pictureLabel);

        mMetaDataManager.updateExperiment(experiment);

        List<Label> labels = mMetaDataManager.getExperimentById(experiment.getExperimentId())
                                             .getLabels();
        assertEquals(2, labels.size());

        boolean foundText = false;
        boolean foundPicture = false;
        for (Label foundLabel : labels) {
            if (foundLabel.getType() == GoosciLabel.Label.TEXT) {
                assertEquals(testLabelString, foundLabel.getTextLabelValue().text);
                assertEquals(textLabel.getLabelId(), foundLabel.getLabelId());
                foundText = true;
            }
            if (foundLabel.getType() == GoosciLabel.Label.PICTURE) {
                assertEquals(testPicturePath, foundLabel.getPictureLabelValue().filePath);
                assertEquals(pictureLabel.getLabelId(), foundLabel.getLabelId());
                assertEquals(testPictureCaption, foundLabel.getCaptionText());
                foundPicture = true;
            }
            assertTrue(foundLabel.getTimeStamp() > 0);
        }
        assertTrue("Text label was not saved.", foundText);
        assertTrue("Picture label was not saved.", foundPicture);
    }

    @Test
    public void testExperimentStartIds() {
        Experiment experiment = mMetaDataManager.newDatabaseExperiment();
        ApplicationLabel startId1 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START,
                "startId1", "startId1", 0);
        ApplicationLabel stopId1 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId1", "startId1", 1);
        final ApplicationLabel startId2 = new ApplicationLabel(
                ApplicationLabel.TYPE_RECORDING_START, "startId2", "startId2", 2);
        final Label label = Label.newLabel(3, GoosciLabel.Label.TEXT);
        GoosciTextLabelValue.TextLabelValue labelValue = new GoosciTextLabelValue.TextLabelValue();
        labelValue.text = "text";
        label.setLabelProtoData(labelValue);
        ApplicationLabel stopId2 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId2", "startId2", 4);
        final ApplicationLabel startId3 = new ApplicationLabel(
                ApplicationLabel.TYPE_RECORDING_START, "startId3", "startId3", 5);
        final ApplicationLabel stopId3 = new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP,
                "stopId3", "startId3", 6);
        String experimentId = experiment.getExperimentId();
        mMetaDataManager.addDatabaseApplicationLabel(experimentId, startId1);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addDatabaseApplicationLabel(experimentId, stopId1);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addDatabaseApplicationLabel(experimentId, startId2);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addDatabaseApplicationLabel(experimentId, stopId2);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addDatabaseApplicationLabel(experimentId, startId3);
        mTestSystemClock.advanceClock();
        mMetaDataManager.addDatabaseApplicationLabel(experimentId, stopId3);
        mTestSystemClock.advanceClock();
        final List<String> experimentRunIds = mMetaDataManager.getDatabaseExperimentRunIds(
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
        final List<String> experimentRunIds2 = mMetaDataManager.getDatabaseExperimentRunIds(
                experiment.getExperimentId(), true);
        assertEquals(Lists.newArrayList("startId3", "startId2", "startId1"), experimentRunIds2);
    }

    @Test
    public void testAddRemoveExternalSensor() {
        Map<String, SensorProvider> providerMap = getProviderMap();
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

    private Map<String, SensorProvider> getProviderMap() {
        HashMap<String, SensorProvider> map = new HashMap<>();
        map.put(BleSensorSpec.TYPE, new NativeBleDiscoverer(getContext()).getProvider());
        map.put(ScalarInputSpec.TYPE, new ScalarInputDiscoverer(null, null,
                WhistlePunkApplication.getUsageTracker(null)).getProvider());
        return map;
    }

    @Test
    public void testSensorsDifferentTypes() {
        Map<String, SensorProvider> providerMap = null;
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

    @Test
    public void testGetExternalSensorsWithScalarInput() {
        Map<String, SensorProvider> providerMap = getProviderMap();
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
        assertEquals("address", ((ScalarInputSpec) spec).getSensorAddressInService());
    }

    @Test
    public void testSensorToExperiment() {
        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        Map<String, SensorProvider> providerMap = getProviderMap();
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        Experiment experiment = mMetaDataManager.newDatabaseExperiment();

        mMetaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

        ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providerMap);
        Map<String, ExternalSensorSpec> sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentSensors(experiment.getExperimentId(),
                        providerMap, connector));
        assertEquals(1, sensors.size());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.removeSensorFromExperiment(databaseTag, experiment.getExperimentId());

        sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentSensors(experiment.getExperimentId(),
                        providerMap, connector));
        assertEquals(0, sensors.size());
    }

    @Test
    public void testRemoveExternalSensorWithExperiment() {
        String testAddress = "11:22:33:44:55";
        String testName = "testName";
        BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
        Map<String, SensorProvider> providerMap = getProviderMap();
        ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providerMap);
        String databaseTag = mMetaDataManager.addOrGetExternalSensor(sensor, providerMap);

        Experiment experiment = mMetaDataManager.newDatabaseExperiment();

        mMetaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

        Map<String, ExternalSensorSpec> sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentSensors(experiment.getExperimentId(),
                        providerMap, connector));
        assertEquals(1, sensors.size());
        assertEquals(databaseTag, sensors.keySet().iterator().next());

        mMetaDataManager.removeExternalSensor(databaseTag);

        // This should be gone now.
        sensors = ConnectableSensor.makeMap(
                mMetaDataManager.getExperimentSensors(experiment.getExperimentId(),
                        providerMap, connector));
        assertEquals(0, sensors.size());
    }

    @Test
    public void testRunStorage() {
        Experiment experiment = mMetaDataManager.newDatabaseExperiment();
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
        Trial loaded = mMetaDataManager.getDatabaseTrial(startLabel.getLabelId(),
                Arrays.asList(startLabel));
        assertEquals(startLabel.getLabelId(), saved.getTrialId());
        assertEquals(startLabel.getLabelId(), loaded.getTrialId());
        assertEquals(sensorIds, saved.getSensorIds());
        assertEquals(sensorIds, loaded.getSensorIds());
        assertEquals(5, saved.getSensorLayouts().get(0).maximumYAxisValue, 0.1);
        assertEquals(5, loaded.getSensorLayouts().get(0).maximumYAxisValue, 0.1);

        // Test that runs are deleted.
        mMetaDataManager.deleteDatabaseTrial(startLabel.getLabelId());
        loaded = mMetaDataManager.getDatabaseTrial(startLabel.getLabelId(),
                Arrays.asList(startLabel));
        assertNull(loaded);
    }

    @Test
    public void testDatabaseExperimentDelete() {
        Experiment experiment = mMetaDataManager.newDatabaseExperiment();
        final ApplicationLabel startLabel = newStartLabel("startId", 1);
        GoosciSensorLayout.SensorLayout layout1 = new GoosciSensorLayout.SensorLayout();
        layout1.sensorId = "sensor1";
        GoosciSensorLayout.SensorLayout layout2 = new GoosciSensorLayout.SensorLayout();
        layout2.sensorId = "sensor2";
        final ArrayList<GoosciSensorLayout.SensorLayout> sensorLayouts =
                Lists.newArrayList(layout1, layout2);
        mMetaDataManager.addDatabaseApplicationLabel(experiment.getExperimentId(), startLabel);
        mMetaDataManager.newTrial(experiment, startLabel.getTrialId(), startLabel.getTimeStamp(),
                sensorLayouts);
        mMetaDataManager.setExperimentSensorLayouts(experiment.getExperimentId(), sensorLayouts);

        mMetaDataManager.deleteDatabaseExperiment(experiment);

        assertNull(mMetaDataManager.getDatabaseExperimentById(experiment.getExperimentId()));
        // Test that runs are deleted when deleting experiments.
        assertNull(mMetaDataManager.getDatabaseTrial(startLabel.getLabelId(),
                Arrays.asList(startLabel)));
        // Test that sensor layouts are gone.
        assertEquals(0, mMetaDataManager.getDatabaseExperimentSensorLayouts(
                experiment.getExperimentId()).size());
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
                getIds(mMetaDataManager.getDatabaseExperimentSensorLayouts(experimentId)));

        GoosciSensorLayout.SensorLayout layout3 = new GoosciSensorLayout.SensorLayout();
        layout3.sensorId = "sensorId3";
        mMetaDataManager.setExperimentSensorLayouts(experimentId, Lists.newArrayList(layout3));

        assertEquals(Lists.newArrayList(layout3.sensorId),
                getIds(mMetaDataManager.getDatabaseExperimentSensorLayouts(experimentId)));

        // Try storing duplicate
        mMetaDataManager.setExperimentSensorLayouts(experimentId, Lists.newArrayList(layout3,
                layout3));
        // duplicates are removed
        assertEquals(Lists.newArrayList(layout3.sensorId),
                getIds(mMetaDataManager.getDatabaseExperimentSensorLayouts(experimentId)));
    }

    @Test
    public void testExternalSensorOrder() {
        Map<String, SensorProvider> providerMap = getProviderMap();
        ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providerMap);
        String id1 = mMetaDataManager.addOrGetExternalSensor(new BleSensorSpec("address1", "name1"),
                providerMap);
        String id2 = mMetaDataManager.addOrGetExternalSensor(new BleSensorSpec("address2", "name2"),
                providerMap);
        String id3 = mMetaDataManager.addOrGetExternalSensor(new BleSensorSpec("address3", "name3"),
                providerMap);
        mMetaDataManager.addSensorToExperiment(id1, "experimentId");
        mMetaDataManager.addSensorToExperiment(id2, "experimentId");
        mMetaDataManager.addSensorToExperiment(id3, "experimentId");
        List<ConnectableSensor> sensors = mMetaDataManager.getExperimentSensors(
                "experimentId", providerMap, connector).getIncludedSensors();
        assertEquals(id1, sensors.get(0).getConnectedSensorId());
        assertEquals(id2, sensors.get(1).getConnectedSensorId());
        assertEquals(id3, sensors.get(2).getConnectedSensorId());
    }

    @Test
    public void testExternalSensorDuplication() {
        Map<String, SensorProvider> providerMap = getProviderMap();
        ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providerMap);
        String id1 = mMetaDataManager.addOrGetExternalSensor(new BleSensorSpec("address1", "name1"),
                providerMap);
        mMetaDataManager.addSensorToExperiment(id1, "experimentId");
        mMetaDataManager.addSensorToExperiment(id1, "experimentId");
        List<ConnectableSensor> sensors = mMetaDataManager.getExperimentSensors(
                "experimentId", providerMap, connector).getIncludedSensors();
        assertEquals(1, sensors.size());
    }

    @Test
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

        assertSameSensor(device1, mMetaDataManager.getMyDevices().get(0));
        assertSameSensor(device2, mMetaDataManager.getMyDevices().get(1));

        mMetaDataManager.removeMyDevice(device1);
        assertEquals(1, mMetaDataManager.getMyDevices().size());

        mMetaDataManager.removeMyDevice(device2);
        assertEquals(0, mMetaDataManager.getMyDevices().size());
    }

    private void assertSameSensor(InputDeviceSpec expected, InputDeviceSpec actual) {
        assertTrue(expected + " != " + actual, expected.isSameSensor(actual));
    }

    @Test
    public void testNewProject() {
        Project project = mMetaDataManager.newProject();
        assertNotNull(project);
        assertFalse(TextUtils.isEmpty(project.getProjectId()));
        assertNotSame(project.getId(), -1);

        List<Project> projects = mMetaDataManager.getDatabaseProjects(false);
        boolean found = false;
        for (Project retrievedProject : projects) {
            if (retrievedProject.getProjectId().equals(project.getProjectId())) {
                found = true;
                break;
            }
        }
        assertTrue("New project not found in list of retrieved projects", found);
    }

    @Test
    public void testUpgradeProjects() {
        // Nothing in this project, so no updates should be made to firstExp.
        Project first = mMetaDataManager.newProject();
        Experiment firstExp = mMetaDataManager.newDatabaseExperiment(first.getProjectId());

        // The title, cover photo and description should be translated into objects in the secondExp
        Project second = mMetaDataManager.newProject();
        second.setTitle("Title");
        second.setCoverPhoto("path/to/photo");
        second.setDescription("Description");
        second.setArchived(true);
        mMetaDataManager.updateProject(second);
        Experiment secondExp = mMetaDataManager.newDatabaseExperiment(second.getProjectId());

        assertEquals(2, mMetaDataManager.getDatabaseProjects(true).size());

        mMetaDataManager.migrateProjectData();
        assertEquals(0, mMetaDataManager.getDatabaseProjects(true).size());

        Experiment firstExpResult = mMetaDataManager.getDatabaseExperimentById(
                firstExp.getExperimentId());
        assertEquals(0, firstExpResult.getLabels().size());
        assertEquals(1, firstExpResult.getVersion());
        assertTrue(TextUtils.isEmpty(firstExp.getTitle()));

        Experiment secondExpResult = mMetaDataManager.getDatabaseExperimentById(
                secondExp.getExperimentId());
        List<Label> labels = secondExpResult.getLabels();
        assertEquals(2, labels.size());
        assertEquals("Description", labels.get(0).getTextLabelValue().text);
        assertEquals("path/to/photo", labels.get(1).getPictureLabelValue().filePath);
        assertTrue(labels.get(0).getTimeStamp() < secondExpResult.getCreationTimeMs());
        assertTrue(labels.get(1).getTimeStamp() < secondExpResult.getCreationTimeMs());
        assertTrue(secondExpResult.getDisplayTitle(getContext()).startsWith(
                "Title"));
        assertTrue(secondExpResult.isArchived());
    }

    @Test
    public void testMyDevicesMigrate() {
        mMetaDataManager.databaseAddMyDevice(new InputDeviceSpec("provider", "address", "name"));
        assertEquals(0, mMetaDataManager.fileGetMyDevices().size());
        assertEquals(1, mMetaDataManager.databaseGetMyDevices().size());
        mMetaDataManager.migrateMyDevices();

        // none left in database
        assertEquals(0, mMetaDataManager.databaseGetMyDevices().size());

        List<GoosciDeviceSpec.DeviceSpec> myDevices = mMetaDataManager.fileGetMyDevices();
        assertEquals(1, myDevices.size());
        assertEquals("name", myDevices.get(0).name);
    }

    @Test
    public void testGetInternalSensors() {
        mMetaDataManager.addSensorToExperiment("internalTag", "experimentId");
        HashMap<String, SensorProvider> providerMap = new HashMap<>();
        ExperimentSensors sensors =
                mMetaDataManager.getExperimentSensors("experimentId", providerMap,
                        new ConnectableSensor.Connector(providerMap));
        List<ConnectableSensor> included = sensors.getIncludedSensors();
        Observable.fromIterable(included)
                  .test()
                  .assertValue(
                          cs -> cs.isBuiltIn() && cs.getConnectedSensorId().equals("internalTag"));
    }

    @Test
    public void testMigrateExperimentsToFiles() {
        Experiment experiment = mMetaDataManager.newDatabaseExperiment();
        for (int i = 0; i < 50; i++) {
            GoosciPictureLabelValue.PictureLabelValue labelValue = new GoosciPictureLabelValue
                    .PictureLabelValue();
            labelValue.filePath = "fake/path";
            Label label = Label.newLabelWithValue(i * 1000, GoosciLabelValue.LabelValue.PICTURE,
                    labelValue, null);
            LabelValue deprecatedValue = PictureLabelValue.fromPicture(labelValue.filePath, "");
            // In the database, labels are stored separately. Add them directly here so that we
            // don't need to re-create methods to update them from the SimpleMetadataManager.
            mMetaDataManager.addDatabaseLabel(experiment.getExperimentId(),
                    RecorderController.NOT_RECORDING_RUN_ID, label, deprecatedValue);
        }
        mMetaDataManager.migrateExperimentsToFiles();
        assertEquals(50,
                mMetaDataManager.getExperimentById(experiment.getExperimentId()).getLabelCount());
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

    private Context getContext() {
        return RuntimeEnvironment.application.getApplicationContext();
    }
}
