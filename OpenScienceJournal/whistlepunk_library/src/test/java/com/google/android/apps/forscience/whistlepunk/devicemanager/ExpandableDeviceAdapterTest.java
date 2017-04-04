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

import android.support.annotation.NonNull;

import com.bignerdranch.expandablerecyclerview.Adapter.ExpandableRecyclerAdapter;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExpandableDeviceAdapterTest {
    private static final InputDeviceSpec BUILT_IN_DEVICE = new InputDeviceSpec(InputDeviceSpec.TYPE,
            InputDeviceSpec.BUILT_IN_DEVICE_ADDRESS, "Phone sensors");
    private DeviceRegistry mDeviceRegistry = new DeviceRegistry(BUILT_IN_DEVICE);
    private final MemoryMetadataManager mMetadataManager = new MemoryMetadataManager();
    private DataController mDataController = new InMemorySensorDatabase().makeSimpleController(
            mMetadataManager);
    private TestSensorRegistry mSensors = new TestSensorRegistry();
    private Map<String, ExternalSensorDiscoverer> mDiscoverers = new HashMap<>();
    private final MemorySensorGroup mAvailableDevices = new MemorySensorGroup(mDeviceRegistry);
    private final MemorySensorGroup mPairedDevices = new MemorySensorGroup(mDeviceRegistry);
    private final TestDevicesPresenter mPresenter = new TestDevicesPresenter(mAvailableDevices,
            mPairedDevices);
    private ConnectableSensorRegistry mSensorRegistry = new ConnectableSensorRegistry(
            mDataController, mDiscoverers, mPresenter, null, null, null,
            mDeviceRegistry, null, UsageTracker.STUB);
    private Scenario mScenario = new Scenario();

    private class Scenario {
        private int mSensorCount = 0;
        private int mKeyCount = 0;

        public ScalarInputSpec makeSensorNewDevice() {
            String deviceId = Arbitrary.string("deviceId" + mSensorCount);
            return makeSensorOnDevice(deviceId);
        }

        @NonNull
        private ScalarInputSpec makeSensorOnDevice(String deviceId) {
            String sensorName = Arbitrary.string("sensorName" + mSensorCount);
            String serviceId = Arbitrary.string("serviceId" + mSensorCount);
            String sensorAddress = Arbitrary.string("sensorAddress" + mSensorCount);
            ScalarInputSpec sis = new ScalarInputSpec(sensorName, serviceId, sensorAddress, null,
                    null, deviceId);

            String deviceName = Arbitrary.string("deviceName");
            mDeviceRegistry.addDevice(
                    new InputDeviceSpec(ScalarInputSpec.TYPE, sis.getDeviceAddress(), deviceName));

            mSensorCount++;
            return sis;
        }

        public ScalarInputSpec makeSensorSameDevice(ExternalSensorSpec spec) {
            ScalarInputSpec previous = (ScalarInputSpec) spec;

            String sensorAddress = Arbitrary.string("sensorAddress" + mSensorCount);
            String sensorName = Arbitrary.string("sensorName" + mSensorCount);
            ScalarInputSpec sis = new ScalarInputSpec(sensorName, previous.getServiceId(),
                    sensorAddress, null, null, previous.getDeviceId());

            mSensorCount++;
            return sis;
        }

        @NonNull
        private ConnectableSensor makeConnectedSensorNewDevice() {
            return ConnectableSensor.connected(makeSensorNewDevice(), newConnectedSensorId());
        }


        private String newConnectedSensorId() {
            return Arbitrary.string("connectedSensorId" + mSensorCount);
        }

        public String newSensorKey() {
            String key = "sensorKey" + mKeyCount;
            mKeyCount++;
            return key;
        }
    }

    @Test
    public void devicesAtTopLevel() {
        ExpandableDeviceAdapter adapter = ExpandableDeviceAdapter.createEmpty(mSensorRegistry,
                mDeviceRegistry, null, null, 0);
        assertTrue(adapter instanceof ExpandableRecyclerAdapter);

        assertEquals(0, adapter.getParentItemList().size());

        ConnectableSensor sensor = mScenario.makeConnectedSensorNewDevice();
        adapter.addSensor(mScenario.newSensorKey(), sensor);
        assertEquals(1, adapter.getParentItemList().size());
        assertEquals(1, adapter.getParentItemList().get(0).getChildItemList().size());
        assertEquals(mDeviceRegistry.getDevice(sensor.getSpec()).getName(),
                adapter.getDevice(0).getDeviceName());
    }

    @Test
    public void phoneSensorsAtTop() {
        ExpandableDeviceAdapter adapter = ExpandableDeviceAdapter.createEmpty(mSensorRegistry,
                mDeviceRegistry, null, null, 0);

        RecordingAdapterDataObserver observer = new RecordingAdapterDataObserver();
        adapter.registerAdapterDataObserver(observer);

        ConnectableSensor sensor = mScenario.makeConnectedSensorNewDevice();
        adapter.addSensor(mScenario.newSensorKey(), sensor);

        ConnectableSensor builtInSensor = ConnectableSensor.builtIn("builtInId", true);
        adapter.addSensor(mScenario.newSensorKey(), builtInSensor);

        assertEquals(2, adapter.getParentItemList().size());
        DeviceParentListItem firstParent = (DeviceParentListItem) adapter.getParentItemList().get(
                0);
        assertEquals(BUILT_IN_DEVICE.getName(), firstParent.getDeviceName());
        observer.assertMostRecentNotification("Inserted 2 at 0");
    }

    @Test
    public void twoSensorsSameDevice() {
        ExpandableDeviceAdapter adapter = ExpandableDeviceAdapter.createEmpty(mSensorRegistry,
                mDeviceRegistry, null, null, 0);

        ConnectableSensor sensor1 = mScenario.makeConnectedSensorNewDevice();
        adapter.addSensor(mScenario.newSensorKey(), sensor1);

        assertEquals(1, adapter.getParentItemList().size());
        assertEquals(1, adapter.getParentItemList().get(0).getChildItemList().size());
        assertEquals(1, adapter.getSensorCount());

        // Same device, new sensor
        ScalarInputSpec sis2 = mScenario.makeSensorSameDevice(sensor1.getSpec());
        String id2 = Arbitrary.string(sensor1.getConnectedSensorId());
        adapter.addSensor(mScenario.newSensorKey(), ConnectableSensor.connected(sis2, id2));

        assertEquals(1, adapter.getParentItemList().size());
        assertEquals(2, adapter.getParentItemList().get(0).getChildItemList().size());
        assertEquals(2, adapter.getSensorCount());

        // Different device, new sensor
        ConnectableSensor sensor3 = mScenario.makeConnectedSensorNewDevice();
        adapter.addSensor(mScenario.newSensorKey(), sensor3);
        assertEquals(2, adapter.getParentItemList().size());
        assertEquals(1, adapter.getParentItemList().get(1).getChildItemList().size());
        assertEquals(3, adapter.getSensorCount());
    }

    @Test
    public void replaceOnSecondAddSameKey() {
        ExpandableDeviceAdapter adapter = ExpandableDeviceAdapter.createEmpty(mSensorRegistry,
                mDeviceRegistry, null, null, 0);

        RecordingAdapterDataObserver observer = new RecordingAdapterDataObserver();
        adapter.registerAdapterDataObserver(observer);

        String key = mScenario.newSensorKey();
        ConnectableSensor sensor1 = mScenario.makeConnectedSensorNewDevice();
        adapter.addSensor(key, sensor1);
        adapter.onParentListItemExpanded(0);
        assertEquals(1, adapter.getSensorCount());

        ScalarInputSpec replacement = mScenario.makeSensorSameDevice(sensor1.getSpec());

        adapter.addSensor(key,
                ConnectableSensor.connected(replacement, sensor1.getConnectedSensorId()));

        ConnectableSensor sensor = adapter.getSensor(0, 0);

        // Replace only sensor of only device
        assertEquals(replacement.getName(), sensor.getAppearance(null).getName(null));
        observer.assertMostRecentNotification("Changed 2 at 0 [null]");
        assertEquals(1, adapter.getSensorCount());

        ConnectableSensor newSensor = mScenario.makeConnectedSensorNewDevice();
        String newKey = mScenario.newSensorKey();
        adapter.addSensor(newKey, newSensor);
        adapter.onParentListItemExpanded(2);

        // Replace only sensor of second device
        ScalarInputSpec newReplace = mScenario.makeSensorSameDevice(newSensor.getSpec());
        adapter.addSensor(newKey,
                ConnectableSensor.connected(newReplace, newSensor.getConnectedSensorId()));
        observer.assertMostRecentNotification("Changed 2 at 2 [null]");

        ScalarInputSpec sensor3 = mScenario.makeSensorSameDevice(newReplace);
        String key3 = mScenario.newSensorKey();
        adapter.addSensor(key3, ConnectableSensor.connected(sensor3, "connectedId3"));

        // Replace second sensor of second device
        ScalarInputSpec replace3 = mScenario.makeSensorSameDevice(sensor3);
        adapter.addSensor(key3, ConnectableSensor.connected(replace3, "connectedId3"));
        observer.assertMostRecentNotification("Changed 3 at 2 [null]");
    }

    @Test
    public void forgetWhenNoLongerMyDevice() {
        ExpandableDeviceAdapter adapter = ExpandableDeviceAdapter.createEmpty(mSensorRegistry,
                mDeviceRegistry, null, null, 0);
        ConnectableSensor sensor = mScenario.makeConnectedSensorNewDevice();
        String key = mScenario.newSensorKey();

        adapter.setMyDevices(Lists.newArrayList(mDeviceRegistry.getDevice(sensor.getSpec())));
        adapter.addSensor(key, sensor);
        assertTrue(adapter.hasSensorKey(key));

        // Remove the my device, make sure we've forgotten the sensor as well
        adapter.setMyDevices(Lists.<InputDeviceSpec>newArrayList());
        assertFalse(adapter.hasSensorKey(key));
    }

    @Test
    public void checkBuiltInWhenForgettingLastSensor() {
        String experimentId = "experimentId";
        TestDevicesPresenter presenter = new TestDevicesPresenter(mAvailableDevices,
                null);
        ConnectableSensorRegistry sensorRegistry = new ConnectableSensorRegistry(
                mDataController, mDiscoverers, presenter, null, null, null,
                mDeviceRegistry, null, UsageTracker.STUB);
        ExpandableDeviceAdapter adapter = ExpandableDeviceAdapter.createEmpty(sensorRegistry,
                mDeviceRegistry, null, mSensors, 0);
        presenter.setPairedDevices(adapter);
        sensorRegistry.setExperimentId(experimentId, mSensors);

        final ConnectableSensor sensor = mScenario.makeConnectedSensorNewDevice();
        String key = mScenario.newSensorKey();

        // Add built-in device
        String builtInId = "builtInId";
        mSensors.addManualBuiltInSensor(builtInId);
        mMetadataManager.removeSensorFromExperiment(builtInId, experimentId);

        InputDeviceSpec device = mDeviceRegistry.getDevice(sensor.getSpec());
        adapter.setMyDevices(Lists.newArrayList(device));
        adapter.addSensor(key, sensor);
        sensorRegistry.setPairedSensors(Lists.<ConnectableSensor>newArrayList(sensor));

        assertTrue(adapter.hasSensorKey(key));

        adapter.getMenuCallbacks().forgetDevice(device);

        // Make sure the built-in sensor is added to the experiment
        ExperimentSensors sensors = mMetadataManager.getExperimentExternalSensors(experimentId,
                Maps.<String, ExternalSensorProvider>newHashMap());
        List<ConnectableSensor> included = sensors.getIncludedSensors();
        assertEquals(1, included.size());
        assertEquals("builtInId", included.get(0).getConnectedSensorId());

        // And make sure it's disabled
        adapter.getEnablementController().addEnablementListener(adapter.getSensorKey(0, 0),
                new Consumer<Boolean>() {
                    @Override
                    public void take(Boolean aBoolean) {
                        assertFalse(aBoolean);
                    }
                });
    }
}