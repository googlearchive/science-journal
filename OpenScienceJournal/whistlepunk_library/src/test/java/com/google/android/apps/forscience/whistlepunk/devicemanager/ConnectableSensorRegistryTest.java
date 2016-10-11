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

import android.app.PendingIntent;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputScenario;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.TestSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ConnectableSensorRegistryTest {
    private final Scheduler mScheduler = new MockScheduler();
    private MemorySensorGroup mAvailableDevices = new MemorySensorGroup();
    private MemorySensorGroup mPairedDevices = new MemorySensorGroup();
    private TestDevicesPresenter mPresenter = new TestDevicesPresenter();

    @Test
    public void testScalarInputPassthrough() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler);

        registry.startScanningInDiscoverers();

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(1, mAvailableDevices.size());

        StoringConsumer<ConnectableSensor> stored = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary("experimentId", mAvailableDevices.getKey(0),
                mPairedDevices.size(), stored);
        ScalarInputSpec sensor = (ScalarInputSpec) stored.getValue().getSpec();
        assertEquals(ScalarInputSpec.TYPE, sensor.getType());
        assertEquals(s.getServiceId(), sensor.getServiceId());
    }

    @Test
    public void testForgetSensor() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler);
        Map<String, ExternalSensorSpec> pairedSensors = new HashMap<>();
        String sensorId = Arbitrary.string();

        // First it's paired...
        pairedSensors.put(sensorId, s.makeSpec());
        registry.setPairedSensors(pairedSensors);
        registry.startScanningInDiscoverers();
        registry.stopScanningInDiscoverers();

        Assert.assertEquals(1, mPairedDevices.size());
        Assert.assertEquals(0, mAvailableDevices.size());

        // Then it's forgotten...
        pairedSensors.clear();
        mPairedDevices.removeAll();
        registry.setPairedSensors(pairedSensors);
        registry.startScanningInDiscoverers();

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(1, mAvailableDevices.size());
    }

    @Test
    public void testPairedWhenSet() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String sensorName = Arbitrary.string();
        sensors.put("sensorId",
                new ScalarInputSpec(sensorName, "serviceId", "address", null, null));

        registry.setPairedSensors(sensors);
        Assert.assertEquals(0, mAvailableDevices.size());
        Assert.assertEquals(1, mPairedDevices.size());

        assertTrue(registry.isPaired(mPairedDevices.getKey(0)));
        Assert.assertEquals(sensorName, mPairedDevices.getTitle(0));
    }

    @Test
    public void testOptionsDialog() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String sensorName = Arbitrary.string();
        String connectedId = Arbitrary.string();

        sensors.put(connectedId,
                new ScalarInputSpec(sensorName, s.getServiceId(), s.getSensorAddress(), null,
                        null));

        registry.setPairedSensors(sensors);

        String experimentId = Arbitrary.string();

        registry.showDeviceOptions(experimentId, mPairedDevices.getKey(0));
        assertEquals(experimentId, mPresenter.experimentId);
        assertEquals(connectedId, mPresenter.sensorId);
    }

    @Test
    public void testDuplicateSensorAdded() {
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();

        ExternalSensorDiscoverer dupeDiscoverer = new EnumeratedDiscoverer(
                new BleSensorSpec("address", "name"), new BleSensorSpec("address", "name"));
        discoverers.put("type", dupeDiscoverer);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler);

        registry.startScanningInDiscoverers();

        Assert.assertEquals(0, mPairedDevices.size());
        // Only 1 of the 2 duplicates!
        Assert.assertEquals(1, mAvailableDevices.size());
    }

    @Test
    public void testDontAddAvailableWhenAlreadyPaired() {
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();
        ExternalSensorDiscoverer dupeDiscoverer = new EnumeratedDiscoverer(
                new BleSensorSpec("address", "name"));
        discoverers.put("type", dupeDiscoverer);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String connectedId = Arbitrary.string();
        sensors.put(connectedId, new BleSensorSpec("address", "name"));
        registry.setPairedSensors(sensors);

        registry.startScanningInDiscoverers();

        Assert.assertEquals(1, mPairedDevices.size());
        // Not added here!
        Assert.assertEquals(0, mAvailableDevices.size());
    }

    @Test
    public void testDifferentConfigIsDuplicate() {
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();
        BleSensorSpec spec1 = new BleSensorSpec("address", "name");
        spec1.setCustomPin("A1");
        BleSensorSpec spec2 = new BleSensorSpec("address", "name");
        spec2.setCustomPin("A2");
        ExternalSensorDiscoverer d = new EnumeratedDiscoverer(spec1, spec2);
        discoverers.put("type", d);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler);

        registry.startScanningInDiscoverers();

        Assert.assertEquals(0, mPairedDevices.size());
        // Only 1 of the 2 duplicates!
        Assert.assertEquals(1, mAvailableDevices.size());
    }

    @Test
    public void testConnectedReplacesAvailable() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler);

        registry.startScanningInDiscoverers();

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        ScalarInputSpec spec = s.makeSpec();
        sensors.put(Arbitrary.string(), spec);
        registry.setPairedSensors(sensors);

        // Should move sensor from available to paired
        Assert.assertEquals(1, mPairedDevices.size());
        Assert.assertEquals(0, mAvailableDevices.size());
    }

    @Test
    public void testOrderOfApiSensors() {
        TestSensorDiscoverer tsd = new TestSensorDiscoverer("serviceName");
        tsd.addDevice("deviceId", "deviceName");
        tsd.addSensor("deviceId", "address1", "name1");
        tsd.addSensor("deviceId", "address2", "name2");

        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                tsd.makeDiscovererMap("serviceId"), mPresenter, mScheduler);

        registry.startScanningInDiscoverers();

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(2, mAvailableDevices.size());

        StoringConsumer<ConnectableSensor> stored1 = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary("experimentId",
                mAvailableDevices.getKey(0), 0, stored1);
        ScalarInputSpec sensor1 = (ScalarInputSpec) stored1.getValue().getSpec();

        StoringConsumer<ConnectableSensor> stored2 = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary("experimentId",
                mAvailableDevices.getKey(1), 1, stored2);
        ScalarInputSpec sensor2 = (ScalarInputSpec) stored2.getValue().getSpec();

        assertEquals(R.drawable.ic_api_01_white_24dp, sensor1.getDefaultIconId());
        assertEquals(R.drawable.ic_api_02_white_24dp, sensor2.getDefaultIconId());
    }

    @NonNull
    private DataController makeDataController() {
        return new InMemorySensorDatabase().makeSimpleController(new MemoryMetadataManager());
    }

    @Test
    public void testDontDuplicatePairedSensors() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        ScalarInputSpec spec = s.makeSpec();
        sensors.put(Arbitrary.string(), spec);

        // Call it twice, to make sure that we're replacing, not appending, duplicate paired
        // sensors.
        registry.setPairedSensors(sensors);
        registry.setPairedSensors(sensors);

        Assert.assertEquals(1, mPairedDevices.size());
        Assert.assertEquals(0, mAvailableDevices.size());
    }

    private class TestDevicesPresenter implements DevicesPresenter {
        public String experimentId;
        public String sensorId;

        @Override
        public void refreshScanningUI() {

        }

        @Override
        public void showDeviceOptions(String experimentId, String sensorId,
                PendingIntent externalSettingsIntent) {
            this.experimentId = experimentId;
            this.sensorId = sensorId;
        }

        @Override
        public SensorGroup getPairedSensorGroup() {
            return mPairedDevices;
        }

        @Override
        public SensorGroup getAvailableSensorGroup() {
            return mAvailableDevices;
        }
    }
}