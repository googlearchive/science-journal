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

import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.CurrentTimeClock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.FakeAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputScenario;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorAppearanceResources;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.TestSensor;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.TestSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class ConnectableSensorRegistryTest {
    private final Scheduler mScheduler = new MockScheduler();
    private final MemoryMetadataManager
            mMetadataManager = new MemoryMetadataManager();
    private final DeviceOptionsDialog.DeviceOptionsListener
            mOptionsListener = DeviceOptionsDialog.NULL_LISTENER;
    private final Map<String, SensorProvider> mProviderMap = new HashMap<>();
    private DeviceRegistry mDeviceRegistry = new DeviceRegistry(null);
    private MemorySensorGroup mAvailableDevices = new MemorySensorGroup(mDeviceRegistry);
    private MemorySensorGroup mPairedDevices = new MemorySensorGroup(mDeviceRegistry);
    private TestDevicesPresenter mPresenter = new TestDevicesPresenter(mAvailableDevices,
            mPairedDevices);
    private TestSensorRegistry mSensorRegistry = new TestSensorRegistry();
    private SensorAppearanceProvider mAppearanceProvider =
            new FakeAppearanceProvider(R.string.sensor_custom);
    // private ConnectableSensor.Connector mConnector = new ConnectableSensor.Connector(providers);

    @Test
    public void testScalarInputPassthrough() {
        final ScalarInputScenario s = makeScenarioWithProviders();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null, mAppearanceProvider, UsageTracker.STUB, s.makeConnector());

        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(1, mAvailableDevices.size());

        StoringConsumer<ConnectableSensor> stored = new StoringConsumer<>();
        registry.addSensorIfNecessary(mAvailableDevices.getKey(0), mPairedDevices.size(),
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
                mOptionsListener, null, mAppearanceProvider, UsageTracker.STUB, s.makeConnector());
        Map<String, ExternalSensorSpec> pairedSensors = new HashMap<>();
        String sensorId = Arbitrary.string();

        // First it's paired...
        pairedSensors.put(sensorId, s.makeSpec());
        setPairedSensors(registry, pairedSensors);
        registry.startScanningInDiscoverers(false);
        registry.stopScanningInDiscoverers();

        Assert.assertEquals(1, mPairedDevices.size());
        Assert.assertEquals(0, mAvailableDevices.size());

        // Then it's forgotten...
        pairedSensors.clear();
        mPairedDevices.removeAll();
        setPairedSensors(registry, pairedSensors);
        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(1, mAvailableDevices.size());
    }

    private void setPairedSensors(ConnectableSensorRegistry registry,
            Map<String, ExternalSensorSpec> pairedSensors) {
        mProviderMap.putAll(EnumeratedDiscoverer.buildProviderMap(
                pairedSensors.values().toArray(new ExternalSensorSpec[0])));
        List<ConnectableSensor> paired = Lists.newArrayList();
        ConnectableSensor.Connector connector = new ConnectableSensor.Connector(mProviderMap);
        for (Map.Entry<String, ExternalSensorSpec> entry : pairedSensors.entrySet())
            paired.add(connector.connected(entry.getValue().asGoosciSpec(), entry.getKey()));
        registry.setPairedSensors(paired);
    }

    @Test
    public void testPairedWhenSet() {
        String deviceName = Arbitrary.string();
        mDeviceRegistry.addDevice(
                new InputDeviceSpec(ScalarInputSpec.TYPE, "serviceId&deviceId", deviceName));
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null, mAppearanceProvider, UsageTracker.STUB, s.makeConnector());

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String sensorName = Arbitrary.string();
        ScalarInputSpec spec =
                new ScalarInputSpec(sensorName, "serviceId", "address", null, null, "deviceId");
        sensors.put("sensorId", spec);
        mProviderMap.putAll(EnumeratedDiscoverer.buildProviderMap(spec));

        setPairedSensors(registry, sensors);
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
                mOptionsListener, null, mAppearanceProvider, UsageTracker.STUB, s.makeConnector());

        final String experimentId = Arbitrary.string("experimentId");
        StoringConsumer<String> storeSensorId = new StoringConsumer<>();
        dc.addOrGetExternalSensor(s.makeSpec(), storeSensorId);
        String sensorId = storeSensorId.getValue();
        dc.addSensorToExperiment(experimentId, sensorId, TestConsumers.<Success>expectingSuccess());

        registry.setExperimentId(experimentId, mSensorRegistry);
        registry.showSensorOptions(mPairedDevices.getKey(0));
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
        Map<String, SensorDiscoverer> discoverers = new HashMap<>();

        SensorDiscoverer dupeDiscoverer = new EnumeratedDiscoverer(
                new BleSensorSpec("address", "name"), new BleSensorSpec("address", "name"));
        discoverers.put("bluetooth_le", dupeDiscoverer);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler, new CurrentTimeClock(), mOptionsListener,
                null, mAppearanceProvider, UsageTracker.STUB, new ConnectableSensor.Connector(
                AppSingleton.buildProviderMap(discoverers)));

        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(0, mPairedDevices.size());
        // Only 1 of the 2 duplicates!
        Assert.assertEquals(1, mAvailableDevices.size());
    }

    @Test
    public void testDontAddAvailableWhenAlreadyPaired() {
        Map<String, SensorDiscoverer> discoverers = new HashMap<>();
        SensorDiscoverer dupeDiscoverer = new EnumeratedDiscoverer(
                new BleSensorSpec("address", "name"));
        discoverers.put(BleSensorSpec.TYPE, dupeDiscoverer);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler, new CurrentTimeClock(), mOptionsListener,
                null, mAppearanceProvider, UsageTracker.STUB,
                ConnectableSensor.Connector.fromDiscoverers(discoverers));

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String connectedId = Arbitrary.string();
        sensors.put(connectedId, new BleSensorSpec("address", "name"));
        setPairedSensors(registry, sensors);

        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(1, mPairedDevices.size());
        // Not added here!
        Assert.assertEquals(0, mAvailableDevices.size());
    }

    @Test
    public void testDifferentConfigIsDuplicate() {
        Map<String, SensorDiscoverer> discoverers = new HashMap<>();
        BleSensorSpec spec1 = new BleSensorSpec("address", "name");
        spec1.setCustomPin("A1");
        BleSensorSpec spec2 = new BleSensorSpec("address", "name");
        spec2.setCustomPin("A2");
        SensorDiscoverer d = new EnumeratedDiscoverer(spec1, spec2);
        discoverers.put("bluetooth_le", d);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler, new CurrentTimeClock(), mOptionsListener,
                null, mAppearanceProvider, UsageTracker.STUB,
                ConnectableSensor.Connector.fromDiscoverers(discoverers));

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
                mOptionsListener, null, mAppearanceProvider, UsageTracker.STUB, s.makeConnector());

        registry.startScanningInDiscoverers(false);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        ScalarInputSpec spec = s.makeSpec();
        sensors.put(Arbitrary.string(), spec);
        setPairedSensors(registry, sensors);

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
                mOptionsListener, null, mAppearanceProvider, UsageTracker.STUB,
                new ConnectableSensor.Connector(mProviderMap));

        registry.startScanningInDiscoverers(false);

        Assert.assertEquals(0, mPairedDevices.size());
        Assert.assertEquals(2, mAvailableDevices.size());

        StoringConsumer<ConnectableSensor> stored1 = new StoringConsumer<>();
        registry.addSensorIfNecessary(mAvailableDevices.getKey(0), 0, stored1);
        ScalarInputSpec sensor1 = (ScalarInputSpec) stored1.getValue().getSpec();

        StoringConsumer<ConnectableSensor> stored2 = new StoringConsumer<>();
        registry.addSensorIfNecessary(mAvailableDevices.getKey(1), 1, stored2);
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
        Map<String, SensorDiscoverer> discoverers = tsd.makeDiscovererMap("serviceId");
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler, clock, mOptionsListener,
                null, mAppearanceProvider, UsageTracker.STUB,
                ConnectableSensor.Connector.fromDiscoverers(discoverers));

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

        Map<String, SensorDiscoverer> discoverers = new HashMap<>();
        discoverers.put(ScalarInputSpec.TYPE, tsd1.makeScalarInputDiscoverer("serviceId",
                MoreExecutors.directExecutor()));
        discoverers.put(ScalarInputSpec.TYPE + "2", tsd2.makeScalarInputDiscoverer("serviceId2",
                MoreExecutors.directExecutor()));
        SettableClock clock = new SettableClock();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers, mPresenter, mScheduler, clock, mOptionsListener, null,
                mAppearanceProvider, UsageTracker.STUB,
                ConnectableSensor.Connector.fromDiscoverers(discoverers));

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
                mOptionsListener, null, mAppearanceProvider, UsageTracker.STUB, s.makeConnector());

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        ScalarInputSpec spec = s.makeSpec();
        sensors.put(Arbitrary.string(), spec);

        // Call it twice, to make sure that we're replacing, not appending, duplicate paired
        // sensors.
        setPairedSensors(registry, sensors);
        setPairedSensors(registry, sensors);

        Assert.assertEquals(1, mPairedDevices.size());
        Assert.assertEquals(0, mAvailableDevices.size());
    }

    @Test
    public void reloadAppearances() {
        final ScalarInputScenario s = new ScalarInputScenario();
        s.appearance.units = "oldUnits";
        mProviderMap.putAll(s.makeScalarInputProviders());
        DataController dc = makeDataController();

        StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
        dc.createExperiment(cExperiment);
        Experiment experiment = cExperiment.getValue();

        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(dc,
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, null, mAppearanceProvider, UsageTracker.STUB, s.makeConnector());
        registry.setExperimentId(experiment.getExperimentId(), mSensorRegistry);
        registry.pair(mAvailableDevices.getKey(0));
        s.appearance.units = "newUnits";
        registry.refresh(false, mSensorRegistry);

        Map<String, ExternalSensorSpec> sensors = ConnectableSensor.makeMap(
                mMetadataManager.getExperimentSensors(experiment.getExperimentId(),
                        mProviderMap, s.makeConnector()).getIncludedSensors());
        ScalarInputSpec retrievedSpec = (ScalarInputSpec) sensors.values().iterator().next();
        SensorAppearance appearance = retrievedSpec.getSensorAppearance();
        assertEquals("newUnits", appearance.getUnits(null));
    }

    @Test
    public void loadDevices() {
        final ScalarInputScenario s = new ScalarInputScenario();
        String serviceId = s.getServiceId();

        TestSensorDiscoverer tsd = new TestSensorDiscoverer(s.getServiceName());
        mProviderMap.putAll(tsd.makeProviderMap(serviceId));
        Map<String, SensorDiscoverer> discoverers = tsd.makeDiscovererMap(serviceId);
        DataController dc = makeDataController();

        InputDeviceSpec deviceSpec = new InputDeviceSpec(ScalarInputSpec.TYPE,
                ScalarInputSpec.makeApiDeviceAddress(s.getServiceId(), s.getDeviceId()),
                "deviceName");

        StoringConsumer<String> cSensorId = new StoringConsumer<>();
        ScalarInputSpec sensorSpec = new ScalarInputSpec(s.getSensorName(), s.getServiceId(),
                s.getSensorAddress(), null, s.appearance, s.getDeviceId());
        dc.addOrGetExternalSensor(sensorSpec, cSensorId);
        String sensorId = cSensorId.getValue();

        DeviceRegistry deviceRegistry = new DeviceRegistry(null);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(dc, discoverers,
                mPresenter, mScheduler, new CurrentTimeClock(), mOptionsListener, deviceRegistry,
                mAppearanceProvider, UsageTracker.STUB, s.makeConnector());

        registry.setMyDevices(deviceSpec);
        setPairedSensors(registry, ImmutableMap.of(sensorId, sensorSpec));

        assertEquals(deviceSpec, deviceRegistry.getDevice(sensorSpec));
    }

    @Test
    public void addDevicesAsFound() {
        final ScalarInputScenario s = new ScalarInputScenario();
        mProviderMap.putAll(s.makeScalarInputProviders());
        DataController dc = makeDataController();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(dc,
                s.makeScalarInputDiscoverers(), mPresenter, mScheduler, new CurrentTimeClock(),
                mOptionsListener, mDeviceRegistry, mAppearanceProvider, UsageTracker.STUB,
                s.makeConnector());
        registry.setExperimentId("experimentId", mSensorRegistry);

        InputDeviceSpec device = mDeviceRegistry.getDevice(ScalarInputSpec.TYPE,
                ScalarInputSpec.makeApiDeviceAddress(s.getServiceId(), s.getDeviceId()));
        assertEquals(s.getDeviceName(), device.getName());
    }

    @Test
    public void getBuiltInSensors() {
        mSensorRegistry.addManualBuiltInSensor("id1");
        mSensorRegistry.addManualBuiltInSensor("id2");

        DataController dc = makeDataController();
        HashMap<String, SensorDiscoverer> discoverers = new HashMap<>();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(dc,
                discoverers, mPresenter, mScheduler,
                new CurrentTimeClock(), mOptionsListener, mDeviceRegistry, mAppearanceProvider,
                UsageTracker.STUB, ConnectableSensor.Connector.fromDiscoverers(discoverers));

        registry.setExperimentId("experimentId", mSensorRegistry);
        assertEquals(2, mPairedDevices.getSensorCount());

        // Don't double-count built-in sensors
        registry.setExperimentId("experimentId", mSensorRegistry);
        assertEquals(2, mPairedDevices.getSensorCount());
    }

}
