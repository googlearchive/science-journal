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
import android.util.ArrayMap;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.TaskPool;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Runnables;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers sensors that have been found during scanning, and can expose them by adding them
 * to a PreferenceCategory.
 */
public class ConnectableSensorRegistry {
    public static final String TAG = "ConSensorRegistry";

    // Don't remove a sensor unless it's been gone 15 seconds
    private static final long ASSUME_GONE_TIMEOUT_MILLIS = 15_000;
    private static final String EXTERNAL_SENSOR_KEY_PREFIX = "sensorKey";

    private final DataController mDataController;
    private final Map<String, ExternalSensorDiscoverer> mDiscoverers;

    private final DevicesPresenter mPresenter;
    private final Map<String, ConnectableSensor> mSensors = new ArrayMap<>();
    private final Map<String, ExternalSensorDiscoverer.SettingsInterface>
            mSettingsIntents = new ArrayMap<>();
    private final Scheduler mScheduler;
    private boolean mScanning = false;
    private int mScanCount = 0;

    // Maps from sensorKey to the timestamp (from mClock) of the last time it showed up in a scan.
    private Map<String, Long> mMostRecentlySeen = new ArrayMap<>();

    private int mKeyNum = 0;
    private String mExperimentId = null;
    private Clock mClock;
    private DeviceOptionsDialog.DeviceOptionsListener mOptionsListener;
    private DeviceRegistry mDeviceRegistry;
    private final SensorAppearanceProvider mAppearanceProvider;
    private Runnable mTimeoutRunnable;

    // TODO: reduce parameter list?
    public ConnectableSensorRegistry(DataController dataController,
            Map<String, ExternalSensorDiscoverer> discoverers, DevicesPresenter presenter,
            Scheduler scheduler, Clock clock,
            DeviceOptionsDialog.DeviceOptionsListener optionsListener,
            DeviceRegistry deviceRegistry, SensorAppearanceProvider appearanceProvider) {
        mDataController = dataController;
        mDiscoverers = discoverers;
        mPresenter = presenter;
        mScheduler = scheduler;
        mClock = clock;
        mOptionsListener = optionsListener;
        mDeviceRegistry = deviceRegistry;
        mAppearanceProvider = appearanceProvider;
    }

    public void pair(final String sensorKey) {
        final ExternalSensorDiscoverer.SettingsInterface settings = mSettingsIntents.get(sensorKey);
        addSensorIfNecessary(sensorKey, getPairedGroup().getSensorCount(),
                new LoggingConsumer<ConnectableSensor>(TAG, "Add external sensor") {
                    @Override
                    public void success(final ConnectableSensor sensor) {
                        getPairedGroup().replaceSensor(sensorKey, sensor);
                        mAppearanceProvider.loadAppearances(
                                new LoggingConsumer<Success>(TAG, "Load appearance") {
                                    @Override
                                    public void success(Success value) {
                                        if (sensor.shouldShowOptionsOnConnect()
                                                && settings != null) {
                                            mPresenter.showSensorOptions(mExperimentId,
                                                    sensor.getConnectedSensorId(), settings);
                                        }
                                    }
                                });
                    }
                });
    }

    public void refresh(final boolean clearSensorCache, final SensorRegistry sr) {
        stopScanningInDiscoverers();
        mDataController.getMyDevices(
                new LoggingConsumer<List<InputDeviceSpec>>(TAG, "Load my devices") {
                    @Override
                    public void success(List<InputDeviceSpec> myDevices) {
                        onMyDevicesLoaded(myDevices, clearSensorCache, sr);
                    }
                });
    }

    private void onMyDevicesLoaded(List<InputDeviceSpec> myDevices, final boolean clearSensorCache,
            final SensorRegistry sr) {
        for (final InputDeviceSpec device : myDevices) {
            mDeviceRegistry.addDevice(device);
        }
        // Paired group needs to know My Devices so it can show them.
        getPairedGroup().setMyDevices(myDevices);

        // Available group needs to know My Devices so it can _not_ show them
        getAvailableGroup().setMyDevices(myDevices);

        mDataController.getExternalSensorsByExperiment(mExperimentId,
                new LoggingConsumer<ExperimentSensors>(TAG, "Load external sensors") {
                    @Override
                    public void success(ExperimentSensors sensors) {
                        List<ConnectableSensor> allSensors = new ArrayList<>();
                        addBuiltInSensors(sensors, allSensors);
                        addExternalSensors(sensors, allSensors);
                        setPairedAndStartScanning(allSensors, clearSensorCache);
                    }

                    private void addBuiltInSensors(ExperimentSensors sensors,
                            List<ConnectableSensor> allSensors) {
                        for (String sensorId : sr.getBuiltInSources()) {
                            allSensors.add(ConnectableSensor.builtIn(sensorId,
                                    !sensors.getExcludedSensorIds().contains(sensorId)));
                        }
                    }

                    private void addExternalSensors(ExperimentSensors sensors,
                            List<ConnectableSensor> allSensors) {
                        for (ConnectableSensor sensor : sensors.getIncludedSensors()) {
                            boolean isExternal = sensor.getSpec() != null;
                            if (isExternal) {
                                allSensors.add(sensor);
                            }
                        }
                    }
                });
    }

