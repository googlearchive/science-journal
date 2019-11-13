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

import android.os.RemoteException;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class TestSensorDiscoverer extends ISensorDiscoverer.Stub {
  private final Executor executor;
  private String serviceName;
  private List<Device> devices = new ArrayList<>();
  private Multimap<String, TestSensor> sensors = HashMultimap.create();

  public TestSensorDiscoverer(String serviceName) {
    this(serviceName, MoreExecutors.directExecutor());
  }

  public TestSensorDiscoverer(String serviceName, Executor executor) {
    this.serviceName = serviceName;
    this.executor = executor;
  }

  @NonNull
  public ScalarInputDiscoverer makeScalarInputDiscoverer(
      final String serviceId, Executor uiThread) {
    return new ScalarInputDiscoverer(
        makeFinder(serviceId),
        new TestStringSource(),
        uiThread,
        new MockScheduler(),
        100,
        new RecordingUsageTracker());
  }

  @NonNull
  public Consumer<AppDiscoveryCallbacks> makeFinder(final String serviceId) {
    return new Consumer<AppDiscoveryCallbacks>() {
      @Override
      public void take(AppDiscoveryCallbacks adc) {
        adc.onServiceFound(serviceId, TestSensorDiscoverer.this);
        adc.onDiscoveryDone();
      }
    };
  }

  @Override
  public String getName() throws RemoteException {
    return serviceName;
  }

  public void addDevice(String deviceId, String name) {
    devices.add(new Device(deviceId, name));
  }

  @Override
  public void scanDevices(final IDeviceConsumer c) throws RemoteException {
    for (final Device device : devices) {
      executor.execute(
          new Runnable() {
            @Override
            public void run() {
              device.deliverTo(c);
            }
          });
    }
    onDevicesDone(c);
  }

  protected void onDevicesDone(final IDeviceConsumer c) {
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              c.onScanDone();
            } catch (RemoteException e) {
              throw new RuntimeException(e);
            }
          }
        });
  }

  public void addSensor(String deviceId, TestSensor sensor) {
    sensors.put(deviceId, sensor);
  }

  public void removeSensor(String deviceId, String address) {
    Collection<TestSensor> testSensors = sensors.get(deviceId);
    Iterator<TestSensor> iter = testSensors.iterator();
    while (iter.hasNext()) {
      if (iter.next().getSensorAddress().equals(address)) {
        iter.remove();
      }
    }
  }

  @Override
  public void scanSensors(String deviceId, final ISensorConsumer c) throws RemoteException {
    for (final TestSensor sensor : sensors.get(deviceId)) {
      executor.execute(
          new Runnable() {
            @Override
            public void run() {
              sensor.deliverTo(c);
            }
          });
    }
    onSensorsDone(c);
  }

  protected void onSensorsDone(final ISensorConsumer c) {
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              c.onScanDone();
            } catch (RemoteException e) {
              throw new RuntimeException(e);
            }
          }
        });
  }

  @Override
  public ISensorConnector getConnector() throws RemoteException {
    return null;
  }

  @NonNull
  public Map<String, SensorDiscoverer> makeDiscovererMap(String serviceId) {
    ScalarInputDiscoverer sid =
        makeScalarInputDiscoverer(serviceId, MoreExecutors.directExecutor());
    Map<String, SensorDiscoverer> discoverers = new HashMap<>();
    discoverers.put(ScalarInputSpec.TYPE, sid);
    return discoverers;
  }

  @NonNull
  public Map<String, SensorProvider> makeProviderMap(String serviceId) {
    Map<String, SensorProvider> providers = new HashMap<>();
    Map<String, SensorDiscoverer> discoverers = makeDiscovererMap(serviceId);
    for (Map.Entry<String, SensorDiscoverer> entry : discoverers.entrySet()) {
      providers.put(entry.getKey(), entry.getValue().getProvider());
    }
    return providers;
  }

  private class Device {
    private final String deviceId;
    private final String name;

    public Device(String deviceId, String name) {
      this.deviceId = deviceId;
      this.name = name;
    }

    public void deliverTo(IDeviceConsumer c) {
      try {
        c.onDeviceFound(deviceId, name, null);
      } catch (RemoteException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
