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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import com.bignerdranch.expandablerecyclerview.Adapter.ExpandableRecyclerAdapter;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ExpandableDeviceAdapterTest {
  private static final InputDeviceSpec BUILT_IN_DEVICE =
      new InputDeviceSpec(
          InputDeviceSpec.TYPE, InputDeviceSpec.BUILT_IN_DEVICE_ADDRESS, "Phone sensors");
  private DeviceRegistry deviceRegistry = new DeviceRegistry(BUILT_IN_DEVICE);
  private final MemoryMetadataManager metadataManager = new MemoryMetadataManager();
  private DataController dataController =
      new InMemorySensorDatabase().makeSimpleController(metadataManager);
  private TestSensorRegistry sensors = new TestSensorRegistry();
  private final MemorySensorGroup availableDevices = new MemorySensorGroup(deviceRegistry);
  private final MemorySensorGroup pairedDevices = new MemorySensorGroup(deviceRegistry);
  private final TestDevicesPresenter presenter =
      new TestDevicesPresenter(availableDevices, pairedDevices);

  private Scenario scenario = new Scenario();
  private Map<String, SensorProvider> providers = scenario.providers;
  private Map<String, SensorDiscoverer> discoverers = scenario.discoverers;
  private ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providers);
  private ConnectableSensorRegistry sensorRegistry =
      new ConnectableSensorRegistry(
          dataController,
          discoverers,
          presenter,
          new MockScheduler(),
          null,
          null,
          deviceRegistry,
          null,
          UsageTracker.STUB,
          connector);

  // TODO: Pull this out and make it reusable?
  private class Scenario {
    private int sensorCount = 0;
    private int keyCount = 0;
    private EnumeratedDiscoverer discoverer = new EnumeratedDiscoverer();

    public Map<String, SensorProvider> providers = new HashMap<>();
    public Map<String, SensorDiscoverer> discoverers = new HashMap<>();

    public ScalarInputSpec makeSensorNewDevice() {
      String deviceId = Arbitrary.string("deviceId" + sensorCount);
      return makeSensorOnDevice(deviceId);
    }

    @NonNull
    private ScalarInputSpec makeSensorOnDevice(String deviceId) {
      String sensorName = Arbitrary.string("sensorName" + sensorCount);
      String serviceId = Arbitrary.string("serviceId" + sensorCount);
      String sensorAddress = Arbitrary.string("sensorAddress" + sensorCount);
      ScalarInputSpec sis =
          new ScalarInputSpec(sensorName, serviceId, sensorAddress, null, null, deviceId);
      addSpecToDiscoverer(sis);

      String deviceName = Arbitrary.string("deviceName");
      InputDeviceSpec device =
          new InputDeviceSpec(ScalarInputSpec.TYPE, sis.getDeviceAddress(), deviceName);
      addSpecToDiscoverer(device);
      deviceRegistry.addDevice(device);

      sensorCount++;
      return sis;
    }

    private void addSpecToDiscoverer(ExternalSensorSpec spec) {
      discoverer.addSpec(spec);
      discoverers.put(spec.getType(), discoverer);
      providers.put(spec.getType(), discoverer.getProvider());
    }

    public ScalarInputSpec makeSensorSameDevice(ExternalSensorSpec spec) {
      ScalarInputSpec previous = (ScalarInputSpec) spec;

      String sensorAddress = Arbitrary.string("sensorAddress" + sensorCount);
      String sensorName = Arbitrary.string("sensorName" + sensorCount);
      ScalarInputSpec sis =
          new ScalarInputSpec(
              sensorName,
              previous.getServiceId(),
              sensorAddress,
              null,
              null,
              previous.getDeviceId());
      addSpecToDiscoverer(sis);

      sensorCount++;
      return sis;
    }

    @NonNull
    private ConnectableSensor makeConnectedSensorNewDevice() {
      return connector.connected(makeSensorNewDevice().asGoosciSpec(), newConnectedSensorId());
    }

    private String newConnectedSensorId() {
      return Arbitrary.string("connectedSensorId" + sensorCount);
    }

    public String newSensorKey() {
      String key = "sensorKey" + keyCount;
      keyCount++;
      return key;
    }
  }

  @Test
  public void devicesAtTopLevel() {
    ExpandableDeviceAdapter adapter =
        ExpandableDeviceAdapter.createEmpty(sensorRegistry, deviceRegistry, null, null, 0);
    assertTrue(adapter instanceof ExpandableRecyclerAdapter);

    assertEquals(0, adapter.getParentItemList().size());

    ConnectableSensor sensor = scenario.makeConnectedSensorNewDevice();
    adapter.addSensor(scenario.newSensorKey(), sensor);
    assertEquals(1, adapter.getParentItemList().size());
    assertEquals(1, adapter.getParentItemList().get(0).getChildItemList().size());
    assertEquals(
        deviceRegistry.getDevice(sensor.getSpec()).getName(), adapter.getDevice(0).getDeviceName());
  }

  @Test
  public void phoneSensorsAtTop() {
    ExpandableDeviceAdapter adapter =
        ExpandableDeviceAdapter.createEmpty(sensorRegistry, deviceRegistry, null, null, 0);

    RecordingAdapterDataObserver observer = new RecordingAdapterDataObserver();
    adapter.registerAdapterDataObserver(observer);

    ConnectableSensor sensor = scenario.makeConnectedSensorNewDevice();
    adapter.addSensor(scenario.newSensorKey(), sensor);

    ConnectableSensor builtInSensor = connector.builtIn("builtInId", true);
    adapter.addSensor(scenario.newSensorKey(), builtInSensor);

    assertEquals(2, adapter.getParentItemList().size());
    DeviceParentListItem firstParent = (DeviceParentListItem) adapter.getParentItemList().get(0);
    assertEquals(BUILT_IN_DEVICE.getName(), firstParent.getDeviceName());
    observer.assertMostRecentNotification("Inserted 2 at 0");
  }

  @Test
  public void twoSensorsSameDevice() {
    ExpandableDeviceAdapter adapter =
        ExpandableDeviceAdapter.createEmpty(sensorRegistry, deviceRegistry, null, null, 0);

    ConnectableSensor sensor1 = scenario.makeConnectedSensorNewDevice();
    adapter.addSensor(scenario.newSensorKey(), sensor1);

    assertEquals(1, adapter.getParentItemList().size());
    assertEquals(1, adapter.getParentItemList().get(0).getChildItemList().size());
    assertEquals(1, adapter.getSensorCount());

    // Same device, new sensor
    ScalarInputSpec sis2 = scenario.makeSensorSameDevice(sensor1.getSpec());
    String id2 = Arbitrary.string(sensor1.getConnectedSensorId());
    adapter.addSensor(scenario.newSensorKey(), connector.connected(sis2.asGoosciSpec(), id2));

    assertEquals(1, adapter.getParentItemList().size());
    assertEquals(2, adapter.getParentItemList().get(0).getChildItemList().size());
    assertEquals(2, adapter.getSensorCount());

    // Different device, new sensor
    ConnectableSensor sensor3 = scenario.makeConnectedSensorNewDevice();
    adapter.addSensor(scenario.newSensorKey(), sensor3);
    assertEquals(2, adapter.getParentItemList().size());
    assertEquals(1, adapter.getParentItemList().get(1).getChildItemList().size());
    assertEquals(3, adapter.getSensorCount());
  }

  @Test
  public void replaceOnSecondAddSameKey() {
    ExpandableDeviceAdapter adapter =
        ExpandableDeviceAdapter.createEmpty(sensorRegistry, deviceRegistry, null, null, 0);

    RecordingAdapterDataObserver observer = new RecordingAdapterDataObserver();
    adapter.registerAdapterDataObserver(observer);

    String key = scenario.newSensorKey();
    ConnectableSensor sensor1 = scenario.makeConnectedSensorNewDevice();
    adapter.addSensor(key, sensor1);
    adapter.onParentListItemExpanded(0);
    assertEquals(1, adapter.getSensorCount());

    ScalarInputSpec replacement = scenario.makeSensorSameDevice(sensor1.getSpec());

    adapter.addSensor(
        key, connector.connected(replacement.asGoosciSpec(), sensor1.getConnectedSensorId()));

    ConnectableSensor sensor = adapter.getSensor(0, 0);

    // Replace only sensor of only device
    assertEquals(replacement.getName(), sensor.getAppearance(null).getName(null));
    observer.assertMostRecentNotification("Changed 2 at 0 [null]");
    assertEquals(1, adapter.getSensorCount());

    ConnectableSensor newSensor = scenario.makeConnectedSensorNewDevice();
    String newKey = scenario.newSensorKey();
    adapter.addSensor(newKey, newSensor);
    adapter.onParentListItemExpanded(2);

    // Replace only sensor of second device
    ScalarInputSpec newReplace = scenario.makeSensorSameDevice(newSensor.getSpec());
    adapter.addSensor(
        newKey, connector.connected(newReplace.asGoosciSpec(), newSensor.getConnectedSensorId()));
    observer.assertMostRecentNotification("Changed 2 at 2 [null]");

    ScalarInputSpec sensor3 = scenario.makeSensorSameDevice(newReplace);
    String key3 = scenario.newSensorKey();
    adapter.addSensor(key3, connector.connected(sensor3.asGoosciSpec(), "connectedId3"));

    // Replace second sensor of second device
    ScalarInputSpec replace3 = scenario.makeSensorSameDevice(sensor3);
    adapter.addSensor(key3, connector.connected(replace3.asGoosciSpec(), "connectedId3"));
    observer.assertMostRecentNotification("Changed 3 at 2 [null]");
  }

  @Test
  public void forgetWhenNoLongerMyDevice() {
    ExpandableDeviceAdapter adapter =
        ExpandableDeviceAdapter.createEmpty(sensorRegistry, deviceRegistry, null, null, 0);
    ConnectableSensor sensor = scenario.makeConnectedSensorNewDevice();
    String key = scenario.newSensorKey();

    adapter.setMyDevices(Lists.newArrayList(deviceRegistry.getDevice(sensor.getSpec())));
    adapter.addSensor(key, sensor);
    assertTrue(adapter.hasSensorKey(key));

    // Remove the my device, make sure we've forgotten the sensor as well
    adapter.setMyDevices(Lists.<InputDeviceSpec>newArrayList());
    assertFalse(adapter.hasSensorKey(key));
  }

  @Test
  public void checkBuiltInWhenForgettingLastSensor() {
    final ConnectableSensor sensor = scenario.makeConnectedSensorNewDevice();

    Experiment experiment = metadataManager.newExperiment();
    TestDevicesPresenter presenter = new TestDevicesPresenter(availableDevices, null);
    ConnectableSensorRegistry sensorRegistry =
        new ConnectableSensorRegistry(
            dataController,
            discoverers,
            presenter,
            new MockScheduler(),
            null,
            null,
            deviceRegistry,
            null,
            UsageTracker.STUB,
            connector);
    ExpandableDeviceAdapter adapter =
        ExpandableDeviceAdapter.createEmpty(sensorRegistry, deviceRegistry, null, this.sensors, 0);
    presenter.setPairedDevices(adapter);
    sensorRegistry.setExperimentId(experiment.getExperimentId(), this.sensors);

    String key = scenario.newSensorKey();

    // Add built-in device
    String builtInId = "builtInId";
    this.sensors.addManualBuiltInSensor(builtInId);
    metadataManager.removeSensorFromExperiment(builtInId, experiment.getExperimentId());

    InputDeviceSpec device = deviceRegistry.getDevice(sensor.getSpec());
    adapter.setMyDevices(Lists.newArrayList(device));
    adapter.addSensor(key, sensor);
    sensorRegistry.setPairedSensors(Lists.<ConnectableSensor>newArrayList(sensor));

    assertTrue(adapter.hasSensorKey(key));

    adapter.getMenuCallbacks().forgetDevice(device);

    // Make sure the built-in sensor is added to the experiment
    ExperimentSensors sensors =
        metadataManager.getExperimentSensors(
            experiment.getExperimentId(), Maps.<String, SensorProvider>newHashMap(), connector);
    List<ConnectableSensor> included = sensors.getExternalSensors();
    assertEquals(1, included.size());
    assertEquals("builtInId", included.get(0).getConnectedSensorId());

    // And make sure it's disabled
    adapter
        .getEnablementController()
        .addEnablementListener(
            adapter.getSensorKey(0, 0),
            new Consumer<Boolean>() {
              @Override
              public void take(Boolean aBoolean) {
                assertFalse(aBoolean);
              }
            });
  }
}
