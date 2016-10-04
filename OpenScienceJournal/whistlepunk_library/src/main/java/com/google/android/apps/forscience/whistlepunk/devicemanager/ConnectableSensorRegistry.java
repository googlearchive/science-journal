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
import android.support.annotation.NonNull;
import android.util.ArrayMap;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensors.SystemScheduler;

import java.util.Map;

/**
 * Remembers sensors that have been found during scanning, and can expose them by adding them
 * to a PreferenceCategory.
 */
public class ConnectableSensorRegistry {
    private static final String TAG = "ConSensorRegistry";

    private final DataController mDataController;
    private final Map<String, ExternalSensorDiscoverer> mDiscoverers;
    private final Map<String, ConnectableSensor> mSensors = new ArrayMap<>();
    private final Map<String, PendingIntent> mSettingsIntents = new ArrayMap<>();
    private final Scheduler mScheduler;
    private boolean mScanning = false;
    private int mScanCount = 0;

    private int mKeyNum = 0;

    public ConnectableSensorRegistry(DataController dataController,
            Map<String, ExternalSensorDiscoverer> discoverers) {
        mDataController = dataController;
        mDiscoverers = discoverers;
        mScheduler = new SystemScheduler();
    }

    // TODO: clear available sensors that are not seen on subsequent scans (b/31644042)

    public void showDeviceOptions(DeviceOptionsPresenter presenter, String experimentId,
            String uiSensorKey, PendingIntent settingsIntent) {
        presenter.showDeviceOptions(experimentId, getSensor(uiSensorKey).getConnectedSensorId(),
                settingsIntent);
    }

    public boolean isPaired(String uiSensorKey) {
        return getSensor(uiSensorKey).isPaired();
    }

    public void startScanningInDiscoverers(final SensorGroup available, Context context,
            DeviceOptionsPresenter presenter) {
        if (mScanning) {
            return;
        }
        Consumer<ExternalSensorDiscoverer.DiscoveredSensor> onEachSensorFound =
                new Consumer<ExternalSensorDiscoverer.DiscoveredSensor>() {
                    @Override
                    public void take(ExternalSensorDiscoverer.DiscoveredSensor ds) {
                        onSensorFound(ds, available);
                    }
                };
        for (ExternalSensorDiscoverer discoverer : mDiscoverers.values()) {
            Runnable onScanDone = new Runnable() {
                @Override
                public void run() {
                    // TODO: remove sensors that we had marked available, but didn't find this time.

                }
            };
            if (discoverer.startScanning(onEachSensorFound, onScanDone,
                    LoggingConsumer.expectSuccess(TAG, "Discovering sensors"), context)) {
                onScanStarted(presenter);
            }
        }
    }

    /**
     * Scan has started; starts a 10-second timer that will stop scanning.
     */
    private void onScanStarted(final DeviceOptionsPresenter presenter) {
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
                        presenter.refreshScanningUI();
                    }
                }
            });
        }
    }

    private void onSensorFound(ExternalSensorDiscoverer.DiscoveredSensor ds,
            SensorGroup sensorGroup) {
        String sensorKey = findSensorKey(ds.getSpec());

        if (sensorKey == null) {
            ConnectableSensor sensor = ConnectableSensor.disconnected(ds.getSpec());
            String key = registerSensor(null, sensor, ds.getSettingsIntent());
            sensorGroup.addSensor(key, sensor);
        } else {
            ConnectableSensor sensor = mSensors.get(sensorKey);
            if (!sensor.isPaired()) {
                if (!sensorGroup.hasSensorKey(sensorKey)) {
                    registerSensor(sensorKey, sensor, ds.getSettingsIntent());
                    sensorGroup.addSensor(sensorKey, sensor);
                }
            } else {
                // TODO: UI feedback
                mSettingsIntents.put(sensorKey, ds.getSettingsIntent());
            }
        }
    }

    private String findSensorKey(ExternalSensorSpec spec) {
        for (Map.Entry<String, ConnectableSensor> entry : mSensors.entrySet()) {
            if (entry.getValue().getSpec().isSameSensor(spec)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void setPairedSensors(SensorGroup available, SensorGroup paired,
            Map<String, ExternalSensorSpec> sensors) {
        for (Map.Entry<String, ExternalSensorSpec> entry : sensors.entrySet()) {
            ExternalSensorSpec sensor = entry.getValue();
            removeSensorWithSpec(available, sensor);

            ConnectableSensor newSensor = ConnectableSensor.connected(sensor, entry.getKey());
            String key = registerSensor(null, newSensor, null);
            paired.addSensor(key, newSensor);
        }

        removeMissingPairedSensors(sensors, paired);
    }


    private void removeMissingPairedSensors(Map<String, ExternalSensorSpec> sensors,
            SensorGroup paired) {
        for (String sensorKey : mSensors.keySet()) {
            ConnectableSensor sensor = mSensors.get(sensorKey);
            if (sensor.isPaired() && !sensors.containsKey(sensor.getConnectedSensorId())) {
                mSensors.put(sensorKey, ConnectableSensor.disconnected(sensor.getSpec()));
                paired.removeSensor(sensorKey);
            }
        }
    }

    private void removeSensorWithSpec(SensorGroup availableDevices, ExternalSensorSpec sensor) {
        String sensorKey = findSensorKey(sensor);
        if (sensorKey != null) {
            availableDevices.removeSensor(sensorKey);
            mSensors.remove(sensorKey);
            mSettingsIntents.remove(sensorKey);
        }
    }

    @NonNull
    private String registerSensor(String key, ConnectableSensor sensor,
            PendingIntent settingsIntent) {
        if (key == null) {
            key = "sensorKey" + (mKeyNum++);
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
    public void addExternalSensorIfNecessary(final String experimentId, String key,
            int numPairedBeforeThis, final MaybeConsumer<ConnectableSensor> onAdded) {
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
                        mDataController.addSensorToExperiment(experimentId, sensorId,
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
    }

    public PendingIntent getSettingsIntentFromKey(String key) {
        return mSettingsIntents.get(key);
    }

    public boolean isScanning() {
        return mScanning;
    }
}
