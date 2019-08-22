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
package com.google.android.apps.forscience.whistlepunk;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.NativeBleDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.SineWavePseudoSensor;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SensorRegistryTest {

  private final Context context = RuntimeEnvironment.application.getApplicationContext();

  @Rule public DevOptionsResource devOptions = new DevOptionsResource();

  @Test
  public void testImmediatelyReset() {
    // Test relies on set DevOptions which are bypassed in Release builds
    Assume.assumeTrue(DevOptionsFragment.isDebugVersion());

    SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(context);

    devOptions.setPrefValue(false);
    reg.refreshBuiltinSensors(context);
    assertFalse(reg.getAllSources().contains(SineWavePseudoSensor.ID));

    devOptions.setPrefValue(true);
    reg.refreshBuiltinSensors(context);
    assertTrue(reg.getAllSources().contains(SineWavePseudoSensor.ID));

    // double-check removal
    devOptions.setPrefValue(false);
    reg.refreshBuiltinSensors(context);
    assertFalse(reg.getAllSources().contains(SineWavePseudoSensor.ID));
  }

  @Test
  public void testNoDevOptionsInReleaseBuild() {
    Assume.assumeFalse(DevOptionsFragment.isDebugVersion());

    SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(context);

    devOptions.setPrefValue(true);
    reg.refreshBuiltinSensors(context);
    assertFalse(reg.getAllSources().contains(SineWavePseudoSensor.ID));
  }

  @Test
  public void testDontClearExternalSensors() {
    SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(context);

    List<ConnectableSensor> sensors = new ArrayList<>();
    String bleSensorId = "bleSensor1";

    HashMap<String, SensorProvider> providers = bleProviderMap();
    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providers);

    sensors.add(
        connector.connected(new BleSensorSpec("address", "name").asGoosciSpec(), bleSensorId));

    reg.updateExternalSensors(sensors, providers);
    assertTrue(reg.getAllSources().contains(bleSensorId));
    reg.refreshBuiltinSensors(context);
    assertTrue(reg.getAllSources().contains(bleSensorId));
  }

  @Test
  public void testLoggingId() {
    SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(context);

    HashMap<String, SensorProvider> providers = bleProviderMap();
    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providers);

    List<ConnectableSensor> sensors = new ArrayList<>();
    String bleSensorId = "aa:bb:cc:dd";
    sensors.add(
        connector.connected(new BleSensorSpec(bleSensorId, "name").asGoosciSpec(), bleSensorId));

    reg.updateExternalSensors(sensors, providers);
    assertTrue(reg.getAllSources().contains(bleSensorId));

    assertEquals("bluetooth_le:raw", reg.getLoggingId(bleSensorId));
  }

  @Test
  public void testDropWaitingOperations() {
    SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(context);
    StoringConsumer cyes = new StoringConsumer();
    String tagyes = "yes";

    StoringConsumer cno = new StoringConsumer();
    String tagno = "no";

    String newSensorId = "newSensorId";
    reg.withSensorChoice(tagyes, newSensorId, cyes);
    reg.withSensorChoice(tagno, newSensorId, cno);
    reg.removePendingOperations(tagno);
    ExternalSensorSpec spec = new BleSensorSpec("address", "name");
    ImmutableMap<String, SensorProvider> providers =
        ImmutableMap.<String, SensorProvider>of(BleSensorSpec.TYPE, bleProvider());
    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providers);
    reg.updateExternalSensors(
        Lists.newArrayList(connector.connected(spec.asGoosciSpec(), newSensorId)), providers);
    assertNull(cno.latestChoice);
    assertNotNull(cyes.latestChoice);
  }

  @Test
  public void testExternalSensorOrder() {
    SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(context);

    List<ConnectableSensor> sensors = new ArrayList<>();
    HashMap<String, SensorProvider> providers = bleProviderMap();
    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providers);

    sensors.add(
        connector.connected(new BleSensorSpec("aa:bb:cc:aa", "name4").asGoosciSpec(), "id4"));
    sensors.add(
        connector.connected(new BleSensorSpec("aa:bb:cc:bb", "name3").asGoosciSpec(), "id3"));
    sensors.add(
        connector.connected(new BleSensorSpec("aa:bb:cc:cc", "name2").asGoosciSpec(), "id2"));
    sensors.add(
        connector.connected(new BleSensorSpec("aa:bb:cc:dd", "name1").asGoosciSpec(), "id1"));

    reg.updateExternalSensors(sensors, providers);

    List<String> allSources = reg.getAllSources();
    assertTrue(allSources.indexOf("id4") < allSources.indexOf("id3"));
    assertTrue(allSources.indexOf("id3") < allSources.indexOf("id2"));
    assertTrue(allSources.indexOf("id2") < allSources.indexOf("id1"));
  }

  @Test
  public void roundtripThroughSpecProto() {
    SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(context);

    List<ConnectableSensor> sensors = new ArrayList<>();
    HashMap<String, SensorProvider> providers = bleProviderMap();
    ConnectableSensor.Connector connector = new ConnectableSensor.Connector(providers);

    BleSensorSpec bleSpec = new BleSensorSpec("aa:bb:cc:aa", "name4");
    String id = "id4";
    sensors.add(connector.connected(bleSpec.asGoosciSpec(), id));
    reg.updateExternalSensors(sensors, providers);

    GoosciSensorSpec.SensorSpec specProto =
        reg.getSpecForId(id, buildAppearanceProvider(), context);
    assertEquals(bleSpec.getAddress(), specProto.getInfo().getAddress());

    ExternalSensorSpec afterRoundTrip = ExternalSensorSpec.fromGoosciSpec(specProto, providers);

    assertEquals(bleSpec.toString(), afterRoundTrip.toString());
  }

  @NonNull
  private HashMap<String, SensorProvider> bleProviderMap() {
    HashMap<String, SensorProvider> providers = new HashMap<>();
    providers.put(BleSensorSpec.TYPE, bleProvider());
    return providers;
  }

  @Test
  public void addressForBuiltIn() {
    SensorRegistry reg = SensorRegistry.createWithBuiltinSensors(context);

    GoosciSensorSpec.SensorSpec spec =
        reg.getSpecForId(DecibelSensor.ID, buildAppearanceProvider(), context);
    assertEquals(SensorRegistry.WP_HARDWARE_PROVIDER_ID, spec.getInfo().getProviderId());
    assertEquals(DecibelSensor.ID, spec.getInfo().getAddress());
  }

  @NonNull
  private FakeAppearanceProvider buildAppearanceProvider() {
    return new FakeAppearanceProvider(android.R.string.ok);
  }

  private SensorProvider bleProvider() {
    return new NativeBleDiscoverer(context).getProvider();
  }

  private static class StoringConsumer extends Consumer<SensorChoice> {
    public SensorChoice latestChoice = null;

    @Override
    public void take(SensorChoice sensorChoice) {
      latestChoice = sensorChoice;
    }
  }
}
