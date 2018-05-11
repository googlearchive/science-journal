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
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.bignerdranch.expandablerecyclerview.Model.ParentListItem;
import com.bignerdranch.expandablerecyclerview.Model.ParentWrapper;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExpandableDeviceAdapter
    extends CompositeSensitiveExpandableAdapter<DeviceParentViewHolder, SensorChildViewHolder>
    implements SensorGroup, CompositeRecyclerAdapter.CompositeSensitiveAdapter {
  private static final String KEY_COLLAPSED_DEVICE_ADDRESSES = "key_collapsed_devices";
  private final List<DeviceParentListItem> deviceParents;
  private final DeviceRegistry deviceRegistry;
  private Map<String, ConnectableSensor> sensorMap = new ArrayMap<>();
  private ConnectableSensorRegistry registry;
  private final SensorAppearanceProvider appearanceProvider;
  private final SensorRegistry sensorRegistry;
  private ArrayList<String> initiallyCollapsedAddresses = new ArrayList<>();
  private final EnablementController enablementController;
  private DeviceParentViewHolder.MenuCallbacks menuCallbacks =
      new DeviceParentViewHolder.MenuCallbacks() {
        @Override
        public void forgetDevice(InputDeviceSpec spec) {
          registry.forgetMyDevice(spec, sensorRegistry, enablementController);
        }
      };

  public static ExpandableDeviceAdapter createEmpty(
      final ConnectableSensorRegistry registry,
      DeviceRegistry deviceRegistry,
      SensorAppearanceProvider appearanceProvider,
      SensorRegistry sensorRegistry,
      int uniqueId) {
    return new ExpandableDeviceAdapter(
        registry,
        new ArrayList<DeviceParentListItem>(),
        deviceRegistry,
        appearanceProvider,
        sensorRegistry,
        uniqueId);
  }

  private ExpandableDeviceAdapter(
      final ConnectableSensorRegistry registry,
      List<DeviceParentListItem> deviceParents,
      DeviceRegistry deviceRegistry,
      SensorAppearanceProvider appearanceProvider,
      SensorRegistry sensorRegistry,
      int uniqueId) {
    super(deviceParents, uniqueId);
    this.registry = Preconditions.checkNotNull(registry);
    this.deviceParents = deviceParents;
    this.deviceRegistry = deviceRegistry;
    this.appearanceProvider = appearanceProvider;
    this.sensorRegistry = sensorRegistry;
    enablementController = new EnablementController();
  }

  @Override
  public long getItemId(int position) {
    Object item = getListItem(position);
    long result;
    if (item instanceof ParentWrapper) {
      DeviceParentListItem parent =
          (DeviceParentListItem) ((ParentWrapper) item).getParentListItem();
      result = parent.getSpec().getGlobalDeviceAddress().hashCode();
    } else {
      // The item is a SensorKey string.
      result = ((String) item).hashCode();
    }
    return result;
  }

  @Override
  public DeviceParentViewHolder onCreateParentViewHolder(ViewGroup parentViewGroup) {
    View viewGroup =
        LayoutInflater.from(parentViewGroup.getContext())
            .inflate(R.layout.device_expandable_recycler_item, parentViewGroup, false);
    return new DeviceParentViewHolder(viewGroup, offsetSupplier(), menuCallbacks);
  }

  @Override
  public void onBindParentViewHolder(
      DeviceParentViewHolder parentViewHolder, int position, ParentListItem parentListItem) {
    parentViewHolder.bind((DeviceParentListItem) parentListItem, sensorMap, deviceRegistry);
  }

  @Override
  public SensorChildViewHolder onCreateChildViewHolder(ViewGroup childViewGroup) {
    View viewGroup =
        LayoutInflater.from(childViewGroup.getContext())
            .inflate(R.layout.sensor_child_recycler_item, childViewGroup, false);
    return new SensorChildViewHolder(viewGroup, appearanceProvider);
  }

  @Override
  public void onBindChildViewHolder(
      SensorChildViewHolder childViewHolder, int position, Object childListItem) {
    childViewHolder.bind((String) childListItem, sensorMap, registry, enablementController);
  }

  @Override
  public boolean hasSensorKey(String sensorKey) {
    return sensorMap.containsKey(sensorKey);
  }

  @Override
  public boolean addAvailableSensor(String sensorKey, ConnectableSensor sensor) {
    boolean isReplacement = sensorMap.containsKey(sensorKey);
    if (isReplacement) {
      putSensor(sensorKey, sensor);
      int parentIndex = findParentIndex(sensorKey);
      if (parentIndex >= 0) {
        notifyParentItemChanged(parentIndex);
        return true;
      }
    }
    ExternalSensorSpec spec = sensor.getSpec();

    // Do we already have an item for this device?  If so, add the sensor there.
    InputDeviceSpec device = deviceRegistry.getDevice(spec);
    int i = findDevice(device);
    if (i >= 0) {
      putSensor(sensorKey, sensor);
      addSensorToDevice(i, sensorKey);
      return true;
    }

    return false;
  }

  private ConnectableSensor putSensor(String sensorKey, ConnectableSensor sensor) {
    enablementController.setChecked(sensorKey, sensor.isPaired());
    return sensorMap.put(sensorKey, sensor);
  }

  @Override
  public void onSensorAddedElsewhere(String newKey, ConnectableSensor sensor) {
    // Don't care
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    ArrayList<String> collapsedDevices = new ArrayList<>();
    for (DeviceParentListItem deviceParent : deviceParents) {
      if (!deviceParent.isCurrentlyExpanded()) {
        collapsedDevices.add(deviceParent.getSpec().getGlobalDeviceAddress());
      }
    }
    savedInstanceState.putStringArrayList(KEY_COLLAPSED_DEVICE_ADDRESSES, collapsedDevices);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    ArrayList<String> list = savedInstanceState.getStringArrayList(KEY_COLLAPSED_DEVICE_ADDRESSES);
    if (list != null) {
      initiallyCollapsedAddresses.addAll(list);
    }
    super.onRestoreInstanceState(savedInstanceState);
  }

  private void addSensorToDevice(int deviceIndex, String sensorKey) {
    DeviceParentListItem parent = deviceParents.get(deviceIndex);
    parent.addSensor(sensorKey);

    // Maybe refresh icon, and refresh children
    notifyChildItemInserted(deviceIndex, parent.getChildItemList().size() - 1);
    notifyParentItemChanged(deviceIndex);
  }

  @Override
  public void addSensor(String sensorKey, ConnectableSensor sensor) {
    boolean addedToMyDevice = addAvailableSensor(sensorKey, sensor);
    putSensor(sensorKey, sensor);
    if (!addedToMyDevice) {
      // Otherwise, add a new device item.
      InputDeviceSpec device = deviceRegistry.getDevice(sensor.getSpec());
      DeviceParentListItem item = createDeviceParent(device);
      item.addSensor(sensorKey);
      addDevice(item);
    }
  }

  @NonNull
  private DeviceParentListItem createDeviceParent(InputDeviceSpec device) {
    String globalAddress = device.getGlobalDeviceAddress();
    boolean initallyCollapsed = initiallyCollapsedAddresses.remove(globalAddress);
    boolean startsExpanded = !initallyCollapsed;
    return new DeviceParentListItem(device, appearanceProvider, startsExpanded);
  }

  private int findDevice(InputDeviceSpec device) {
    for (int i = 0; i < deviceParents.size(); i++) {
      DeviceParentListItem parent = deviceParents.get(i);
      if (parent.isDevice(device)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void addAvailableDevice(SensorDiscoverer.DiscoveredDevice device) {
    // Don't need anything here; we'll grab from DeviceRegistry if this is My Device
  }

  @Override
  public void setMyDevices(List<InputDeviceSpec> myDevices) {
    List<InputDeviceSpec> unaccountedDevices = new ArrayList<>(myDevices);

    for (int i = deviceParents.size() - 1; i >= 0; i--) {
      removeUnlessPresent(i, unaccountedDevices);
    }

    for (InputDeviceSpec unaccountedDevice : unaccountedDevices) {
      addDevice(createDeviceParent(unaccountedDevice));
    }
  }

  private void removeUnlessPresent(int i, List<InputDeviceSpec> unaccountedDevices) {
    DeviceParentListItem parent = deviceParents.get(i);
    for (int j = unaccountedDevices.size() - 1; j >= 0; j--) {
      if (parent.isDevice(unaccountedDevices.get(j))) {
        unaccountedDevices.remove(j);
        return;
      }
    }
    DeviceParentListItem listItem = deviceParents.remove(i);
    List<String> sensorKeys = listItem.getSensorKeys();
    for (String sensorKey : sensorKeys) {
      sensorMap.remove(sensorKey);
    }
    notifyParentItemRemoved(i);
  }

  private void addDevice(DeviceParentListItem item) {
    if (item.isPhoneSensorParent(deviceRegistry)) {
      // add phone sensor container always at top
      deviceParents.add(0, item);
      notifyParentItemInserted(0);
    } else {
      deviceParents.add(item);
      int parentPosition = deviceParents.size() - 1;
      notifyParentItemInserted(parentPosition);
    }
  }

  private int findParentIndex(String sensorKey) {
    for (int i = 0; i < deviceParents.size(); i++) {
      if (deviceParents.get(i).sensorIndexOf(sensorKey) > -1) {
        return i;
      }
    }
    return -1;
  }

  private int findChildIndex(String sensorKey) {
    for (int i = 0; i < deviceParents.size(); i++) {
      int sensorIndex = deviceParents.get(i).sensorIndexOf(sensorKey);
      if (sensorIndex > -1) {
        return sensorIndex;
      }
    }
    return -1;
  }

  @Override
  public boolean removeSensor(String sensorKey) {
    // We don't expect this to be called, since we only use Expandable for "My Devices", and
    // there we only enable/disable known sensors under a device, and "forget" devices
    return false;
  }

  @Override
  public void replaceSensor(String sensorKey, ConnectableSensor sensor) {
    addSensor(sensorKey, sensor);
  }

  @Override
  public int getSensorCount() {
    return sensorMap.size();
  }

  public void setProgress(boolean isScanning) {
    // TODO: update UI to show scan status
  }

  DeviceParentListItem getDevice(int position) {
    return deviceParents.get(position);
  }

  ConnectableSensor getSensor(int deviceIndex, int sensorIndex) {
    return sensorMap.get(getDevice(deviceIndex).getSensorKey(sensorIndex));
  }

  @Override
  public void addAvailableService(
      String providerId, SensorDiscoverer.DiscoveredService service, boolean startSpinners) {
    // This view doesn't track services
  }

  @Override
  public void onServiceScanComplete(String serviceId) {
    // This view doesn't track services
  }

  public void onDestroy() {
    enablementController.onDestroy();
  }

  @VisibleForTesting
  public DeviceParentViewHolder.MenuCallbacks getMenuCallbacks() {
    return menuCallbacks;
  }

  @VisibleForTesting
  EnablementController getEnablementController() {
    return enablementController;
  }

  @VisibleForTesting
  public String getSensorKey(int deviceIndex, int sensorIndex) {
    return deviceParents.get(deviceIndex).getSensorKey(sensorIndex);
  }
}
