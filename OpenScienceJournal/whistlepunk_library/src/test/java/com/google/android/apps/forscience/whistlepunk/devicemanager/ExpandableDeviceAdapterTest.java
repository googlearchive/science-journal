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
import static org.junit.Assert.assertTrue;

import android.support.annotation.NonNull;

import com.bignerdranch.expandablerecyclerview.Adapter.ExpandableRecyclerAdapter;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;

import org.junit.Test;

public class ExpandableDeviceAdapterTest {
    private DeviceRegistry mDeviceRegistry = new DeviceRegistry();
    private DataController mDataController = InMemorySensorDatabase.makeSimpleController();
    private ConnectableSensorRegistry mSensorRegistry = new ConnectableSensorRegistry(
            mDataController, null, null, null, null, null, null);
    private Scenario mScenario = new Scenario();

    private class Scenario {
        private int mSensorCount = 0;
        private int mKeyCount = 0;

        public ScalarInputSpec makeSensorNewDevice() {
            String deviceId = Arbitrary.string("deviceId" + mSensorCount);
            String sensorName = Arbitrary.string("sensorName" + mSensorCount);
            String serviceId = Arbitrary.string("serviceId" + mSensorCount);
            String sensorAddress = Arbitrary.string("sensorAddress" + mSensorCount);
            ScalarInputSpec sis = new ScalarInputSpec(sensorName, serviceId, sensorAddress, null,
                    null, deviceId);

            String deviceName = Arbitrary.string("deviceName");
            mDeviceRegistry.addDevice(ScalarInputSpec.TYPE,
                    new InputDeviceSpec(sis.getDeviceAddress(), deviceName));

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
            return ConnectableSensor.connected(makeSensorNewDevice(),
                    Arbitrary.string("connectedSensorId" + mSensorCount));
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
                mDeviceRegistry);
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
    public void twoSensorsSameDevice() {
        ExpandableDeviceAdapter adapter = ExpandableDeviceAdapter.createEmpty(mSensorRegistry,
                mDeviceRegistry);

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
                mDeviceRegistry);

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
        assertEquals(replacement.getName(), sensor.getName());
        observer.assertMostRecentNotification("Changed 1 at 1 [null]");
        assertEquals(1, adapter.getSensorCount());

        ConnectableSensor newSensor = mScenario.makeConnectedSensorNewDevice();
        String newKey = mScenario.newSensorKey();
        adapter.addSensor(newKey, newSensor);
        adapter.onParentListItemExpanded(2);

        // Replace only sensor of second device
        ScalarInputSpec newReplace = mScenario.makeSensorSameDevice(newSensor.getSpec());
        adapter.addSensor(newKey,
                ConnectableSensor.connected(newReplace, newSensor.getConnectedSensorId()));
        observer.assertMostRecentNotification("Changed 1 at 3 [null]");

        ScalarInputSpec sensor3 = mScenario.makeSensorSameDevice(newReplace);
        String key3 = mScenario.newSensorKey();
        adapter.addSensor(key3, ConnectableSensor.connected(sensor3, "connectedId3"));

        // Replace second sensor of second device
        ScalarInputSpec replace3 = mScenario.makeSensorSameDevice(sensor3);
        adapter.addSensor(key3, ConnectableSensor.connected(replace3, "connectedId3"));
        observer.assertMostRecentNotification("Changed 1 at 4 [null]");
    }

}