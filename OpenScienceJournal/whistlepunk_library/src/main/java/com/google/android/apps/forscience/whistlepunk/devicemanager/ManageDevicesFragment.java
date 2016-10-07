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
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.PreferenceProgressCategory;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.squareup.leakcanary.RefWatcher;

import java.util.Map;

/**
 * Searches for Bluetooth LE devices that are supported.
 */
public class ManageDevicesFragment extends PreferenceFragment implements DevicesPresenter {
    private static final String TAG = "ManageDevices";

    private static final String PREF_KEY_PAIRED_DEVICES = "paired_devices";
    private static final String PREF_KEY_AVAILABLE_DEVICES = "available_devices";

    private PreferenceCategory mPairedDevices;
    private PreferenceProgressCategory mAvailableDevices;
    private SensorPreferenceGroup mPairedGroup;
    private SensorPreferenceGroup mAvailableGroup;
    private Menu mMainMenu;

    private DataController mDataController;

    private String mExperimentId;
    private ConnectableSensorRegistry mConnectableSensorRegistry;

    public ManageDevicesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDataController = AppSingleton.getInstance(getActivity()).getDataController();
        addPreferencesFromResource(R.xml.external_devices);

        mPairedDevices = new PreferenceProgressCategory(getActivity());
        mPairedDevices.setKey(PREF_KEY_PAIRED_DEVICES);
        mPairedDevices.setTitle(R.string.external_devices_paired);
        mPairedDevices.setOrder(0);
        mPairedDevices.setSelectable(false);
        PreferenceScreen screen = getPreferenceScreen();
        mPairedGroup = new SensorPreferenceGroup(screen, mPairedDevices, true, true);

        mAvailableDevices = new PreferenceProgressCategory(getActivity());
        mAvailableDevices.setKey(PREF_KEY_AVAILABLE_DEVICES);
        mAvailableDevices.setTitle(R.string.external_devices_available);
        mAvailableDevices.setOrder(1);
        mAvailableDevices.setSelectable(false);
        mAvailableGroup = new SensorPreferenceGroup(screen, mAvailableDevices, false, false);

        screen.addPreference(mAvailableDevices);
        setHasOptionsMenu(true);

        Map<String, ExternalSensorDiscoverer> discoverers =
                WhistlePunkApplication.getExternalSensorDiscoverers(getActivity());

        mConnectableSensorRegistry = new ConnectableSensorRegistry(mDataController, discoverers);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAfterLoad();
    }

    @Override
    public void onPause() {
        stopScanning();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        stopScanning();
        mMainMenu = null;
        super.onDestroy();

        // Make sure we don't leak this fragment.
        RefWatcher watcher = WhistlePunkApplication.getRefWatcher(getActivity());
        watcher.watch(this);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            final Preference preference) {
        String sensorKey = preference.getKey();
        final PendingIntent settingsIntent = mConnectableSensorRegistry.getSettingsIntentFromKey(
                sensorKey);

        if (sensorKey != null) {
            if (!mConnectableSensorRegistry.isPaired(sensorKey)) {
                preference.setEnabled(false);
                preference.setSummary(R.string.external_devices_pairing);
                mConnectableSensorRegistry.addExternalSensorIfNecessary(mExperimentId, sensorKey,
                        mPairedDevices.getPreferenceCount(),
                        new LoggingConsumer<ConnectableSensor>(TAG, "Add external sensor") {
                            @Override
                            public void success(ConnectableSensor sensor) {
                                reloadAppearancesAndShowOptions(sensor, settingsIntent);
                            }
                        });
            } else {
                mConnectableSensorRegistry.showDeviceOptions(this, mExperimentId,
                        sensorKey, settingsIntent);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_manage_devices, menu);
        super.onCreateOptionsMenu(menu, inflater);
        mMainMenu = menu;
        refreshScanningUI();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void refreshAfterLoad() {
        mExperimentId = getArguments().getString(ManageDevicesActivity.EXTRA_EXPERIMENT_ID);
        refresh();
    }

    private void refresh() {
        stopScanning();
        mDataController.getExternalSensorsByExperiment(mExperimentId,
                new LoggingConsumer<Map<String, ExternalSensorSpec>>(TAG, "Load external sensors") {
                    @Override
                    public void success(Map<String, ExternalSensorSpec> sensors) {
                        if (getActivity() == null) {
                            return;
                        }

                        mConnectableSensorRegistry.setPairedSensors(mAvailableGroup, mPairedGroup,
                                sensors);
                        scanForDevices();
                    }
                });
    }

    private void scanForDevices() {
        mConnectableSensorRegistry.startScanningInDiscoverers(mAvailableDevices.getContext(), this);
        refreshScanningUI();
    }

    private void stopScanning() {
        mConnectableSensorRegistry.stopScanningInDiscoverers();
        refreshScanningUI();
    }

    @Override
    public void refreshScanningUI() {
        boolean isScanning = mConnectableSensorRegistry.isScanning();

        if (mAvailableDevices != null) {
            mAvailableDevices.setProgress(isScanning);
        }
        if (mMainMenu != null) {
            MenuItem refresh = mMainMenu.findItem(R.id.action_refresh);
            refresh.setEnabled(!isScanning);
            if (getActivity() != null) {
                refresh.getIcon().setAlpha(getActivity().getResources().getInteger(
                        isScanning ? R.integer.icon_inactive_alpha : R.integer.icon_active_alpha));
            }
        }
    }

    private void reloadAppearancesAndShowOptions(final ConnectableSensor sensor,
            final PendingIntent settingsIntent) {
        AppSingleton.getInstance(getActivity()).getSensorAppearanceProvider()
                .loadAppearances(new LoggingConsumer<Success>(TAG, "Load appearance") {
                    @Override
                    public void success(Success value) {
                        refresh();
                        // TODO: make this testable?
                        if (sensor.shouldShowOptionsOnConnect()) {
                            showDeviceOptions(mExperimentId, sensor.getConnectedSensorId(),
                                    settingsIntent);
                        }
                    }
                });
    }

    @Override
    public void showDeviceOptions(String experimentId, String sensorId,
            PendingIntent externalSettingsIntent) {
        if (! isResumed()) {
            // Fragment has paused between pairing and popping up options.
            // TODO: if the sensor says that immediate options must be shown, then in this case
            //       we should probably remember that we never showed the options, and pop them
            //       up on resume.
            return;
        }
        // TODO: use a SettingsController subclass once it's fragmentized.
        DeviceOptionsDialog dialog = DeviceOptionsDialog.newInstance(experimentId, sensorId,
                externalSettingsIntent);
        dialog.show(getFragmentManager(), "edit_device");
    }

    @Override
    public SensorGroup getPairedSensorGroup() {
        return mPairedGroup;
    }

    @Override
    public SensorGroup getAvailableSensorGroup() {
        return mAvailableGroup;
    }
}
