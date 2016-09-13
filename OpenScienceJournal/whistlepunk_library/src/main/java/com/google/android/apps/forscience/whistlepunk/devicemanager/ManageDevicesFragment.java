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

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;
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
import java.util.concurrent.TimeUnit;

/**
 * Searches for Bluetooth LE devices that are supported.
 */
public class ManageDevicesFragment extends PreferenceFragment implements DeviceOptionsPresenter {
    private static final String TAG = "ManageDevices";

    // STOPSHIP: set this to false.
    private static final boolean LOCAL_LOGD = true;

    private static final int MSG_STOP_SCANNING = 10001;
    private static final long SCAN_TIME_MS = TimeUnit.SECONDS.toMillis(10);

    private static final String PREF_KEY_PAIRED_DEVICES = "paired_devices";
    private static final String PREF_KEY_AVAILABLE_DEVICES = "available_devices";

    private PreferenceCategory mPairedDevices;
    private PreferenceProgressCategory mAvailableDevices;
    private Menu mMainMenu;

    private boolean mScanning;
    private Handler mHandler;
    private DataController mDataController;

    private String mExperimentId;
    private ConnectableSensorRegistry mConnectableSensorRegistry;

    public ManageDevicesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == MSG_STOP_SCANNING) {
                    stopScanning();
                    return true;
                }
                return false;
            }
        });
        mDataController = AppSingleton.getInstance(getActivity()).getDataController();
        addPreferencesFromResource(R.xml.external_devices);
        mPairedDevices = new PreferenceProgressCategory(getActivity());
        mPairedDevices.setKey(PREF_KEY_PAIRED_DEVICES);
        mPairedDevices.setTitle(R.string.external_devices_paired);
        mPairedDevices.setOrder(0);
        mPairedDevices.setSelectable(false);
        mAvailableDevices = new PreferenceProgressCategory(getActivity());
        mAvailableDevices.setKey(PREF_KEY_AVAILABLE_DEVICES);
        mAvailableDevices.setTitle(R.string.external_devices_available);
        mAvailableDevices.setOrder(1);
        mAvailableDevices.setSelectable(false);
        getPreferenceScreen().addPreference(mAvailableDevices);
        setHasOptionsMenu(true);

        Map<String, ExternalSensorDiscoverer> discoverers =
                WhistlePunkApplication.getExternalSensorDiscoverers(getActivity());

        mConnectableSensorRegistry = new ConnectableSensorRegistry(mDataController, discoverers,
                getActivity());
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
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey() != null) {
            if (!mConnectableSensorRegistry.getIsPairedFromPreference(preference)) {
                mConnectableSensorRegistry.addExternalSensorIfNecessary(mExperimentId, preference,
                        new LoggingConsumer<ConnectableSensor>(TAG, "Add external sensor") {
                            @Override
                            public void success(ConnectableSensor sensor) {
                                if (LOCAL_LOGD) {
                                    Log.d(TAG, "Added sensor to experiment " + mExperimentId);
                                }
                                reloadAppearancesAndShowOptions(sensor);
                            }
                        });
            } else {
                mConnectableSensorRegistry.showDeviceOptions(this, mExperimentId, preference);
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

                        mPairedDevices.removeAll();
                        // Need to add paired devices header before adding specific prefs
                        boolean hasPairedDevicePref = getPreferenceScreen().findPreference(
                                PREF_KEY_PAIRED_DEVICES) != null;
                        if (sensors.size() == 0 && hasPairedDevicePref) {
                            getPreferenceScreen().removePreference(mPairedDevices);
                        } else if (sensors.size() > 0 && !hasPairedDevicePref) {
                            getPreferenceScreen().addPreference(mPairedDevices);
                        }
                        mConnectableSensorRegistry.setPairedSensors(mAvailableDevices,
                                mPairedDevices, sensors);
                        scanForDevices();
                    }
                });
    }

    private void scanForDevices() {
        if (!mScanning) {
            mScanning = true;
            mHandler.removeMessages(MSG_STOP_SCANNING);
            if (mConnectableSensorRegistry.startScanningInDiscoverers(mAvailableDevices)) {
                mHandler.sendEmptyMessageDelayed(MSG_STOP_SCANNING, SCAN_TIME_MS);
                refreshScanningUI();
            } else {
                mScanning = false;
            }
        }
    }

    private void stopScanning() {
        if (mScanning) {
            mScanning = false;
            mConnectableSensorRegistry.stopScanningInDiscoverers();
            mHandler.removeMessages(MSG_STOP_SCANNING);
            refreshScanningUI();
        }
    }

    private void refreshScanningUI() {
        if (mAvailableDevices != null) {
            mAvailableDevices.setProgress(mScanning);
        }
        if (mMainMenu != null) {
            MenuItem refresh = mMainMenu.findItem(R.id.action_refresh);
            refresh.setEnabled(!mScanning);
            if (getActivity() != null) {
                refresh.getIcon().setAlpha(getActivity().getResources().getInteger(
                        mScanning ? R.integer.icon_inactive_alpha : R.integer.icon_active_alpha));
            }
        }
    }

    private void reloadAppearancesAndShowOptions(final ConnectableSensor sensor) {
        AppSingleton.getInstance(getActivity()).getSensorAppearanceProvider()
                .loadAppearances(new LoggingConsumer<Success>(TAG, "Load appearance") {
                    @Override
                    public void success(Success value) {
                        refresh();
                        showDeviceOptions(mExperimentId, sensor.getConnectedSensorId());
                    }
                });
    }

    @Override
    public void showDeviceOptions(String experimentId, String sensorId) {
        // TODO: use a SettingsController subclass once it's fragmentized.
        DeviceOptionsDialog dialog = DeviceOptionsDialog.newInstance(experimentId, sensorId);
        dialog.show(getFragmentManager(), "edit_device");
    }
}
