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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
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
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.sensors.SystemScheduler;
import com.squareup.leakcanary.RefWatcher;
import java.util.Map;

/** Searches for Bluetooth LE devices that are supported. */
public class ManageDevicesRecyclerFragment extends Fragment
    implements DevicesPresenter, ManageFragment {
  private static final String TAG = "MDRFragment";
  private static final String KEY_MY_DEVICES = "state_key_my_devices";
  private static final String KEY_AVAILABLE_DEVICES = "state_key_available_devices";

  private AppAccount appAccount;
  private ExpandableDeviceAdapter myDevices;
  private ExpandableServiceAdapter availableDevices;
  private Menu mainMenu;
  private ConnectableSensorRegistry registry;
  private SensorRegistry sensorRegistry;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    appAccount =
        WhistlePunkApplication.getAccount(
            getContext(), getArguments(), ManageDevicesActivity.EXTRA_ACCOUNT_KEY);
    AppSingleton appSingleton = AppSingleton.getInstance(getActivity());
    DataController dc = appSingleton.getDataController(appAccount);
    Map<String, SensorDiscoverer> discoverers =
        WhistlePunkApplication.getExternalSensorDiscoverers(getActivity());
    DeviceRegistry deviceRegistry =
        new DeviceRegistry(InputDeviceSpec.builtInDevice(getActivity()));
    SensorAppearanceProvider appearanceProvider =
        appSingleton.getSensorAppearanceProvider(appAccount);

    UsageTracker tracker = WhistlePunkApplication.getUsageTracker(getActivity());
    registry =
        new ConnectableSensorRegistry(
            dc,
            discoverers,
            this,
            new SystemScheduler(),
            new CurrentTimeClock(),
            ManageDevicesActivity.getOptionsListener(getActivity()),
            deviceRegistry,
            appearanceProvider,
            tracker,
            appSingleton.getSensorConnector());
    sensorRegistry = appSingleton.getSensorRegistry();

    myDevices =
        ExpandableDeviceAdapter.createEmpty(
            registry, deviceRegistry, appearanceProvider, sensorRegistry, 0);

    availableDevices =
        ExpandableServiceAdapter.createEmpty(
            sensorRegistry, registry, 1, deviceRegistry, getFragmentManager(), appearanceProvider);
    setHasOptionsMenu(true);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    View view = inflater.inflate(R.layout.fragment_manage_devices, container, false);

    RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler);
    HeaderAdapter myHeader = new HeaderAdapter(R.layout.device_header, R.string.my_devices);
    HeaderAdapter availableHeader =
        new HeaderAdapter(R.layout.device_header, R.string.available_devices);
    if (savedInstanceState != null) {
      myDevices.onRestoreInstanceState(savedInstanceState.getBundle(KEY_MY_DEVICES));
      availableDevices.onRestoreInstanceState(savedInstanceState.getBundle(KEY_AVAILABLE_DEVICES));
    }
    CompositeRecyclerAdapter adapter =
        new CompositeRecyclerAdapter(myHeader, myDevices, availableHeader, availableDevices);
    adapter.setHasStableIds(true);
    recyclerView.setAdapter(adapter);
    recyclerView.setLayoutManager(
        new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
    // Don't animate on change: https://code.google.com/p/android/issues/detail?id=204277.
    SimpleItemAnimator animator = new DefaultItemAnimator();
    animator.setSupportsChangeAnimations(false);
    recyclerView.setItemAnimator(animator);
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshAfterLoad();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    Bundle myDeviceState = new Bundle();
    myDevices.onSaveInstanceState(myDeviceState);
    outState.putBundle(KEY_MY_DEVICES, myDeviceState);

    Bundle availableState = new Bundle();
    availableDevices.onSaveInstanceState(availableState);
    outState.putBundle(KEY_AVAILABLE_DEVICES, availableState);
  }

  @Override
  public void onPause() {
    stopScanning();
    super.onPause();
  }

  @Override
  public void onDestroy() {
    stopScanning();
    mainMenu = null;
    myDevices.onDestroy();
    myDevices = null;
    super.onDestroy();

    // Make sure we don't leak this fragment.
    RefWatcher watcher = WhistlePunkApplication.getAppServices(getActivity()).getRefWatcher();
    watcher.watch(this);
  }

  @Override
  public boolean isDestroyed() {
    return myDevices == null;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_manage_devices, menu);
    super.onCreateOptionsMenu(menu, inflater);
    mainMenu = menu;
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
    registry.refresh(clearSensorCache, sensorRegistry);
  }

  public void refreshAfterLoad() {
    setExperimentId(getArguments().getString(ManageDevicesActivity.EXTRA_EXPERIMENT_ID));
  }

  private void setExperimentId(String experimentId) {
    registry.setExperimentId(experimentId, sensorRegistry);
    AppSingleton.getInstance(getActivity())
        .getDataController(appAccount)
        .getExperimentById(
            experimentId,
            new LoggingConsumer<Experiment>(TAG, "load experiment for name") {
              @Override
              public void success(Experiment exp) {
                View view = getView();
                if (view == null) {
                  return;
                }
                TextView selectSensors = (TextView) view.findViewById(R.id.select_sensors);
                selectSensors.setText(
                    getString(R.string.select_sensors, exp.getDisplayTitle(getActivity())));
              }
            });
  }

  private void stopScanning() {
    registry.stopScanningInDiscoverers();
  }

  @Override
  public void refreshScanningUI() {
    boolean isScanning = registry.isScanning();

    if (mainMenu != null) {
      MenuItem refresh = mainMenu.findItem(R.id.action_refresh);
      refresh.setEnabled(!isScanning);
      if (getActivity() != null) {
        refresh
            .getIcon()
            .setAlpha(
                getActivity()
                    .getResources()
                    .getInteger(
                        isScanning ? R.integer.icon_inactive_alpha : R.integer.icon_active_alpha));
      }
    }
  }

  @Override
  public void showSensorOptions(
      String experimentId, String sensorId, SensorDiscoverer.SettingsInterface settings) {
    if (!isResumed()) {
      // Fragment has paused between pairing and popping up options.
      // TODO: if the sensor says that immediate options must be shown, then in this case
      //       we should probably remember that we never showed the options, and pop them
      //       up on resume.
      return;
    }
    settings.show(appAccount, experimentId, sensorId, getFragmentManager(), false);
  }

  @Override
  public SensorGroup getPairedSensorGroup() {
    return myDevices;
  }

  @Override
  public SensorGroup getAvailableSensorGroup() {
    return availableDevices;
  }

  @Override
  public void unpair(String experimentId, String sensorId) {
    ((DeviceOptionsListener) getActivity()).onRemoveSensorFromExperiment(experimentId, sensorId);
  }
}
