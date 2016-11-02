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
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.TaskPool;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

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
    private final Map<String, PendingIntent> mSettingsIntents = new ArrayMap<>();
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

    // TODO: reduce parameter list?
    public ConnectableSensorRegistry(DataController dataController,
            Map<String, ExternalSensorDiscoverer> discoverers, DevicesPresenter presenter,
            Scheduler scheduler, Clock clock,
            DeviceOptionsDialog.DeviceOptionsListener optionsListener,
            DeviceRegistry deviceRegistry) {
        mDataController = dataController;
        mDiscoverers = discoverers;
        mPresenter = presenter;
        mScheduler = scheduler;
        mClock = clock;
        mOptionsListener = optionsListener;
        mDeviceRegistry = deviceRegistry;
    }

    public void pair(String sensorKey, final SensorAppearanceProvider appearanceProvider,
            final SensorRegistry sr) {
        final PendingIntent settingsIntent = mSettingsIntents.get(sensorKey);
        addExternalSensorIfNecessary(sensorKey, getPairedGroup().getSensorCount(),
                new LoggingConsumer<ConnectableSensor>(TAG, "Add external sensor") {
                    @Override
                    public void success(final ConnectableSensor sensor) {
                        appearanceProvider.loadAppearances(
                                new LoggingConsumer<Success>(TAG, "Load appearance") {
                                    @Override
                                    public void success(Success value) {
                                        refresh(false, sr);
                                        if (sensor.shouldShowOptionsOnConnect()) {
                                            mPresenter.showDeviceOptions(mExperimentId,
                                                    sensor.getConnectedSensorId(), settingsIntent);
                                        }
                                    }
                                });
                    }
                });
    }

    public void refresh(final boolean clearSensorCache, final SensorRegistry sr) {
        stopScanningInDiscoverers();
        mDataController.getExternalSensorsByExperiment(mExperimentId,
                new LoggingConsumer<List<ConnectableSensor>>(TAG, "Load external sensors") {
                    @Override
                    public void success(List<ConnectableSensor> sensors) {
                        List<ConnectableSensor> allSensors = new ArrayList<>(sensors);
                        for (String sensorId : sr.getBuiltInSources()) {
                            allSensors.add(ConnectableSensor.builtIn(sensorId));
                        }
                        setPairedAndStartScanning(ConnectableSensor.makeMap(allSensors),
                                clearSensorCache);
                    }
                });
    }

    private void setPairedAndStartScanning(Map<String, ExternalSensorSpec> sensors,
            boolean clearSensorCache) {
        setPairedSensors(sensors);
        startScanningInDiscoverers(clearSensorCache);
    }

    // TODO: clear available sensors that are not seen on subsequent scans (b/31644042)

    public void showDeviceOptions(String uiSensorKey) {
        mPresenter.showDeviceOptions(mExperimentId, getSensor(uiSensorKey).getConnectedSensorId(),
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
            final String providerId = discoverer.getProvider().getProviderId();
            pool.addTask(providerId);
            ExternalSensorDiscoverer.ScanListener listener =
                    new ExternalSensorDiscoverer.ScanListener() {
                        @Override
                        public void onSensorFound(
                                ExternalSensorDiscoverer.DiscoveredSensor sensor) {
                            ConnectableSensorRegistry.this.onSensorFound(sensor, keysSeen);
                        }

                        @Override
                        public void onDeviceFound(InputDeviceSpec device) {
                            if (mDeviceRegistry != null) {
                                mDeviceRegistry.addDevice(device);
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
        mPresenter.refreshScanningUI();
    }

    /**
     * Scan has started; starts a 10-second timer that will stop scanning.
     */
    private void onScanStarted() {
        if (!mScanning) {
            mScanning = true;
            mScanCount++;
            final int thisScanCount = mScanCount;
            mScheduler.schedule(Delay.seconds(10), new Runnable() {
                @Override
                public void run() {
                    if (mScanCount == thisScanCount) {
                        mScanning = false;
                        stopScanningInDiscoverers();
                        // TODO: test that this actually happens
                        mPresenter.refreshScanningUI();
                    }
                }
            });
        }
    }

    private void onSensorFound(ExternalSensorDiscoverer.DiscoveredSensor ds,
            Set<String> availableKeysSeen) {
        final ExternalSensorSpec newSpec = ds.getSpec();
        ConnectableSensor sensor = ConnectableSensor.disconnected(newSpec);
        final String sensorKey = findSensorKey(sensor);

        if (sensorKey == null) {
            String key = registerSensor(null, sensor, ds.getSettingsIntent());
            getAvailableGroup().addSensor(key, sensor);
            availableKeysSeen.add(key);
        } else {
            sensor = mSensors.get(sensorKey);
            if (!sensor.isPaired()) {
                availableKeysSeen.add(sensorKey);
                if (!getAvailableGroup().hasSensorKey(sensorKey)) {
                    registerSensor(sensorKey, sensor, ds.getSettingsIntent());
                    getAvailableGroup().addSensor(sensorKey, sensor);
                }
            } else {
                // TODO: UI feedback
                mSettingsIntents.put(sensorKey, ds.getSettingsIntent());
                if (!newSpec.isSameSensorAndSpec(sensor.getSpec())) {
                    final String oldSensorId = sensor.getConnectedSensorId();
                    DeviceOptionsViewController.maybeReplaceSensor(mDataController, mExperimentId,
                            oldSensorId, newSpec,
                            new LoggingConsumer<String>(TAG, "replacing sensor on scan") {
                                @Override
                                public void success(String newSensorId) {
                                    mOptionsListener.onExperimentSensorReplaced(oldSensorId,
                                            newSensorId);
                                    mSensors.put(sensorKey,
                                            ConnectableSensor.connected(newSpec, newSensorId));
                                }
                            });
                }
            }
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

    public void setPairedSensors(final Map<String, ExternalSensorSpec> sensors) {
        for (Map.Entry<String, ExternalSensorSpec> entry : sensors.entrySet()) {
            String sensorId = entry.getKey();
            ExternalSensorSpec sensor = entry.getValue();
            ConnectableSensor newSensor = ConnectableSensor.connected(sensor, sensorId);
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

        removeMissingPairedSensors(sensors);
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
            PendingIntent settingsIntent) {
        if (key == null) {
            key = EXTERNAL_SENSOR_KEY_PREFIX + (mKeyNum++);
        }
        mSensors.put(key, sensor);
        mSettingsIntents.put(key, settingsIntent);
        return key;
    }

    /**
     * Pairs to the sensor represented by the given preference, and adds it to the given experiment
     *
     * @param numPairedBeforeThis how many paired sensors there were in this experiment before
     *                            this one was added
     * @param onAdded             receives the connected ConnectableSensor that's been added to the
     */
    public void addExternalSensorIfNecessary(String key, int numPairedBeforeThis,
            final MaybeConsumer<ConnectableSensor> onAdded) {
        ConnectableSensor connectableSensor = getSensor(key);

        // TODO: probably shouldn't finish in these cases, instead go into sensor editing.

        // The paired spec will be stored in the database, and may contain modified/added
        // information that wasn't supplied by the Discoverer.
        final ExternalSensorSpec pairedSpec =
                connectableSensor.getSpec().maybeAdjustBeforePairing(numPairedBeforeThis);
        mDataController.addOrGetExternalSensor(pairedSpec, MaybeConsumers.chainFailure(onAdded,
                new Consumer<String>() {
                    @Override
                    public void take(final String sensorId) {
                        mDataController.addSensorToExperiment(mExperimentId, sensorId,
                                new LoggingConsumer<Success>(TAG, "add sensor to experiment") {
                                    @Override
                                    public void success(Success value) {
                                        onAdded.success(
                                                ConnectableSensor.connected(pairedSpec, sensorId));
                                    }
                                });
                    }
                }));
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
        mScanning = false;
        mPresenter.refreshScanningUI();
    }

    public boolean isScanning() {
        return mScanning;
    }

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
        mPresenter.unpair(mExperimentId, getSensor(sensorKey).getConnectedSensorId());
    }
}
