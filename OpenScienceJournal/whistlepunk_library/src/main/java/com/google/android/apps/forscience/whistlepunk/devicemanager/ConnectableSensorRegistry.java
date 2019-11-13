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

import androidx.annotation.NonNull;
import android.util.ArrayMap;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.TaskPool;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Runnables;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers sensors that have been found during scanning, and can expose them by adding them to a
 * PreferenceCategory.
 */
public class ConnectableSensorRegistry {
  public static final String TAG = "ConSensorRegistry";

  // Don't remove a sensor unless it's been gone 15 seconds
  private static final long ASSUME_GONE_TIMEOUT_MILLIS = 15_000;
  private static final String EXTERNAL_SENSOR_KEY_PREFIX = "sensorKey";

  private final DataController dataController;
  private final Map<String, SensorDiscoverer> discoverers;

  private final DevicesPresenter presenter;
  private final Map<String, ConnectableSensor> sensors = new ArrayMap<>();
  private final Map<String, SensorDiscoverer.SettingsInterface> settingsIntents = new ArrayMap<>();
  private final Scheduler scheduler;
  private final Map<String, SensorProvider> providers;
  private boolean scanning = false;
  private int scanCount = 0;

  // Maps from sensorKey to the timestamp (from clock) of the last time it showed up in a scan.
  private Map<String, Long> mostRecentlySeen = new ArrayMap<>();

  private int keyNum = 0;
  private String experimentId = null;
  private Clock clock;
  private DeviceOptionsListener optionsListener;
  private DeviceRegistry deviceRegistry;
  private final SensorAppearanceProvider appearanceProvider;
  private UsageTracker usageTracker;
  private Runnable timeoutRunnable;
  private ConnectableSensor.Connector connector;

  // TODO: reduce parameter list?
  public ConnectableSensorRegistry(
      DataController dataController,
      Map<String, SensorDiscoverer> discoverers,
      DevicesPresenter presenter,
      Scheduler scheduler,
      Clock clock,
      DeviceOptionsListener optionsListener,
      DeviceRegistry deviceRegistry,
      SensorAppearanceProvider appearanceProvider,
      UsageTracker usageTracker,
      ConnectableSensor.Connector connector) {
    this.dataController = dataController;
    this.discoverers = discoverers;
    providers = AppSingleton.buildProviderMap(this.discoverers);
    this.presenter = presenter;
    this.scheduler = Preconditions.checkNotNull(scheduler);
    this.clock = clock;
    this.optionsListener = optionsListener;
    this.deviceRegistry = deviceRegistry;
    this.appearanceProvider = appearanceProvider;
    this.usageTracker = usageTracker;
    this.connector = connector;
  }

  public void pair(final String sensorKey) {
    final SensorDiscoverer.SettingsInterface settings = settingsIntents.get(sensorKey);
    addSensorIfNecessary(
        sensorKey,
        getPairedGroup().getSensorCount(),
        new LoggingConsumer<ConnectableSensor>(TAG, "Add external sensor") {
          @Override
          public void success(final ConnectableSensor sensor) {
            if (presenter.isDestroyed()) {
              return;
            }
            getPairedGroup().replaceSensor(sensorKey, sensor);
            appearanceProvider.loadAppearances(
                new LoggingConsumer<Success>(TAG, "Load appearance") {
                  @Override
                  public void success(Success value) {
                    if (sensor.shouldShowOptionsOnConnect() && settings != null) {
                      presenter.showSensorOptions(
                          experimentId, sensor.getConnectedSensorId(), settings);
                    }
                  }
                });
          }
        });
  }

  // TODO: should SensorRegistry be a field?
  public void refresh(final boolean clearSensorCache, final SensorRegistry sr) {
    Preconditions.checkNotNull(sr);
    stopScanningInDiscoverers();
    dataController.getMyDevices(
        new LoggingConsumer<List<InputDeviceSpec>>(TAG, "Load my devices") {
          @Override
          public void success(List<InputDeviceSpec> myDevices) {
            if (presenter.isDestroyed()) {
              return;
            }
            onMyDevicesLoaded(myDevices, clearSensorCache, sr);
          }
        });
  }

