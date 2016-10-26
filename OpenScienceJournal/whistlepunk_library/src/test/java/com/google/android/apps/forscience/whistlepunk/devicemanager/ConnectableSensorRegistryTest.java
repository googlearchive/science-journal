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
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.CurrentTimeClock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.MemoryAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputScenario;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorAppearanceResources;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.TestSensor;
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
    private final MemoryMetadataManager
            mMetadataManager = new MemoryMetadataManager();
    private final DeviceOptionsDialog.DeviceOptionsListener
            mOptionsListener = DeviceOptionsDialog.NULL_LISTENER;
    private final Map<String, ExternalSensorProvider> mProviderMap = new HashMap<>();
    private DeviceRegistry mDeviceRegistry = new DeviceRegistry();
    private MemorySensorGroup mAvailableDevices = new MemorySensorGroup(mDeviceRegistry);
    private MemorySensorGroup mPairedDevices = new MemorySensorGroup(mDeviceRegistry);
    private TestDevicesPresenter mPresenter = new TestDevicesPresenter();

    @Test
    public void testScalarInputPassthrough() {
        final ScalarInputScenario s = makeScenarioWithProviders();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null);

        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(1, mAvailableDevices.size());

        StoringConsumer<ConnectableSensor> stored = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary(mAvailableDevices.getKey(0), mPairedDevices.size(),
                stored);
        ScalarInputSpec sensor = (ScalarInputSpec) stored.getValue().getSpec();
        assertEquals(ScalarInputSpec.TYPE, sensor.getType());
        assertEquals(s.getServiceId(), sensor.getServiceId());
    }

    @Test
    public void testForgetSensor() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null);
        Map<String, ExternalSensorSpec> pairedSensors = new HashMap<>();
        String sensorId = Arbitrary.string();

        // First it's paired...
        pairedSensors.put(sensorId, s.makeSpec());
        registry.setPairedSensors(pairedSensors);
        registry.startScanningInDiscoverers(false);
        registry.stopScanningInDiscoverers();

        Assert.assertEquals(1, mPairedDevices.size());
        Assert.assertEquals(0, mAvailableDevices.size());

        // Then it's forgotten...
        pairedSensors.clear();
        mPairedDevices.removeAll();
        registry.setPairedSensors(pairedSensors);
        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(1, mAvailableDevices.size());
    }

    @Test
    public void testPairedWhenSet() {
        String deviceName = Arbitrary.string();
        mDeviceRegistry.addDevice(ScalarInputSpec.TYPE,
                new InputDeviceSpec("serviceId&deviceId", deviceName));
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String sensorName = Arbitrary.string();
        sensors.put("sensorId",
                new ScalarInputSpec(sensorName, "serviceId", "address", null, null, "deviceId"));

        registry.setPairedSensors(sensors);
        Assert.assertEquals(0, mAvailableDevices.size());
        Assert.assertEquals(1, mPairedDevices.size());

        assertTrue(registry.isPaired(mPairedDevices.getKey(0)));
        assertEquals(sensorName, mPairedDevices.getTitle(0));
        assertEquals(deviceName, mPairedDevices.getDeviceName(0));
    }

    @Test
    public void testOptionsDialog() {
        final ScalarInputScenario s = makeScenarioWithProviders();
        final DataController dc = makeDataController();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(dc,
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null);

        final String experimentId = Arbitrary.string("experimentId");
        StoringConsumer<String> storeSensorId = new StoringConsumer<>();
        dc.addOrGetExternalSensor(s.makeSpec(), storeSensorId);
        String sensorId = storeSensorId.getValue();
        dc.addSensorToExperiment(experimentId, sensorId, TestConsumers.<Success>expectingSuccess());

        registry.setExperimentId(experimentId);
        registry.showDeviceOptions(mPairedDevices.getKey(0));
        assertEquals(experimentId, mPresenter.experimentId);
        assertEquals(sensorId, mPresenter.sensorId);
    }

    @NonNull
    private ScalarInputScenario makeScenarioWithProviders() {
        final ScalarInputScenario s = new ScalarInputScenario();
        mProviderMap.putAll(s.makeScalarInputProviders());
        return s;
    }

    @Test
    public void testDuplicateSensorAdded() {
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();

        ExternalSensorDiscoverer dupeDiscoverer = new EnumeratedDiscoverer(
                new BleSensorSpec("address", "name"), new BleSensorSpec("address", "name"));
        discoverers.put("type", dupeDiscoverer);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler, new CurrentTimeClock(), mOptionsListener,
                null);

        registry.startScanningInDiscoverers(false);

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
                discoverers, mPresenter, mScheduler, new CurrentTimeClock(), mOptionsListener,
                null);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String connectedId = Arbitrary.string();
        sensors.put(connectedId, new BleSensorSpec("address", "name"));
        registry.setPairedSensors(sensors);

        registry.startScanningInDiscoverers(false);

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
                discoverers, mPresenter, mScheduler, new CurrentTimeClock(), mOptionsListener,
                null);

        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(0, mPairedDevices.size());
        // Only 1 of the 2 duplicates!
        Assert.assertEquals(1, mAvailableDevices.size());
    }

    @Test
    public void testConnectedReplacesAvailable() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null);

        registry.startScanningInDiscoverers(false);

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
        final SensorAppearanceResources appearance = new SensorAppearanceResources();
        tsd.addSensor("deviceId", new TestSensor("address1", "name1", appearance));
        final SensorAppearanceResources appearance1 = new SensorAppearanceResources();
        tsd.addSensor("deviceId", new TestSensor("address2", "name2", appearance1));
        mProviderMap.putAll(tsd.makeProviderMap("serviceId"));

        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                tsd.makeDiscovererMap("serviceId"), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null);

        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(2, mAvailableDevices.size());

        StoringConsumer<ConnectableSensor> stored1 = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary(mAvailableDevices.getKey(0), 0, stored1);
        ScalarInputSpec sensor1 = (ScalarInputSpec) stored1.getValue().getSpec();

        StoringConsumer<ConnectableSensor> stored2 = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary(mAvailableDevices.getKey(1), 1, stored2);
        ScalarInputSpec sensor2 = (ScalarInputSpec) stored2.getValue().getSpec();

        assertEquals(R.drawable.ic_api_01_white_24dp, sensor1.getDefaultIconId());
        assertEquals(R.drawable.ic_api_02_white_24dp, sensor2.getDefaultIconId());
    }

    @Test
    public void testNoLongerAvailable() {
        TestSensorDiscoverer tsd = new TestSensorDiscoverer("serviceName");
        tsd.addDevice("deviceId", "deviceName");
        final SensorAppearanceResources appearance = new SensorAppearanceResources();
        tsd.addSensor("deviceId", new TestSensor("address1", "name1", appearance));
        final SensorAppearanceResources appearance1 = new SensorAppearanceResources();
        tsd.addSensor("deviceId", new TestSensor("address2", "name2", appearance1));

        SettableClock clock = new SettableClock();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                tsd.makeDiscovererMap("serviceId"), mPresenter, mScheduler, clock, mOptionsListener,
                null);

        clock.setNow(0);
        registry.startScanningInDiscoverers(false);
        assertEquals(2, mAvailableDevices.size());

        tsd.removeSensor("deviceId", "address2");

        registry.stopScanningInDiscoverers();

        clock.setNow(20_000);
        registry.startScanningInDiscoverers(false);
        assertEquals(1, mAvailableDevices.size());
    }

    @Test
    public void testNoLongerAvailableMultipleDiscoverers() {
        TestSensorDiscoverer tsd1 = new TestSensorDiscoverer("serviceName");
        tsd1.addDevice("deviceId", "deviceName");
        final SensorAppearanceResources appearance = new SensorAppearanceResources();
        tsd1.addSensor("deviceId", new TestSensor("address1", "name1", appearance));
        final SensorAppearanceResources appearance1 = new SensorAppearanceResources();
        tsd1.addSensor("deviceId", new TestSensor("address2", "name2", appearance1));

        TestSensorDiscoverer tsd2 = new TestSensorDiscoverer("serviceName2");
        tsd2.addDevice("deviceId2", "deviceName2");
        final SensorAppearanceResources appearance2 = new SensorAppearanceResources();
        tsd2.addSensor("deviceId2", new TestSensor("address3", "name3", appearance2));
        final SensorAppearanceResources appearance3 = new SensorAppearanceResources();
        tsd2.addSensor("deviceId2", new TestSensor("address4", "name4", appearance3));

        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();
        discoverers.put(ScalarInputSpec.TYPE, tsd1.makeScalarInputDiscoverer("serviceId"));
        discoverers.put(ScalarInputSpec.TYPE + "2", tsd2.makeScalarInputDiscoverer("serviceId2"));
        SettableClock clock = new SettableClock();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler, clock, mOptionsListener, null);

        clock.setNow(0);
        registry.startScanningInDiscoverers(true);
        assertEquals(4, mAvailableDevices.size());

        tsd1.removeSensor("deviceId", "address2");

        registry.stopScanningInDiscoverers();

        // Calling clear cache, so even one millisecond is enough to drop stale sensors
        clock.setNow(1);
        registry.startScanningInDiscoverers(true);
        assertEquals(3, mAvailableDevices.size());
    }

    @NonNull
    private DataController makeDataController() {
        return new InMemorySensorDatabase().makeSimpleController(mMetadataManager, mProviderMap);
    }

    @Test
    public void testDontDuplicatePairedSensors() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null);

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

    @Test
    public void reloadAppearances() {
        final ScalarInputScenario s = new ScalarInputScenario();
        s.appearance.units = "oldUnits";
        mProviderMap.putAll(s.makeScalarInputProviders());
        DataController dc = makeDataController();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(dc,
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null);
        registry.setExperimentId("experimentId");
        registry.pair(mAvailableDevices.getKey(0), new MemoryAppearanceProvider());
        s.appearance.units = "newUnits";
        registry.refresh(false);

        Map<String, ExternalSensorSpec> sensors = ConnectableSensor.makeMap(
                mMetadataManager.getExperimentExternalSensors(
                        "experimentId", mProviderMap));
        ScalarInputSpec retrievedSpec = (ScalarInputSpec) sensors.values().iterator().next();
        SensorAppearance appearance = retrievedSpec.getSensorAppearance();
        assertEquals("newUnits", appearance.getUnits(null));
    }

    @Test public void addDevicesAsFound() {
        final ScalarInputScenario s = new ScalarInputScenario();
        mProviderMap.putAll(s.makeScalarInputProviders());
        DataController dc = makeDataController();
        DeviceRegistry deviceRegistry = new DeviceRegistry();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(dc,
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, deviceRegistry);
        registry.setExperimentId("experimentId");

        InputDeviceSpec device = deviceRegistry.getDevice(ScalarInputSpec.TYPE,
                ScalarInputSpec.makeApiDeviceAddress(s.getServiceId(), s.getDeviceId()));
        assertEquals(s.getDeviceName(), device.getName());
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