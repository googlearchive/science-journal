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

import android.app.PendingIntent;
import android.content.Context;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputScenario;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.StubPreferenceCategory;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.TestSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;

import java.util.HashMap;
import java.util.Map;

public class ConnectableSensorRegistryTest extends AndroidTestCase {
    StubPreferenceCategory mAvailableDevices;
    StubPreferenceCategory mPairedDevices;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mAvailableDevices = new StubPreferenceCategory(getContext());
        mPairedDevices = new StubPreferenceCategory(getContext());
    }

    public void testScalarInputPassthrough() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers());

        registry.startScanningInDiscoverers(mAvailableDevices);

        assertEquals(0, mPairedDevices.prefs.size());
        assertEquals(1, mAvailableDevices.prefs.size());

        Preference pref = mAvailableDevices.prefs.get(0);
        StoringConsumer<ConnectableSensor> stored = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary("experimentId", pref,
                mPairedDevices.getPreferenceCount(), stored);
        ScalarInputSpec sensor = (ScalarInputSpec) stored.getValue().getSpec();
        assertEquals(ScalarInputSpec.TYPE, sensor.getType());
        assertEquals(s.getServiceId(), sensor.getServiceId());
    }

    public void testForgetSensor() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers());
        Map<String, ExternalSensorSpec> pairedSensors = new HashMap<>();
        String sensorId = Arbitrary.string();

        // First it's paired...
        pairedSensors.put(sensorId, s.makeSpec());
        registry.setPairedSensors(mAvailableDevices, mPairedDevices, pairedSensors);
        registry.startScanningInDiscoverers(mAvailableDevices);

        assertEquals(1, mPairedDevices.prefs.size());
        assertEquals(0, mAvailableDevices.prefs.size());

        // Then it's forgotten...
        pairedSensors.clear();
        mPairedDevices.removeAll();
        registry.setPairedSensors(mAvailableDevices, mPairedDevices, pairedSensors);
        registry.startScanningInDiscoverers(mAvailableDevices);

        assertEquals(0, mPairedDevices.prefs.size());
        assertEquals(1, mAvailableDevices.prefs.size());
    }

    public void testPairedWhenSet() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(
                makeDataController(), s.makeScalarInputDiscoverers());

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String sensorName = Arbitrary.string();
        sensors.put("sensorId",
                new ScalarInputSpec(sensorName, "serviceId", "address", null, null));

        registry.setPairedSensors(mAvailableDevices, mPairedDevices, sensors);
        assertEquals(0, mAvailableDevices.prefs.size());
        assertEquals(1, mPairedDevices.prefs.size());

        Preference preference = mPairedDevices.prefs.get(0);
        assertTrue(registry.getIsPairedFromPreference(preference));
        assertEquals(sensorName, preference.getTitle());
    }

    public void testOptionsDialog() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers());

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String sensorName = Arbitrary.string();
        String connectedId = Arbitrary.string();

        sensors.put(connectedId,
                new ScalarInputSpec(sensorName, s.getServiceId(), s.getSensorAddress(), null,
                        null));

        registry.setPairedSensors(mAvailableDevices, mPairedDevices, sensors);
        Preference pref = mPairedDevices.prefs.get(0);

        String experimentId = Arbitrary.string();

        TestDeviceOptionsPresenter presenter = new TestDeviceOptionsPresenter();
        registry.showDeviceOptions(presenter, experimentId, pref, null);
        assertEquals(experimentId, presenter.experimentId);
        assertEquals(connectedId, presenter.sensorId);
    }

    public void testDuplicateSensorAdded() {
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();

        ExternalSensorDiscoverer dupeDiscoverer = makeDiscovererThatWillDiscover(
                new BleSensorSpec("address", "name"), new BleSensorSpec("address", "name"));
        discoverers.put("type", dupeDiscoverer);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers);

        registry.startScanningInDiscoverers(mAvailableDevices);

        assertEquals(0, mPairedDevices.prefs.size());
        // Only 1 of the 2 duplicates!
        assertEquals(1, mAvailableDevices.prefs.size());
    }

    private ExternalSensorDiscoverer.DiscoveredSensor discovered(final ExternalSensorSpec spec) {
        return new ExternalSensorDiscoverer.DiscoveredSensor() {
            @Override
            public ExternalSensorSpec getSpec() {
                return spec;
            }

            @Override
            public PendingIntent getSettingsIntent() {
                return null;
            }
        };
    }

    public void testDontAddAvailableWhenAlreadyPaired() {
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();
        ExternalSensorDiscoverer dupeDiscoverer = makeDiscovererThatWillDiscover(
                new BleSensorSpec("address", "name"));
        discoverers.put("type", dupeDiscoverer);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        String connectedId = Arbitrary.string();
        sensors.put(connectedId, new BleSensorSpec("address", "name"));
        registry.setPairedSensors(mAvailableDevices, mPairedDevices, sensors);

        registry.startScanningInDiscoverers(mAvailableDevices);

        assertEquals(1, mPairedDevices.prefs.size());
        // Not added here!
        assertEquals(0, mAvailableDevices.prefs.size());
    }

    public void testDifferentConfigIsDuplicate() {
        Map<String, ExternalSensorDiscoverer> discoverers = new HashMap<>();
        BleSensorSpec spec1 = new BleSensorSpec("address", "name");
        spec1.setCustomPin("A1");
        BleSensorSpec spec2 = new BleSensorSpec("address", "name");
        spec2.setCustomPin("A2");
        ExternalSensorDiscoverer d = makeDiscovererThatWillDiscover(spec1, spec2);
        discoverers.put("type", d);
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                discoverers);

        registry.startScanningInDiscoverers(mAvailableDevices);

        assertEquals(0, mPairedDevices.prefs.size());
        // Only 1 of the 2 duplicates!
        assertEquals(1, mAvailableDevices.prefs.size());
    }

    public void testConnectedReplacesAvailable() {
        final ScalarInputScenario s = new ScalarInputScenario();
        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                s.makeScalarInputDiscoverers());

        registry.startScanningInDiscoverers(mAvailableDevices);

        Map<String, ExternalSensorSpec> sensors = new HashMap<>();
        ScalarInputSpec spec = s.makeSpec();
        sensors.put(Arbitrary.string(), spec);
        registry.setPairedSensors(mAvailableDevices, mPairedDevices, sensors);

        // Should move sensor from available to paired
        assertEquals(1, mPairedDevices.prefs.size());
        assertEquals(0, mAvailableDevices.prefs.size());
    }

    public void testOrderOfApiSensors() {
        TestSensorDiscoverer tsd = new TestSensorDiscoverer("serviceName");
        tsd.addDevice("deviceId", "deviceName");
        tsd.addSensor("deviceId", "address1", "name1");
        tsd.addSensor("deviceId", "address2", "name2");

        ConnectableSensorRegistry registry = new ConnectableSensorRegistry(makeDataController(),
                tsd.makeDiscovererMap("serviceId"));

        registry.startScanningInDiscoverers(mAvailableDevices);

        assertEquals(0, mPairedDevices.prefs.size());
        assertEquals(2, mAvailableDevices.prefs.size());

        StoringConsumer<ConnectableSensor> stored1 = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary("experimentId", mAvailableDevices.prefs.get(0), 0,
                stored1);
        ScalarInputSpec sensor1 = (ScalarInputSpec) stored1.getValue().getSpec();

        StoringConsumer<ConnectableSensor> stored2 = new StoringConsumer<>();
        registry.addExternalSensorIfNecessary("experimentId", mAvailableDevices.prefs.get(1), 1,
                stored2);
        ScalarInputSpec sensor2 = (ScalarInputSpec) stored2.getValue().getSpec();

        assertEquals(R.drawable.ic_api_01_white_24dp, sensor1.getDefaultIconId());
        assertEquals(R.drawable.ic_api_02_white_24dp, sensor2.getDefaultIconId());
    }

    ExternalSensorDiscoverer makeDiscovererThatWillDiscover(final ExternalSensorSpec... specs) {
        return new StubSensorDiscoverer() {
            @Override
            public boolean startScanning(Consumer<DiscoveredSensor> onEachSensorFound,
                    Runnable onScanDone, FailureListener onScanError, Context context) {
                for (ExternalSensorSpec spec : specs) {
                    onEachSensorFound.take(discovered(spec));
                }
                return true;
            }
        };
    }

    @NonNull
    private DataController makeDataController() {
        return new InMemorySensorDatabase().makeSimpleController(new MemoryMetadataManager());
    }
}