  private void onMyDevicesLoaded(
      List<InputDeviceSpec> myDevices, final boolean clearSensorCache, final SensorRegistry sr) {
    Preconditions.checkNotNull(sr);
    for (final InputDeviceSpec device : myDevices) {
      deviceRegistry.addDevice(device);
    }
    // Paired group needs to know My Devices so it can show them.
    getPairedGroup().setMyDevices(myDevices);

    // Available group needs to know My Devices so it can _not_ show them
    getAvailableGroup().setMyDevices(myDevices);

    dataController.getExternalSensorsByExperiment(
        experimentId,
        new LoggingConsumer<ExperimentSensors>(TAG, "Load external sensors") {
          @Override
          public void success(ExperimentSensors sensors) {
            if (presenter.isDestroyed()) {
              return;
            }
            List<ConnectableSensor> allSensors = new ArrayList<>();
            addBuiltInSensors(sensors, allSensors);
            addExternalSensors(sensors, allSensors);

            setPairedAndStartScanning(allSensors, clearSensorCache, sr);
          }

          private void addBuiltInSensors(
              ExperimentSensors sensors, List<ConnectableSensor> allSensors) {
            for (String sensorId : sr.getBuiltInSources()) {
              allSensors.add(
                  connector.builtIn(
                      sensorId, !sensors.getExcludedInternalSensorIds().contains(sensorId)));
            }
          }

          private void addExternalSensors(
              ExperimentSensors sensors, List<ConnectableSensor> allSensors) {
            for (ConnectableSensor sensor : sensors.getExternalSensors()) {
              boolean isExternal = sensor.getSpec() != null;
              if (isExternal) {
                allSensors.add(sensor);
              }
            }
          }
        });
  }

  private void setPairedAndStartScanning(
      List<ConnectableSensor> sensors, boolean clearSensorCache, final SensorRegistry sr) {
    boolean atLeastOneWasPaired = setPairedSensors(sensors);

    // If we somehow had zero sensors when the experiment was loaded, add an
    // arbitrary sensor (Currently, we can get here by someone choosing to
    // "forget" the only device that had paired sensors, but this is also
    // to add robustness against future unintentional exploits.)
    if (!atLeastOneWasPaired && !sensors.isEmpty()) {
      addSensorToCurrentExperiment(
          sensors.get(0),
          new LoggingConsumer<ConnectableSensor>(TAG, "Adding backup sensor") {
            @Override
            public void success(ConnectableSensor value) {
              refresh(false, sr);
            }
          });
    }

    startScanningInDiscoverers(clearSensorCache);
  }

  // TODO: clear available sensors that are not seen on subsequent scans (b/31644042)

  public void showSensorOptions(String uiSensorKey) {
    presenter.showSensorOptions(
        experimentId,
        getSensor(uiSensorKey).getConnectedSensorId(),
        settingsIntents.get(uiSensorKey));
  }

  public boolean isPaired(String uiSensorKey) {
    return getSensor(uiSensorKey).isPaired();
  }

  public void startScanningInDiscoverers(boolean clearDeviceCache) {
    if (scanning) {
      return;
    }
    final long timeout = clearDeviceCache ? 0 : ASSUME_GONE_TIMEOUT_MILLIS;
    final Set<String> keysSeen = new HashSet<>();
    final String[] discovererTaskIds = discoverers.keySet().toArray(new String[0]);

    final TaskPool pool =
        new TaskPool(
            () -> {
              long nowMillis = clock.getNow();

              for (String key : keysSeen) {
                mostRecentlySeen.put(key, nowMillis);
              }

              Set<Map.Entry<String, Long>> entries = mostRecentlySeen.entrySet();
              Iterator<Map.Entry<String, Long>> iter = entries.iterator();
              while (iter.hasNext()) {
                Map.Entry<String, Long> entry = iter.next();
                if (nowMillis - entry.getValue() > timeout) {
                  getAvailableGroup().removeSensor(entry.getKey());
                  iter.remove();
                }
              }
            },
            discovererTaskIds);

    for (final Map.Entry<String, SensorDiscoverer> entry : discoverers.entrySet()) {
      SensorDiscoverer discoverer = entry.getValue();
      startScanning(entry.getKey(), discoverer, pool, keysSeen, true);
    }
    presenter.refreshScanningUI();
  }