    private void setPairedAndStartScanning(List<ConnectableSensor> sensors,
            boolean clearSensorCache) {
        setPairedSensors(sensors);
        startScanningInDiscoverers(clearSensorCache);
    }

    // TODO: clear available sensors that are not seen on subsequent scans (b/31644042)

    public void showSensorOptions(String uiSensorKey) {
        mPresenter.showSensorOptions(mExperimentId, getSensor(uiSensorKey).getConnectedSensorId(),
                mSettingsIntents.get(uiSensorKey));
    }

    public boolean isPaired(String uiSensorKey) {
        return getSensor(uiSensorKey).isPaired();
    }

    public void startScanningInDiscoverers(boolean clearDeviceCache) {
        if (mScanning) {
            return;
        }
        final long timeout = clearDeviceCache ? 0 : ASSUME_GONE_TIMEOUT_MILLIS;
        final Set<String> keysSeen = new HashSet<>();


        final TaskPool pool = new TaskPool(new Runnable() {
            @Override
            public void run() {
                long nowMillis = mClock.getNow();

                for (String key : keysSeen) {
                    mMostRecentlySeen.put(key, nowMillis);
                }

                Set<Map.Entry<String, Long>> entries = mMostRecentlySeen.entrySet();
                Iterator<Map.Entry<String, Long>> iter = entries.iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, Long> entry = iter.next();
                    if (nowMillis - entry.getValue() > timeout) {
                        getAvailableGroup().removeSensor(entry.getKey());
                        iter.remove();
                    }
                }
            }
        });

