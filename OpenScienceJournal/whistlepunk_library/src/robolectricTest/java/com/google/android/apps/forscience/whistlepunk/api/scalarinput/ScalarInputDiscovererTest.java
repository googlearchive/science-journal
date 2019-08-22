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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.RemoteException;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.AccumulatingConsumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.DeviceRegistry;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScalarInputDiscovererTest {
  @Test
  public void testStartScanning() {
    final ScalarInputScenario s = new ScalarInputScenario();
    final DeviceRegistry deviceRegistry = new DeviceRegistry(null);
    ExplicitExecutor uiThread = new ExplicitExecutor();
    ScalarInputDiscoverer sid = s.buildDiscoverer(uiThread);

    RecordingScanListener listener =
        new RecordingScanListener() {
          @Override
          public void onDeviceFound(SensorDiscoverer.DiscoveredDevice device) {
            super.onDeviceFound(device);
            deviceRegistry.addDevice(device.getSpec());
          }
        };
    assertTrue(sid.startScanning(listener, TestConsumers.expectingSuccess()));

    // Haven't executed main thread yet.
    assertEquals(0, listener.sensors.size());
    assertEquals(0, deviceRegistry.getDeviceCount());
    assertFalse(listener.isDone);

    uiThread.drain();
    ExternalSensorSpec sensor =
        ExternalSensorSpec.fromGoosciSpec(
            getOnly(listener.sensors).getSensorSpec(), s.makeScalarInputProviders());
    ScalarInputSpec spec = (ScalarInputSpec) sensor;
    assertEquals(s.getSensorName(), spec.getName());
    assertEquals(s.getSensorAddress(), spec.getSensorAddressInService());
    assertEquals(s.getServiceId(), spec.getServiceId());
    assertEquals(ScalarInputSpec.TYPE, spec.getType());
    assertEquals(s.getServiceId() + "&" + s.getDeviceId(), spec.getDeviceAddress());

    InputDeviceSpec device =
        deviceRegistry.getDevice(ScalarInputSpec.TYPE, spec.getDeviceAddress());
    assertEquals(s.getDeviceName(), device.getName());
    assertEquals(spec.getDeviceAddress(), device.getDeviceAddress());

    SensorDiscoverer.DiscoveredDevice discovered = getOnly(listener.devices);
    assertEquals(s.getServiceId(), discovered.getServiceId());
    assertEquals(s.getDeviceName(), discovered.getSpec().getName());

    SensorDiscoverer.DiscoveredService service = getOnly(listener.services);
    assertEquals(s.getServiceName(), service.getName());
    assertEquals(s.getServiceId(), service.getServiceId());

    assertTrue(listener.isDone);
  }

  @Test
  public void testStartScanningWithBadScanDevices() {
    final ScalarInputScenario s = new ScalarInputScenario();
    ExplicitExecutor uiThread = new ExplicitExecutor();
    final TestSensorDiscoverer discoverer =
        new TestSensorDiscoverer(s.getServiceName()) {
          @Override
          public void scanDevices(IDeviceConsumer c) throws RemoteException {
            throw new NullPointerException();
          }
        };
    ScalarInputDiscoverer sid = discoverer.makeScalarInputDiscoverer(s.getServiceId(), uiThread);

    RecordingScanListener listener = new RecordingScanListener();
    sid.startScanning(listener, TestConsumers.expectingFailureType(NullPointerException.class));
  }

  private <T> T getOnly(List<T> list) {
    assertEquals(1, list.size());
    return list.get(0);
  }

  @Test
  public void testScanError() {
    final String serviceName = Arbitrary.string();
    final String errorText = Arbitrary.string();

    ScalarInputDiscoverer sid =
        new ScalarInputDiscoverer(
            new Consumer<AppDiscoveryCallbacks>() {
              @Override
              public void take(AppDiscoveryCallbacks adc) {
                adc.onServiceFound(
                    null,
                    new TestSensorDiscoverer(serviceName) {
                      @Override
                      public void scanDevices(IDeviceConsumer c) throws RemoteException {
                        throw new RemoteException(errorText);
                      }

                      @Override
                      public void scanSensors(String actualDeviceId, ISensorConsumer c)
                          throws RemoteException {
                        throw new RemoteException("Should never be thrown");
                      }

                      @Override
                      public ISensorConnector getConnector() throws RemoteException {
                        return null;
                      }
                    });
              }
            },
            new TestStringSource(),
            MoreExecutors.directExecutor(),
            new MockScheduler(),
            100,
            new RecordingUsageTracker());

    AccumulatingConsumer<SensorDiscoverer.DiscoveredSensor> c = new AccumulatingConsumer<>();
    RecordingRunnable onScanDone = new RecordingRunnable();
    assertTrue(
        sid.startScanning(
            new TestScanListener(c, onScanDone),
            TestConsumers.expectingFailure(
                new FailureListener() {
                  @Override
                  public void fail(Exception e) {
                    String message = e.getMessage();
                    assertTrue(message.contains(errorText));
                  }
                })));
    assertEquals(0, c.seen.size());
    assertFalse(onScanDone.hasRun);
  }

  @Test
  public void testScanAllDevices() {
    ExplicitExecutor executor = new ExplicitExecutor();

    final TestSensorDiscoverer service1 = new TestSensorDiscoverer("service1", executor);
    service1.addDevice("deviceId1", "deviceName1");
    final SensorAppearanceResources appearance = new SensorAppearanceResources();
    service1.addSensor("deviceId1", new TestSensor("sensorAddress1", "sensorName1", appearance));

    final TestSensorDiscoverer service2 = new TestSensorDiscoverer("service2", executor);
    service2.addDevice("deviceId2", "deviceName2");
    final SensorAppearanceResources appearance1 = new SensorAppearanceResources();
    service2.addSensor("deviceId2", new TestSensor("sensorAddress2", "sensorName2", appearance1));

    RecordingUsageTracker usageTracker = new RecordingUsageTracker();
    ScalarInputDiscoverer sid =
        new ScalarInputDiscoverer(
            new Consumer<AppDiscoveryCallbacks>() {
              @Override
              public void take(AppDiscoveryCallbacks adc) {
                adc.onServiceFound("serviceId1", service1);
                adc.onServiceFound("serviceId2", service2);
                adc.onDiscoveryDone();
              }
            },
            new TestStringSource(),
            executor,
            new MockScheduler(),
            100,
            usageTracker);
    final AccumulatingConsumer<SensorDiscoverer.DiscoveredSensor> c = new AccumulatingConsumer<>();
    RecordingRunnable onScanDone =
        new RecordingRunnable() {
          @Override
          public void run() {
            super.run();

            // Make sure this isn't run until we've seen 2 sensors
            assertEquals(2, c.seen.size());
          }
        };
    sid.startScanning(new TestScanListener(c, onScanDone), TestConsumers.expectingSuccess());

    assertFalse(onScanDone.hasRun);
    executor.drain();
    assertTrue(onScanDone.hasRun);
    assertTrue(usageTracker.events.isEmpty());
  }

  @Test
  public void testTimeout() {
    final ScalarInputScenario s = new ScalarInputScenario();
    final TestSensorDiscoverer discoverer = s.neverDoneDiscoverer();
    discoverer.addDevice(s.getDeviceId(), s.getDeviceName());
    final SensorAppearanceResources appearance = new SensorAppearanceResources();
    discoverer.addSensor(
        s.getDeviceId(), new TestSensor(s.getSensorAddress(), s.getSensorName(), appearance));
    MockScheduler scheduler = new MockScheduler();

    RecordingUsageTracker usageTracker = new RecordingUsageTracker();
    ScalarInputDiscoverer sid =
        new ScalarInputDiscoverer(
            discoverer.makeFinder("serviceId"),
            new TestStringSource(),
            MoreExecutors.directExecutor(),
            scheduler,
            100,
            usageTracker);

    AccumulatingConsumer<SensorDiscoverer.DiscoveredSensor> c = new AccumulatingConsumer<>();
    RecordingRunnable onScanDone = new RecordingRunnable();
    assertTrue(
        sid.startScanning(new TestScanListener(c, onScanDone), TestConsumers.expectingSuccess()));
    GoosciSensorSpec.SensorSpec sensor = c.getOnlySeen().getSensorSpec();
    assertEquals(ScalarInputSpec.TYPE, sensor.getInfo().getProviderId());
    assertEquals(s.getSensorName(), sensor.getRememberedAppearance().getName());

    assertFalse(onScanDone.hasRun);
    scheduler.incrementTime(200);
    assertTrue(onScanDone.hasRun);
    assertEquals(2, usageTracker.events.size());
    Set<String> eventLabels = new HashSet<>();
    for (TrackedEvent event : usageTracker.events) {
      eventLabels.add(event.getLabel());
    }
    assertTrue(eventLabels.toString(), eventLabels.contains("SERVICE:serviceId"));
    assertTrue(eventLabels.toString(), eventLabels.contains("DEVICE:" + s.getDeviceId()));
  }

  @Test
  public void testTimeoutWithNullUsageTracker() {
    final ScalarInputScenario s = new ScalarInputScenario();
    final TestSensorDiscoverer discoverer = s.neverDoneDiscoverer();
    discoverer.addDevice(s.getDeviceId(), s.getDeviceName());
    final SensorAppearanceResources appearance = new SensorAppearanceResources();
    discoverer.addSensor(
        s.getDeviceId(), new TestSensor(s.getSensorAddress(), s.getSensorName(), appearance));
    MockScheduler scheduler = new MockScheduler();

    ScalarInputDiscoverer sid =
        new ScalarInputDiscoverer(
            discoverer.makeFinder("serviceId"),
            new TestStringSource(),
            MoreExecutors.directExecutor(),
            scheduler,
            100,
            null);

    AccumulatingConsumer<SensorDiscoverer.DiscoveredSensor> c = new AccumulatingConsumer<>();
    RecordingRunnable onScanDone = new RecordingRunnable();
    sid.startScanning(new TestScanListener(c, onScanDone), TestConsumers.expectingSuccess());

    // Just don't crash
    scheduler.incrementTime(200);
  }
}