  private void startScanning(
      final String providerKey,
      SensorDiscoverer discoverer,
      final TaskPool pool,
      final Set<String> keysSeen,
      final boolean startSpinners) {

    usageTracker.trackEvent(
        TrackerConstants.CATEGORY_SENSOR_MANAGEMENT, TrackerConstants.ACTION_SCAN, providerKey, 0);

    SensorDiscoverer.ScanListener listener =
        new SensorDiscoverer.ScanListener() {
          @Override
          public void onSensorFound(SensorDiscoverer.DiscoveredSensor sensor) {
            ConnectableSensorRegistry.this.onSensorFound(sensor, keysSeen);
          }

          @Override
          public void onServiceFound(SensorDiscoverer.DiscoveredService service) {
            getAvailableGroup().addAvailableService(providerKey, service, startSpinners);
          }

          @Override
          public void onServiceScanComplete(String serviceId) {
            getAvailableGroup().onServiceScanComplete(serviceId);
          }

          @Override
          public void onDeviceFound(SensorDiscoverer.DiscoveredDevice device) {
            getAvailableGroup().addAvailableDevice(device);
            if (deviceRegistry != null) {
              deviceRegistry.addDevice(device.getSpec());
            }
          }

          @Override
          public void onScanDone() {
            pool.taskDone(providerKey);
          }
        };
    if (discoverer.startScanning(
        listener, LoggingConsumer.expectSuccess(TAG, "Discovering sensors"))) {
      onScanStarted();
    }
  }

  /** Scan has started; starts a 10-second timer that will stop scanning. */
  private void onScanStarted() {
    if (!scanning) {
      scanning = true;
      scanCount++;
      final int thisScanCount = scanCount;
      timeoutRunnable =
          () -> {
            if (scanCount == thisScanCount) {
              scanning = false;
              stopScanningInDiscoverers();
              // TODO: test that this actually happens
              presenter.refreshScanningUI();
            }
          };
      scheduler.schedule(Delay.seconds(10), timeoutRunnable);
    }
  }

  private void onSensorFound(SensorDiscoverer.DiscoveredSensor ds, Set<String> availableKeysSeen) {
    ConnectableSensor sensor = connector.disconnected(ds.getSensorSpec());
    final String sensorKey = findSensorKey(sensor);

    if (sensorKey == null) {
      String newKey = registerSensor(null, sensor, ds.getSettingsInterface());

      // Try first to add the sensor to the paired group, which will only work if the sensor
      // is a new sensor on a device that we already know about, and is already in My Devices.
      if (!getPairedGroup().addAvailableSensor(newKey, sensor)) {
        // If that doesn't work, this is a new available sensor.
        getAvailableGroup().addSensor(newKey, sensor);
        availableKeysSeen.add(newKey);
      } else {
        getAvailableGroup().onSensorAddedElsewhere(newKey, sensor);
      }
    } else {
      sensor = sensors.get(sensorKey);
      if (getPairedGroup().addAvailableSensor(sensorKey, sensor)) {
        replaceSensorDataDuringScan(sensorKey, sensor, ds);
        return;
      }
      if (!sensor.isPaired()) {
        availableKeysSeen.add(sensorKey);
        if (!getAvailableGroup().hasSensorKey(sensorKey)) {
          registerSensor(sensorKey, sensor, ds.getSettingsInterface());
          getAvailableGroup().addSensor(sensorKey, sensor);
        }
      } else {
        // TODO: can this ever happen?
        replaceSensorDataDuringScan(sensorKey, sensor, ds);
      }
    }
  }

  private void replaceSensorDataDuringScan(
      final String sensorKey,
      ConnectableSensor oldSensor,
      final SensorDiscoverer.DiscoveredSensor newSensor) {
    settingsIntents.put(sensorKey, newSensor.getSettingsInterface());

    // TODO: can we avoid translating here?
    ExternalSensorSpec newSpec =
        ExternalSensorSpec.fromGoosciSpec(newSensor.getSensorSpec(), providers);
    if (!newSpec.isSameSensorAndSpec(oldSensor.getSpec())
        && newSensor.shouldReplaceStoredSensor(oldSensor)) {
      final String oldSensorId = oldSensor.getConnectedSensorId();
      DeviceOptionsViewController.maybeReplaceSensor(
          dataController,
          experimentId,
          oldSensorId,
          newSpec,
          new LoggingConsumer<String>(TAG, "replacing sensor on scan") {
            @Override
            public void success(String newSensorId) {
              optionsListener.onExperimentSensorReplaced(oldSensorId, newSensorId);
              sensors.put(sensorKey, connector.connected(newSensor.getSensorSpec(), newSensorId));
            }
          });
    }
  }

  public static final boolean isSameSensorAndSpec(
      GoosciSensorSpec.SensorSpec a, GoosciSensorSpec.SensorSpec b) {
    return a.equals(b);
  }

  private String findSensorKey(ConnectableSensor sensor) {
    for (Map.Entry<String, ConnectableSensor> entry : sensors.entrySet()) {
      if (entry.getValue().isSameSensor(sensor)) {
        return entry.getKey();
      }
    }
    return null;
  }