        for (final Map.Entry<String, ExternalSensorDiscoverer> entry : mDiscoverers.entrySet()) {
            ExternalSensorDiscoverer discoverer = entry.getValue();
            startScanning(entry.getKey(), discoverer, pool, keysSeen, true);
        }
        mPresenter.refreshScanningUI();
    }

    private void startScanning(final String providerKey, ExternalSensorDiscoverer discoverer,
            final TaskPool pool, final Set<String> keysSeen, final boolean startSpinners) {
        ExternalSensorProvider provider = discoverer.getProvider();
        final String providerId = provider.getProviderId();
        pool.addTask(providerId);
        ExternalSensorDiscoverer.ScanListener listener =
                new ExternalSensorDiscoverer.ScanListener() {
                    @Override
                    public void onSensorFound(ExternalSensorDiscoverer.DiscoveredSensor sensor) {
                        ConnectableSensorRegistry.this.onSensorFound(sensor, keysSeen);
                    }

                    @Override
                    public void onServiceFound(ExternalSensorDiscoverer.DiscoveredService service) {
                        getAvailableGroup().addAvailableService(providerKey, service,
                                startSpinners);
                    }

                    @Override
                    public void onServiceScanComplete(String serviceId) {
                        getAvailableGroup().onServiceScanComplete(serviceId);
                    }

                    @Override
                    public void onDeviceFound(ExternalSensorDiscoverer.DiscoveredDevice device) {
                        getAvailableGroup().addAvailableDevice(device);
                        if (mDeviceRegistry != null) {
                            mDeviceRegistry.addDevice(device.getSpec());
                        }
                    }

                    @Override
                    public void onScanDone() {
                        pool.taskDone(providerId);
                    }
                };
        if (discoverer.startScanning(listener,
                LoggingConsumer.expectSuccess(TAG, "Discovering sensors"))) {
            onScanStarted();
        }
    }

    /**
     * Scan has started; starts a 10-second timer that will stop scanning.
     */
    private void onScanStarted() {
        if (!mScanning) {
            mScanning = true;
            mScanCount++;
            final int thisScanCount = mScanCount;
            mTimeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mScanCount == thisScanCount) {
                        mScanning = false;
                        stopScanningInDiscoverers();
                        // TODO: test that this actually happens
                        mPresenter.refreshScanningUI();
                    }
                }
            };
            mScheduler.schedule(Delay.seconds(10), mTimeoutRunnable);
        }
    }

    private void onSensorFound(ExternalSensorDiscoverer.DiscoveredSensor ds,
            Set<String> availableKeysSeen) {
        ConnectableSensor sensor = ConnectableSensor.disconnected(ds.getSpec());
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
            sensor = mSensors.get(sensorKey);
            if (getPairedGroup().addAvailableSensor(sensorKey, sensor)) {
                replaceSensor(sensorKey, sensor, ds);
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
                replaceSensor(sensorKey, sensor, ds);
            }
        }
    }

    private void replaceSensor(final String sensorKey, ConnectableSensor oldSensor,
            final ExternalSensorDiscoverer.DiscoveredSensor newSensor) {
        mSettingsIntents.put(sensorKey, newSensor.getSettingsInterface());
        if (!newSensor.getSpec().isSameSensorAndSpec(oldSensor.getSpec())) {
            final String oldSensorId = oldSensor.getConnectedSensorId();
            DeviceOptionsViewController.maybeReplaceSensor(mDataController, mExperimentId,
                    oldSensorId, newSensor.getSpec(),
                    new LoggingConsumer<String>(TAG, "replacing sensor on scan") {
                        @Override
                        public void success(String newSensorId) {
                            mOptionsListener.onExperimentSensorReplaced(oldSensorId,
                                    newSensorId);
                            mSensors.put(sensorKey,
                                    ConnectableSensor.connected(newSensor.getSpec(), newSensorId));
                        }
                    });
        }
    }

    private String findSensorKey(ConnectableSensor sensor) {
        for (Map.Entry<String, ConnectableSensor> entry : mSensors.entrySet()) {
            if (entry.getValue().isSameSensor(sensor)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // TODO: need to get My Devices from database
    public void setMyDevices(InputDeviceSpec... deviceSpecs) {
        for (InputDeviceSpec deviceSpec : deviceSpecs) {
            mDeviceRegistry.addDevice(deviceSpec);
        }
    }

    public void setPairedSensors(final List<ConnectableSensor> sensors) {
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
            mSensors.put(sensorKey, newSensor);

        }

        removeMissingPairedSensors(ConnectableSensor.makeMap(sensors));
    }


    private void removeMissingPairedSensors(Map<String, ExternalSensorSpec> sensors) {
        for (String sensorKey : mSensors.keySet()) {
            ConnectableSensor sensor = mSensors.get(sensorKey);
            if (sensor.isPaired() && !sensors.containsKey(sensor.getConnectedSensorId())) {
                mSensors.put(sensorKey, ConnectableSensor.disconnected(sensor.getSpec()));
                getPairedGroup().removeSensor(sensorKey);
            }
        }
    }

    @NonNull
    private String registerSensor(String key, ConnectableSensor sensor,
            ExternalSensorDiscoverer.SettingsInterface settingsInterface) {
        if (key == null) {
            key = EXTERNAL_SENSOR_KEY_PREFIX + (mKeyNum++);
        }
        mSensors.put(key, sensor);
        mSettingsIntents.put(key, settingsInterface);
        return key;
    }

    /**
     * Pairs to the sensor represented by the given preference, and adds it to the given experiment
     *
     * @param numPairedBeforeThis how many paired sensors there were in this experiment before
     *                            this one was added
     * @param onAdded             receives the connected ConnectableSensor that's been added to the
     */
    public void addSensorIfNecessary(String key, int numPairedBeforeThis,
            final MaybeConsumer<ConnectableSensor> onAdded) {
        ConnectableSensor connectableSensor = getSensor(key);

        // TODO: probably shouldn't finish in these cases, instead go into sensor editing.

        ExternalSensorSpec spec = connectableSensor.getSpec();
        if (spec != null) {
            // The paired spec will be stored in the database, and may contain modified/added
            // information that wasn't supplied by the Discoverer.
            final ExternalSensorSpec pairedSpec =
                    spec.maybeAdjustBeforePairing(numPairedBeforeThis);
            mDataController.addOrGetExternalSensor(pairedSpec, MaybeConsumers.chainFailure(onAdded,
                    new Consumer<String>() {
                        @Override
                        public void take(final String sensorId) {
                            addSensorToCurrentExperiment(
                                    ConnectableSensor.connected(pairedSpec, sensorId), onAdded);
                        }
                    }));
        } else {
            addSensorToCurrentExperiment(connectableSensor, onAdded);
        }
    }

    private void addSensorToCurrentExperiment(final ConnectableSensor sensor,
            final MaybeConsumer<ConnectableSensor> onAdded) {
        mDataController.addSensorToExperiment(mExperimentId, sensor.getConnectedSensorId(),
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
        ConnectableSensor sensor = mSensors.get(uiSensorKey);
        if (sensor == null) {
            throw new IllegalArgumentException("No sensor found for key " + uiSensorKey);
        }
        return sensor;
    }

    public void stopScanningInDiscoverers() {
        for (ExternalSensorDiscoverer discoverer : mDiscoverers.values()) {
            discoverer.stopScanning();
        }
        if (mTimeoutRunnable != null) {
            mScheduler.unschedule(mTimeoutRunnable);
            mTimeoutRunnable = null;
        }
        mScanning = false;
        mPresenter.refreshScanningUI();
    }

    public boolean isScanning() {
        return mScanning;
    }

    /**
     * @return true if anything is changed.
     */
    public void setExperimentId(String experimentId, SensorRegistry sr) {
        mExperimentId = experimentId;
        refresh(false, sr);
    }

    private SensorGroup getPairedGroup() {
        return mPresenter.getPairedSensorGroup();
    }

    private SensorGroup getAvailableGroup() {
        return mPresenter.getAvailableSensorGroup();
    }

    public void unpair(String sensorKey) {
        ConnectableSensor sensor = getSensor(sensorKey);
        getPairedGroup().replaceSensor(sensorKey, sensor.asDisconnected());
        mPresenter.unpair(mExperimentId, sensor.getConnectedSensorId());
    }

    public void forgetMyDevice(final InputDeviceSpec spec, final SensorRegistry sr) {
        List<String> idsToUnpair = Lists.newArrayList();
        for (ConnectableSensor sensor : mSensors.values()) {
            if (mDeviceRegistry.getDevice(sensor.getSpec()).isSameSensor(spec)) {
                idsToUnpair.add(sensor.getConnectedSensorId());
            }
        }
        removeSensorsFromExperiment(idsToUnpair,
                new LoggingConsumer<Success>(TAG, "removing sensors") {
                    @Override
                    public void success(Success value) {
                        mDataController.forgetMyDevice(spec,
                                new LoggingConsumer<Success>(TAG, "Forgetting device") {
                                    @Override
                                    public void success(Success success) {
                                        refresh(false, sr);
                                    }
                                });
                    }
                });
    }

    private void removeSensorsFromExperiment(final List<String> idsToUnpair,
            final LoggingConsumer<Success> onSuccess) {
        if (idsToUnpair.isEmpty()) {
            onSuccess.success(Success.SUCCESS);
            return;
        }
        String nextId = idsToUnpair.remove(0);
        mDataController.removeSensorFromExperiment(mExperimentId, nextId,
                MaybeConsumers.chainFailure(onSuccess, new Consumer<Success>() {
                    @Override
                    public void take(Success success) {
                        removeSensorsFromExperiment(idsToUnpair, onSuccess);
                    }
                }));
    }

    /**
     * Pair to a new device.
     *
     * @param sensorKeys the known sensors on this device.  If there's only one, we assume that the
     *                   user wants to add it to the current experiment, so we do so.  If there's
     *                   >1, then we add it and display it expanded so the user can choose.
     */
    public void addMyDevice(InputDeviceSpec spec, final SensorRegistry sr,
            final List<String> sensorKeys) {
        mDataController.addMyDevice(spec,
                new LoggingConsumer<Success>(TAG, "Forgetting device") {
                    @Override
                    public void success(Success success) {
                        if (sensorKeys.size() == 1) {
                            pair(sensorKeys.get(0));
                        }
                        refresh(false, sr);
                    }
                });
    }

    public boolean hasOptions(String sensorKey) {
        return mSettingsIntents.containsKey(sensorKey) && mSettingsIntents.get(sensorKey) != null;
    }

    public void reloadProvider(String providerKey, boolean startSpinners) {
        ExternalSensorDiscoverer discoverer = mDiscoverers.get(providerKey);
        if (discoverer == null) {
            throw new IllegalArgumentException(
                    "Couldn't find " + providerKey + " in " + mDiscoverers);
        }
        startScanning(providerKey, discoverer, new TaskPool(Runnables.doNothing()),
                new HashSet<String>(), startSpinners);
    }
}
