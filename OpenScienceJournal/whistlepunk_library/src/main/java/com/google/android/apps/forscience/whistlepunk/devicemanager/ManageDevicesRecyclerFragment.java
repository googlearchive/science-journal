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

import android.app.Fragment;
import android.app.PendingIntent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.CurrentTimeClock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.sensors.SystemScheduler;
import com.squareup.leakcanary.RefWatcher;

import java.util.Map;

/**
 * Searches for Bluetooth LE devices that are supported.
 */
public class ManageDevicesRecyclerFragment extends Fragment implements DevicesPresenter,
        ManageFragment {
    private static final String TAG = "MDRFragment";

    private ExpandableDeviceAdapter mMyDevices;
    private DeviceAdapter mAvailableDevices;
    private Menu mMainMenu;
    private ConnectableSensorRegistry mRegistry;
    private SensorRegistry mSensorRegistry;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSingleton appSingleton = AppSingleton.getInstance(getActivity());
        DataController dc = appSingleton.getDataController();
        Map<String, ExternalSensorDiscoverer> discoverers =
                WhistlePunkApplication.getExternalSensorDiscoverers(getActivity());
        DeviceRegistry deviceRegistry = new DeviceRegistry(
                InputDeviceSpec.builtInDevice(getActivity()));
        mRegistry = new ConnectableSensorRegistry(dc, discoverers, this, new SystemScheduler(),
                new CurrentTimeClock(), ManageDevicesActivity.getOptionsListener(this
                .getActivity()), deviceRegistry);
        SensorAppearanceProvider appearanceProvider = appSingleton.getSensorAppearanceProvider();
        mSensorRegistry = appSingleton.getSensorRegistry();
        mMyDevices = ExpandableDeviceAdapter.createEmpty(mRegistry, deviceRegistry,
                appearanceProvider);
        mAvailableDevices = new DeviceAdapter(false, mRegistry, appearanceProvider);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_manage_devices, container, false);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler);
        HeaderAdapter myHeader = new HeaderAdapter(R.layout.device_header, R.string.my_devices);
        HeaderAdapter availableHeader = new HeaderAdapter(R.layout.device_header,
                R.string.available_devices);
        CompositeRecyclerAdapter adapter = new CompositeRecyclerAdapter(myHeader, mMyDevices,
                availableHeader, mAvailableDevices);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(
                new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        return view;
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_manage_devices, menu);
        super.onCreateOptionsMenu(menu, inflater);
        mMainMenu = menu;
        refreshScanningUI();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            refresh(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refresh(boolean clearSensorCache) {
        mRegistry.refresh(clearSensorCache);
    }

    public void refreshAfterLoad() {
        String experimentId = getArguments().getString(ManageDevicesActivity.EXTRA_EXPERIMENT_ID);
        setExperimentId(experimentId);
        refresh(false);
    }

    private void setExperimentId(String experimentId) {
        mRegistry.setExperimentId(experimentId);
        AppSingleton.getInstance(getActivity()).getDataController().getExperimentById(experimentId,
                new LoggingConsumer<Experiment>(TAG, "load experiment for name") {
                    @Override
                    public void success(Experiment exp) {
                        View view = getView();
                        if (view == null) {
                            return;
                        }
                        TextView selectSensors = (TextView) view.findViewById(R.id.select_sensors);
                        selectSensors.setText(getString(R.string.select_sensors,
                                exp.getDisplayTitle(getActivity())));
                    }
                });
    }

    private void stopScanning() {
        mRegistry.stopScanningInDiscoverers();
    }

    @Override
    public void refreshScanningUI() {
        boolean isScanning = mRegistry.isScanning();

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

    @Override
    public void showDeviceOptions(String experimentId, String sensorId,
            PendingIntent externalSettingsIntent) {
        if (!isResumed()) {
            // Fragment has paused between pairing and popping up options.
            // TODO: if the sensor says that immediate options must be shown, then in this case
            //       we should probably remember that we never showed the options, and pop them
            //       up on resume.
            return;
        }
        DeviceOptionsDialog dialog = DeviceOptionsDialog.newInstance(experimentId, sensorId,
                externalSettingsIntent);
        dialog.show(getFragmentManager(), "edit_device");
    }

    @Override
    public SensorGroup getPairedSensorGroup() {
        return mMyDevices;
    }

    @Override
    public SensorGroup getAvailableSensorGroup() {
        return mAvailableDevices;
    }
}