  // TODO: need to get My Devices from database
  public void setMyDevices(InputDeviceSpec... deviceSpecs) {
    for (InputDeviceSpec deviceSpec : deviceSpecs) {
      deviceRegistry.addDevice(deviceSpec);
    }
  }

  /** @return true if at least one of {@code sensors} is paired */
  public boolean setPairedSensors(final List<ConnectableSensor> sensors) {
    if (presenter.isDestroyed()) {
      return false;
    }

    boolean atLeastOneWasPaired = false;

    for (ConnectableSensor newSensor : sensors) {
      String sensorKey = findSensorKey(newSensor);
      if (sensorKey != null) {
        if (getAvailableGroup().removeSensor(sensorKey)) {
          getPairedGroup().addSensor(sensorKey, newSensor);
        } else {
          getPairedGroup().replaceSensor(sensorKey, newSensor);
        }
      } else {
        sensorKey = registerSensor(null, newSensor, null);
        getPairedGroup().addSensor(sensorKey, newSensor);
      }
      // TODO(saff): test that this happens?
      this.sensors.put(sensorKey, newSensor);
      if (newSensor.isPaired()) {
        atLeastOneWasPaired = true;
      }
    }

    removeMissingPairedSensors(ConnectableSensor.makeMap(sensors));
    return atLeastOneWasPaired;
  }

  private void removeMissingPairedSensors(Map<String, ExternalSensorSpec> sensors) {
    for (String sensorKey : this.sensors.keySet()) {
      ConnectableSensor sensor = this.sensors.get(sensorKey);
      if (sensor.isPaired() && !sensors.containsKey(sensor.getConnectedSensorId())) {
        this.sensors.put(sensorKey, connector.asDisconnected(sensor));
        getPairedGroup().removeSensor(sensorKey);
      }
    }
  }

  @NonNull
  private String registerSensor(
      String key, ConnectableSensor sensor, SensorDiscoverer.SettingsInterface settingsInterface) {
    if (key == null) {
      key = EXTERNAL_SENSOR_KEY_PREFIX + (keyNum++);
    }
    sensors.put(key, sensor);
    settingsIntents.put(key, settingsInterface);
    return key;
  }

  /**
   * Pairs to the sensor represented by the given preference, and adds it to the given experiment
   *
   * @param numPairedBeforeThis how many paired sensors there were in this experiment before this
   *     one was added
   * @param onAdded receives the connected ConnectableSensor that's been added to the
   */
  public void addSensorIfNecessary(
      String key, int numPairedBeforeThis, final MaybeConsumer<ConnectableSensor> onAdded) {
    ConnectableSensor connectableSensor = getSensor(key);

    // TODO: probably shouldn't finish in these cases, instead go into sensor editing.

    // TODO: work with SensorSpec instead
    ExternalSensorSpec spec = connectableSensor.getSpec();
    if (spec != null) {
      // The paired spec will be stored in the database, and may contain modified/added
      // information that wasn't supplied by the Discoverer.
      final ExternalSensorSpec pairedSpec = spec.maybeAdjustBeforePairing(numPairedBeforeThis);
      dataController.addOrGetExternalSensor(
          pairedSpec,
          MaybeConsumers.chainFailure(
              onAdded,
              new Consumer<String>() {
                @Override
                public void take(final String sensorId) {
                  addSensorToCurrentExperiment(
                      connector.connected(pairedSpec.asGoosciSpec(), sensorId), onAdded);
                }
              }));
    } else {
      addSensorToCurrentExperiment(connectableSensor, onAdded);
    }
  }

  private void addSensorToCurrentExperiment(
      final ConnectableSensor sensor, final MaybeConsumer<ConnectableSensor> onAdded) {
    dataController.addSensorToExperiment(
        experimentId,
        sensor.getConnectedSensorId(),
        new LoggingConsumer<Success>(TAG, "add sensor to experiment") {
          @Override
          public void success(Success value) {
            sensor.setPaired(true);
            onAdded.success(sensor);
          }
        });
  }

  @NonNull
  private ConnectableSensor getSensor(String uiSensorKey) {
    ConnectableSensor sensor = sensors.get(uiSensorKey);
    if (sensor == null) {
      throw new IllegalArgumentException("No sensor found for key " + uiSensorKey);
    }
    return sensor;
  }

  public void stopScanningInDiscoverers() {
    for (SensorDiscoverer discoverer : discoverers.values()) {
      discoverer.stopScanning();
    }
    if (timeoutRunnable != null) {
      scheduler.unschedule(timeoutRunnable);
      timeoutRunnable = null;
    }
    scanning = false;
    presenter.refreshScanningUI();
  }

