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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus.LocalSyncStatus;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.NativeBleDiscoverer;
import com.google.android.apps.forscience.whistlepunk.filemetadata.DeviceSpecPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentOverviewPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LocalSyncManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue.TextLabelValue;
import com.google.common.collect.Lists;
import io.reactivex.Observable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for {@link SimpleMetaDataManager} */
@RunWith(RobolectricTestRunner.class)
public class SimpleMetaDataManagerTest {

  private SimpleMetaDataManager metaDataManager;
  private TestSystemClock testSystemClock;

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
    metaDataManager = makeMetaDataManager();
    LocalSyncManager lsm =
        AppSingleton.getInstance(getContext()).getLocalSyncManager(getAppAccount());
    lsm.setLocalSyncStatus(LocalSyncStatus.getDefaultInstance());
    ExperimentLibraryManager elm =
        AppSingleton.getInstance(getContext()).getExperimentLibraryManager(getAppAccount());
    elm.setLibrary(ExperimentLibrary.getDefaultInstance());
  }

  @NonNull
  private SimpleMetaDataManager makeMetaDataManager() {
    testSystemClock = new TestSystemClock();
    return new SimpleMetaDataManager(
        getContext(), getAppAccount(), "test.main.db", testSystemClock);
  }

  @After
  public void tearDown() {
    getContext().getDatabasePath("test.main.db").delete();
    File sharedMetadataFile = FileMetadataUtil.getInstance().getUserMetadataFile(getAppAccount());
    sharedMetadataFile.delete();
  }

  @Test
  public void testNewExperiment() {
    Experiment experiment = metaDataManager.newDatabaseExperiment();
    assertNotNull(experiment);
    assertFalse(TextUtils.isEmpty(experiment.getExperimentId()));
    assertTrue(experiment.getCreationTimeMs() > 0);

    List<ExperimentOverviewPojo> experiments =
        metaDataManager.getDatabaseExperimentOverviews(false);
    assertEquals(1, experiments.size());
    assertEquals(experiment.getExperimentId(), experiments.get(0).getExperimentId());
    assertFalse(experiments.get(0).isArchived());

    // Test adding a few experiments
    int count = 10;
    // Start at 1, we already added one.
    for (int index = 1; index < count; index++) {
      metaDataManager.newDatabaseExperiment();
    }

    experiments = metaDataManager.getDatabaseExperimentOverviews(false);
    assertEquals(count, experiments.size());
  }

  @Test
  public void testNewLabel() {
    Experiment experiment = metaDataManager.newExperiment();

    String testLabelString = "test label";
    TextLabelValue textLabelValue =
        GoosciTextLabelValue.TextLabelValue.newBuilder().setText(testLabelString).build();

    Label textLabel = Label.newLabelWithValue(1, ValueType.TEXT, textLabelValue, null);
    experiment.addLabel(experiment, textLabel);
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

    Caption caption = GoosciCaption.Caption.newBuilder().setText(testPictureCaption).build();

    GoosciPictureLabelValue.PictureLabelValue labelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath(testPicturePath).build();

    Label pictureLabel = Label.newLabelWithValue(2, ValueType.PICTURE, labelValue, caption);
    experiment.addLabel(experiment, pictureLabel);

    metaDataManager.updateExperiment(experiment, true);

    List<Label> labels =
        metaDataManager.getExperimentById(experiment.getExperimentId()).getLabels();
    assertEquals(2, labels.size());

    boolean foundText = false;
    boolean foundPicture = false;
    for (Label foundLabel : labels) {
      if (foundLabel.getType() == ValueType.TEXT) {
        assertEquals(testLabelString, foundLabel.getTextLabelValue().getText());
        assertEquals(textLabel.getLabelId(), foundLabel.getLabelId());
        foundText = true;
      }
      if (foundLabel.getType() == ValueType.PICTURE) {
        assertEquals(testPicturePath, foundLabel.getPictureLabelValue().getFilePath());
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
    Experiment experiment = metaDataManager.newDatabaseExperiment();
    ApplicationLabel startId1 =
        new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "startId1", "startId1", 0);
    ApplicationLabel stopId1 =
        new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "stopId1", "startId1", 1);
    final ApplicationLabel startId2 =
        new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "startId2", "startId2", 2);
    final Label label = Label.newLabel(3, ValueType.TEXT);
    TextLabelValue labelValue =
        GoosciTextLabelValue.TextLabelValue.newBuilder().setText("text").build();
    label.setLabelProtoData(labelValue);
    ApplicationLabel stopId2 =
        new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "stopId2", "startId2", 4);
    final ApplicationLabel startId3 =
        new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "startId3", "startId3", 5);
    final ApplicationLabel stopId3 =
        new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "stopId3", "startId3", 6);
    String experimentId = experiment.getExperimentId();
    metaDataManager.addDatabaseApplicationLabel(experimentId, startId1);
    testSystemClock.advanceClock();
    metaDataManager.addDatabaseApplicationLabel(experimentId, stopId1);
    testSystemClock.advanceClock();
    metaDataManager.addDatabaseApplicationLabel(experimentId, startId2);
    testSystemClock.advanceClock();
    metaDataManager.addDatabaseApplicationLabel(experimentId, stopId2);
    testSystemClock.advanceClock();
    metaDataManager.addDatabaseApplicationLabel(experimentId, startId3);
    testSystemClock.advanceClock();
    metaDataManager.addDatabaseApplicationLabel(experimentId, stopId3);
    testSystemClock.advanceClock();
    final List<String> experimentRunIds =
        metaDataManager.getDatabaseExperimentRunIds(experiment.getExperimentId(), true);
    assertEquals(Lists.newArrayList(), experimentRunIds);
    metaDataManager.newTrial(
        experiment, startId1.getTrialId(), 0, new ArrayList<GoosciSensorLayout.SensorLayout>());
    testSystemClock.advanceClock();
    metaDataManager.newTrial(
        experiment, startId2.getTrialId(), 2, new ArrayList<GoosciSensorLayout.SensorLayout>());
    testSystemClock.advanceClock();
    metaDataManager.newTrial(
        experiment, startId3.getTrialId(), 5, new ArrayList<GoosciSensorLayout.SensorLayout>());
    testSystemClock.advanceClock();
    final List<String> experimentRunIds2 =
        metaDataManager.getDatabaseExperimentRunIds(experiment.getExperimentId(), true);
    assertEquals(Lists.newArrayList("startId3", "startId2", "startId1"), experimentRunIds2);
  }

  @Test
  public void testAddRemoveExternalSensor() {
    Map<String, SensorProvider> providerMap = getProviderMap();
    Map<String, ExternalSensorSpec> sensors = metaDataManager.getExternalSensors(providerMap);
    assertEquals(0, sensors.size());

    String testAddress = "11:22:33:44:55";
    String testName = "testName";
    BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
    String databaseTag = metaDataManager.addOrGetExternalSensor(sensor, providerMap);

    sensors = metaDataManager.getExternalSensors(providerMap);

    assertEquals(1, sensors.size());
    ExternalSensorSpec spec = sensors.values().iterator().next();
    assertTrue(spec instanceof BleSensorSpec);
    BleSensorSpec dbSensor = (BleSensorSpec) spec;
    assertEquals(testAddress, dbSensor.getAddress());
    assertEquals(testName, dbSensor.getName());
    assertEquals(databaseTag, sensors.keySet().iterator().next());

    metaDataManager.removeExternalSensor(databaseTag);

    sensors = metaDataManager.getExternalSensors(providerMap);
    assertEquals(0, sensors.size());
  }

  private Map<String, SensorProvider> getProviderMap() {
    HashMap<String, SensorProvider> map = new HashMap<>();
    map.put(BleSensorSpec.TYPE, new NativeBleDiscoverer(getContext()).getProvider());
    map.put(
        ScalarInputSpec.TYPE,
        new ScalarInputDiscoverer(null, null, WhistlePunkApplication.getUsageTracker(null))
            .getProvider());
    return map;
  }

  @Test
  public void testSensorsDifferentTypes() {
    Map<String, SensorProvider> providerMap = null;
    Map<String, ExternalSensorSpec> sensors = metaDataManager.getExternalSensors(providerMap);
    assertEquals(0, sensors.size());

    final String testAddress = "11:22:33:44:55";
    final String testName = "testName";
    final BleSensorSpec bleSpec = new BleSensorSpec(testAddress, testName);
    ExternalSensorSpec wackedSpec =
        new ExternalSensorSpec() {
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

    String bleId = metaDataManager.addOrGetExternalSensor(bleSpec, providerMap);
    String wackedId = metaDataManager.addOrGetExternalSensor(wackedSpec, providerMap);

    assertEquals("bluetooth_le-11:22:33:44:55-testName-0", bleId);
    assertEquals("wacked-11:22:33:44:55-testName-0", wackedId);
  }

  @Test
  public void testGetExternalSensorsWithScalarInput() {
    Map<String, SensorProvider> providerMap = getProviderMap();
    assertEquals(0, metaDataManager.getExternalSensors(providerMap).size());
    metaDataManager.addOrGetExternalSensor(
        new ScalarInputSpec("name", "serviceId", "address", null, null, "deviceId"), providerMap);
    Map<String, ExternalSensorSpec> newSensors = metaDataManager.getExternalSensors(providerMap);
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
    String databaseTag = metaDataManager.addOrGetExternalSensor(sensor, providerMap);

    Experiment experiment = metaDataManager.newDatabaseExperiment();

    metaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providerMap);
    Map<String, ExternalSensorSpec> sensors =
        ConnectableSensor.makeMap(
            metaDataManager.getExperimentSensors(
                experiment.getExperimentId(), providerMap, connector));
    assertEquals(1, sensors.size());
    assertEquals(databaseTag, sensors.keySet().iterator().next());

    metaDataManager.eraseSensorFromExperiment(databaseTag, experiment.getExperimentId());

    sensors =
        ConnectableSensor.makeMap(
            metaDataManager.getExperimentSensors(
                experiment.getExperimentId(), providerMap, connector));
    assertEquals(0, sensors.size());
  }

  @Test
  public void testRemoveExternalSensorWithExperiment() {
    String testAddress = "11:22:33:44:55";
    String testName = "testName";
    BleSensorSpec sensor = new BleSensorSpec(testAddress, testName);
    Map<String, SensorProvider> providerMap = getProviderMap();
    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providerMap);
    String databaseTag = metaDataManager.addOrGetExternalSensor(sensor, providerMap);

    Experiment experiment = metaDataManager.newDatabaseExperiment();

    metaDataManager.addSensorToExperiment(databaseTag, experiment.getExperimentId());

    Map<String, ExternalSensorSpec> sensors =
        ConnectableSensor.makeMap(
            metaDataManager.getExperimentSensors(
                experiment.getExperimentId(), providerMap, connector));
    assertEquals(1, sensors.size());
    assertEquals(databaseTag, sensors.keySet().iterator().next());

    metaDataManager.removeExternalSensor(databaseTag);

    // This should be gone now.
    sensors =
        ConnectableSensor.makeMap(
            metaDataManager.getExperimentSensors(
                experiment.getExperimentId(), providerMap, connector));
    assertEquals(0, sensors.size());
  }

  @Test
  public void testRunStorage() {
    Experiment experiment = metaDataManager.newDatabaseExperiment();
    final ApplicationLabel startLabel = newStartLabel("startId", 1);
    SensorLayout layout1 =
        GoosciSensorLayout.SensorLayout.newBuilder()
            .setSensorId("sensor1")
            .setMaximumYAxisValue(5)
            .build();
    SensorLayout layout2 =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("sensor2").build();
    final ArrayList<GoosciSensorLayout.SensorLayout> sensorLayouts =
        Lists.newArrayList(layout1, layout2);
    final ArrayList<String> sensorIds = Lists.newArrayList("sensor1", "sensor2");
    Trial saved =
        metaDataManager.newTrial(
            experiment, startLabel.getTrialId(), startLabel.getTimeStamp(), sensorLayouts);
    Trial loaded =
        metaDataManager.getDatabaseTrial(startLabel.getLabelId(), Arrays.asList(startLabel));
    assertEquals(startLabel.getLabelId(), saved.getTrialId());
    assertEquals(startLabel.getLabelId(), loaded.getTrialId());
    assertEquals(sensorIds, saved.getSensorIds());
    assertEquals(sensorIds, loaded.getSensorIds());
    assertEquals(5, saved.getSensorLayouts().get(0).getMaximumYAxisValue(), 0.1);
    assertEquals(5, loaded.getSensorLayouts().get(0).getMaximumYAxisValue(), 0.1);

    // Test that runs are deleted.
    metaDataManager.deleteDatabaseTrial(startLabel.getLabelId());
    loaded = metaDataManager.getDatabaseTrial(startLabel.getLabelId(), Arrays.asList(startLabel));
    assertNull(loaded);
  }

  @Test
  public void testDatabaseExperimentDelete() {
    Experiment experiment = metaDataManager.newDatabaseExperiment();
    final ApplicationLabel startLabel = newStartLabel("startId", 1);
    SensorLayout layout1 =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("sensor1").build();
    SensorLayout layout2 =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("sensor2").build();
    final ArrayList<GoosciSensorLayout.SensorLayout> sensorLayouts =
        Lists.newArrayList(layout1, layout2);
    metaDataManager.addDatabaseApplicationLabel(experiment.getExperimentId(), startLabel);
    metaDataManager.newTrial(
        experiment, startLabel.getTrialId(), startLabel.getTimeStamp(), sensorLayouts);
    metaDataManager.setExperimentSensorLayouts(experiment.getExperimentId(), sensorLayouts);

    metaDataManager.deleteDatabaseExperiment(experiment);

    assertNull(metaDataManager.getDatabaseExperimentById(experiment.getExperimentId()));
    // Test that runs are deleted when deleting experiments.
    assertNull(
        metaDataManager.getDatabaseTrial(startLabel.getLabelId(), Arrays.asList(startLabel)));
    // Test that sensor layouts are gone.
    assertEquals(
        0, metaDataManager.getDatabaseExperimentSensorLayouts(experiment.getExperimentId()).size());
  }

  @Test
  public void testExperimentSensorLayout() {
    GoosciSensorLayout.SensorLayout layout1 =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("sensorId1").build();
    GoosciSensorLayout.SensorLayout layout2 =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("sensorId2").build();
    String experimentId = Arbitrary.string();
    metaDataManager.setExperimentSensorLayouts(experimentId, Lists.newArrayList(layout1, layout2));

    assertEquals(
        Lists.newArrayList(layout1.getSensorId(), layout2.getSensorId()),
        getIds(metaDataManager.getDatabaseExperimentSensorLayouts(experimentId)));

    GoosciSensorLayout.SensorLayout layout3 =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("sensorId3").build();
    metaDataManager.setExperimentSensorLayouts(experimentId, Lists.newArrayList(layout3));

    assertEquals(
        Lists.newArrayList(layout3.getSensorId()),
        getIds(metaDataManager.getDatabaseExperimentSensorLayouts(experimentId)));

    // Try storing duplicate
    metaDataManager.setExperimentSensorLayouts(experimentId, Lists.newArrayList(layout3, layout3));
    // duplicates are removed
    assertEquals(
        Lists.newArrayList(layout3.getSensorId()),
        getIds(metaDataManager.getDatabaseExperimentSensorLayouts(experimentId)));
  }

  @Test
  public void testExternalSensorOrder() {
    Map<String, SensorProvider> providerMap = getProviderMap();
    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providerMap);
    String id1 =
        metaDataManager.addOrGetExternalSensor(new BleSensorSpec("address1", "name1"), providerMap);
    String id2 =
        metaDataManager.addOrGetExternalSensor(new BleSensorSpec("address2", "name2"), providerMap);
    String id3 =
        metaDataManager.addOrGetExternalSensor(new BleSensorSpec("address3", "name3"), providerMap);
    metaDataManager.addSensorToExperiment(id1, "experimentId");
    metaDataManager.addSensorToExperiment(id2, "experimentId");
    metaDataManager.addSensorToExperiment(id3, "experimentId");
    List<ConnectableSensor> sensors =
        metaDataManager
            .getExperimentSensors("experimentId", providerMap, connector)
            .getExternalSensors();
    assertEquals(id1, sensors.get(0).getConnectedSensorId());
    assertEquals(id2, sensors.get(1).getConnectedSensorId());
    assertEquals(id3, sensors.get(2).getConnectedSensorId());
  }

  @Test
  public void testExternalSensorDuplication() {
    Map<String, SensorProvider> providerMap = getProviderMap();
    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providerMap);
    String id1 =
        metaDataManager.addOrGetExternalSensor(new BleSensorSpec("address1", "name1"), providerMap);
    metaDataManager.addSensorToExperiment(id1, "experimentId");
    metaDataManager.addSensorToExperiment(id1, "experimentId");
    List<ConnectableSensor> sensors =
        metaDataManager
            .getExperimentSensors("experimentId", providerMap, connector)
            .getExternalSensors();
    assertEquals(1, sensors.size());
  }

  @Test
  public void testMyDevices() {
    assertEquals(0, metaDataManager.getMyDevices().size());
    InputDeviceSpec device1 =
        new InputDeviceSpec(ScalarInputSpec.TYPE, "deviceAddress1", "deviceName1");
    InputDeviceSpec device2 =
        new InputDeviceSpec(ScalarInputSpec.TYPE, "deviceAddress2", "deviceName2");

    metaDataManager.addMyDevice(device1);
    assertEquals(1, metaDataManager.getMyDevices().size());

    metaDataManager.addMyDevice(device1);
    assertEquals(1, metaDataManager.getMyDevices().size());

    metaDataManager.addMyDevice(device2);
    assertEquals(2, metaDataManager.getMyDevices().size());

    assertSameSensor(device1, metaDataManager.getMyDevices().get(0));
    assertSameSensor(device2, metaDataManager.getMyDevices().get(1));

    metaDataManager.removeMyDevice(device1);
    assertEquals(1, metaDataManager.getMyDevices().size());

    metaDataManager.removeMyDevice(device2);
    assertEquals(0, metaDataManager.getMyDevices().size());
  }

  private void assertSameSensor(InputDeviceSpec expected, InputDeviceSpec actual) {
    assertTrue(expected + " != " + actual, expected.isSameSensor(actual));
  }

  @Test
  public void testNewProject() {
    Project project = metaDataManager.newProject();
    assertNotNull(project);
    assertFalse(TextUtils.isEmpty(project.getProjectId()));
    assertNotSame(project.getId(), -1);

    List<Project> projects = metaDataManager.getDatabaseProjects(false);
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
    Project first = metaDataManager.newProject();
    Experiment firstExp = metaDataManager.newDatabaseExperiment(first.getProjectId());

    // The title, cover photo and description should be translated into objects in the secondExp
    Project second = metaDataManager.newProject();
    second.setTitle("Title");
    second.setCoverPhoto("path/to/photo");
    second.setDescription("Description");
    second.setArchived(true);
    metaDataManager.updateProject(second);
    Experiment secondExp = metaDataManager.newDatabaseExperiment(second.getProjectId());

    assertEquals(2, metaDataManager.getDatabaseProjects(true).size());

    metaDataManager.migrateProjectData();
    assertEquals(0, metaDataManager.getDatabaseProjects(true).size());

    Experiment firstExpResult =
        metaDataManager.getDatabaseExperimentById(firstExp.getExperimentId());
    assertEquals(0, firstExpResult.getLabels().size());
    assertEquals(1, firstExpResult.getExperimentProto().getVersion());
    assertTrue(TextUtils.isEmpty(firstExp.getTitle()));

    Experiment secondExpResult =
        metaDataManager.getDatabaseExperimentById(secondExp.getExperimentId());
    List<Label> labels = secondExpResult.getLabels();
    assertEquals(2, labels.size());
    assertEquals("Description", labels.get(0).getTextLabelValue().getText());
    assertEquals("path/to/photo", labels.get(1).getPictureLabelValue().getFilePath());
    assertTrue(labels.get(0).getTimeStamp() < secondExpResult.getCreationTimeMs());
    assertTrue(labels.get(1).getTimeStamp() < secondExpResult.getCreationTimeMs());
    assertTrue(secondExpResult.getDisplayTitle(getContext()).startsWith("Title"));
    assertTrue(secondExpResult.isArchived());
  }

  @Test
  public void testMyDevicesMigrate() {
    metaDataManager.databaseAddMyDevice(new InputDeviceSpec("provider", "address", "name"));
    assertEquals(0, metaDataManager.fileGetMyDevices().size());
    assertEquals(1, metaDataManager.databaseGetMyDevices().size());
    metaDataManager.migrateMyDevices();

    // none left in database
    assertEquals(0, metaDataManager.databaseGetMyDevices().size());

    List<DeviceSpecPojo> myDevices = metaDataManager.fileGetMyDevices();
    assertEquals(1, myDevices.size());
    assertEquals("name", myDevices.get(0).getName());
  }

  @Test
  public void testGetInternalSensors() {
    metaDataManager.removeSensorFromExperiment("internalTag", "experimentId");
    HashMap<String, SensorProvider> providerMap = new HashMap<>();
    ExperimentSensors sensors =
        metaDataManager.getExperimentSensors(
            "experimentId", providerMap, new ConnectableSensor.Connector(providerMap));
    Set<String> excluded = sensors.getExcludedInternalSensorIds();
    Observable.fromIterable(excluded).test().assertValue(id -> id.equals("internalTag"));
  }

  @Test
  public void testMigrateExperimentsToFiles() {
    Experiment experiment = metaDataManager.newDatabaseExperiment();
    for (int i = 0; i < 50; i++) {
      GoosciPictureLabelValue.PictureLabelValue labelValue =
          GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("fake/path").build();
      Label label = Label.newLabelWithValue(i * 1000, ValueType.PICTURE, labelValue, null);
      LabelValue deprecatedValue = PictureLabelValue.fromPicture(labelValue.getFilePath(), "");
      // In the database, labels are stored separately. Add them directly here so that we
      // don't need to re-create methods to update them from the SimpleMetadataManager.
      metaDataManager.addDatabaseLabel(
          experiment.getExperimentId(),
          RecorderController.NOT_RECORDING_RUN_ID,
          label,
          deprecatedValue);
    }
    metaDataManager.migrateExperimentsToFiles();
    assertEquals(
        50, metaDataManager.getExperimentById(experiment.getExperimentId()).getLabelCount());
  }

  private static List<String> getIds(List<SensorLayoutPojo> layouts) {
    List<String> ids = new ArrayList<>();
    for (SensorLayoutPojo layout : layouts) {
      ids.add(layout.getSensorId());
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

  private AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }
}