  public boolean isScanning() {
    return scanning;
  }

  /** @return true if anything is changed. */
  public void setExperimentId(String experimentId, SensorRegistry sr) {
    this.experimentId = experimentId;
    refresh(false, sr);
  }

  private SensorGroup getPairedGroup() {
    return presenter.getPairedSensorGroup();
  }

  private SensorGroup getAvailableGroup() {
    return presenter.getAvailableSensorGroup();
  }

  public void unpair(String sensorKey) {
    ConnectableSensor sensor = getSensor(sensorKey);
    getPairedGroup().replaceSensor(sensorKey, connector.asDisconnected(sensor));
    presenter.unpair(experimentId, sensor.getConnectedSensorId());
  }

  public void forgetMyDevice(
      final InputDeviceSpec spec,
      final SensorRegistry sr,
      EnablementController enablementController) {
    Preconditions.checkNotNull(sr);
    List<String> idsToUnpair = Lists.newArrayList();
    for (Map.Entry<String, ConnectableSensor> entry : sensors.entrySet()) {
      ConnectableSensor sensor = entry.getValue();
      if (deviceRegistry.getDevice(sensor.getSpec()).isSameSensor(spec)) {
        idsToUnpair.add(sensor.getConnectedSensorId());
        enablementController.setChecked(entry.getKey(), false);
      }
    }
    removeSensorsFromExperiment(
        idsToUnpair,
        new LoggingConsumer<Success>(TAG, "removing sensors") {
          @Override
          public void success(Success value) {
            dataController.forgetMyDevice(
                spec,
                new LoggingConsumer<Success>(TAG, "Forgetting device") {
                  @Override
                  public void success(Success success) {
                    refresh(false, sr);
                  }
                });
          }
        });
  }

  private void removeSensorsFromExperiment(
      final List<String> idsToUnpair, final LoggingConsumer<Success> onSuccess) {
    if (idsToUnpair.isEmpty()) {
      onSuccess.success(Success.SUCCESS);
      return;
    }
    String nextId = idsToUnpair.remove(0);
    dataController.eraseSensorFromExperiment(
        experimentId,
        nextId,
        MaybeConsumers.chainFailure(
            onSuccess,
            new Consumer<Success>() {
              @Override
              public void take(Success success) {
                removeSensorsFromExperiment(idsToUnpair, onSuccess);
              }
            }));
  }

  /**
   * Pair to a new device.
   *
   * @param sensorKeys the known sensors on this device. If there's only one, we assume that the
   *     user wants to add it to the current experiment, so we do so. If there's >1, then we add it
   *     and display it expanded so the user can choose.
   */
  public void addMyDevice(
      InputDeviceSpec spec, final SensorRegistry sr, final List<String> sensorKeys) {
    dataController.addMyDevice(
        spec,
        new LoggingConsumer<Success>(TAG, "Forgetting device") {
          @Override
          public void success(Success success) {
            if (sensorKeys.size() == 1) {
              pair(sensorKeys.get(0));
            } else {
              final int length = sensorKeys.size();
              final int[] counter = {0};
              for (String key : sensorKeys) {
                addSensorIfNecessary(
                    key,
                    getPairedGroup().getSensorCount(),
                    new LoggingConsumer<ConnectableSensor>(TAG, "add sensor to experiment") {
                      @Override
                      public void success(ConnectableSensor sensor) {
                        dataController.removeSensorFromExperiment(
                            experimentId,
                            sensor.getConnectedSensorId(),
                            new LoggingConsumer<Success>(TAG, "remove sensor from experiment") {
                              @Override
                              public void success(Success value) {
                                sensor.setPaired(false);
                                counter[0]++;
                                if (counter[0] == length) {
                                  refresh(false, sr);
                                }
                              }
                            });
                      }
                    });
              }
            }
            refresh(false, sr);
          }
        });
  }

  public boolean hasOptions(String sensorKey) {
    return settingsIntents.containsKey(sensorKey) && settingsIntents.get(sensorKey) != null;
  }

  public void reloadProvider(String providerKey, boolean startSpinners) {
    SensorDiscoverer discoverer = discoverers.get(providerKey);
    if (discoverer == null) {
      throw new IllegalArgumentException("Couldn't find " + providerKey + " in " + discoverers);
    }
    startScanning(
        providerKey,
        discoverer,
        new TaskPool(Runnables.doNothing()),
        new HashSet<String>(),
        startSpinners);
  }
}
