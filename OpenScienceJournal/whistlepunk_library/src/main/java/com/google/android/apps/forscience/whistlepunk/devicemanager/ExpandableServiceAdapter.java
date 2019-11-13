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
import androidx.fragment.app.FragmentManager;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.bignerdranch.expandablerecyclerview.Model.ParentListItem;
import com.bignerdranch.expandablerecyclerview.Model.ParentWrapper;
import com.bignerdranch.expandablerecyclerview.ViewHolder.ChildViewHolder;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adapter that shows scannable services and available devices on each service */
public class ExpandableServiceAdapter
    extends CompositeSensitiveExpandableAdapter<
        ServiceParentViewHolder, ExpandableServiceAdapter.DeviceChildViewHolder>
    implements SensorGroup, CompositeRecyclerAdapter.CompositeSensitiveAdapter {
  private static final String KEY_COLLAPSED_SERVICE_ADDRESSES = "key_collapsed_services";
  private List<ServiceParentListItem> parentItemList;
  private SensorRegistry sensorRegistry;
  private ConnectableSensorRegistry connectableSensorRegistry;
  private ArrayList<InputDeviceSpec> myDevices = Lists.newArrayList();
  private final DeviceRegistry deviceRegistry;
  private final FragmentManager fragmentManager;

  // TODO: maintain one global map
  private Map<String, ConnectableSensor> sensorMap = new ArrayMap<>();
  private final SensorAppearanceProvider appearanceProvider;
  private ArrayList<String> initiallyCollapsedServiceIds = new ArrayList<>();

  public static ExpandableServiceAdapter createEmpty(
      SensorRegistry sensorRegistry,
      ConnectableSensorRegistry connectableSensorRegistry,
      int uniqueId,
      DeviceRegistry deviceRegistry,
      FragmentManager fragmentManager,
      SensorAppearanceProvider appearanceProvider) {
    return new ExpandableServiceAdapter(
        new ArrayList<ServiceParentListItem>(),
        sensorRegistry,
        connectableSensorRegistry,
        uniqueId,
        deviceRegistry,
        fragmentManager,
        appearanceProvider);
  }

  private ExpandableServiceAdapter(
      @NonNull List<ServiceParentListItem> parentItemList,
      SensorRegistry sensorRegistry,
      ConnectableSensorRegistry connectableSensorRegistry,
      int uniqueId,
      DeviceRegistry deviceRegistry,
      FragmentManager fragmentManager,
      SensorAppearanceProvider appearanceProvider) {
    super(parentItemList, uniqueId);
    this.parentItemList = parentItemList;
    this.sensorRegistry = sensorRegistry;
    this.connectableSensorRegistry = connectableSensorRegistry;
    this.deviceRegistry = deviceRegistry;
    this.fragmentManager = fragmentManager;
    this.appearanceProvider = appearanceProvider;
  }

  @Override
  public long getItemId(int position) {
    Object item = getListItem(position);
    long result;
    if (item instanceof ParentWrapper) {
      ServiceParentListItem parent =
          (ServiceParentListItem) ((ParentWrapper) item).getParentListItem();
      result = parent.getGlobalServiceId().hashCode();
    } else {
      DeviceWithSensors device = (DeviceWithSensors) item;
      result = (device.getSpec().getGlobalDeviceAddress() + "device").hashCode();
    }
    return result;
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    ArrayList<String> collapsedServices = new ArrayList<>();
    for (ServiceParentListItem serviceParent : parentItemList) {
      if (!serviceParent.isCurrentlyExpanded()) {
        collapsedServices.add(serviceParent.getGlobalServiceId());
      }
    }
    savedInstanceState.putStringArrayList(KEY_COLLAPSED_SERVICE_ADDRESSES, collapsedServices);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    ArrayList<String> list = savedInstanceState.getStringArrayList(KEY_COLLAPSED_SERVICE_ADDRESSES);
    if (list != null) {
      initiallyCollapsedServiceIds.addAll(list);
    }
    super.onRestoreInstanceState(savedInstanceState);
  }

  @Override
  public ServiceParentViewHolder onCreateParentViewHolder(ViewGroup parentViewGroup) {
    View viewGroup =
        LayoutInflater.from(parentViewGroup.getContext())
            .inflate(R.layout.service_expandable_recycler_item, parentViewGroup, false);
    return new ServiceParentViewHolder(viewGroup, this.offsetSupplier());
  }

  @Override
  public void onBindParentViewHolder(
      ServiceParentViewHolder parentViewHolder,
      final int position,
      final ParentListItem parentListItem) {
    final ServiceParentListItem serviceParent = (ServiceParentListItem) parentListItem;
    parentViewHolder.bind(
        serviceParent,
        fragmentManager,
        new Runnable() {
          @Override
          public void run() {
            connectableSensorRegistry.reloadProvider(serviceParent.getProviderId(), false);
          }
        });
  }

  @Override
  public DeviceChildViewHolder onCreateChildViewHolder(ViewGroup childViewGroup) {
    View viewGroup =
        LayoutInflater.from(childViewGroup.getContext())
            .inflate(R.layout.available_device_recycler_item, childViewGroup, false);
    return new DeviceChildViewHolder(viewGroup);
  }

  @Override
  public void onBindChildViewHolder(
      DeviceChildViewHolder childViewHolder, int position, Object childListItem) {
    childViewHolder.bind((DeviceWithSensors) childListItem, appearanceProvider, sensorMap);
  }

  @Override
  public boolean hasSensorKey(String sensorKey) {
    for (ServiceParentListItem listItem : parentItemList) {
      if (listItem.containsSensorKey(sensorKey)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addSensor(String sensorKey, ConnectableSensor sensor) {
    addAvailableSensor(sensorKey, sensor);
  }

  @Override
  public boolean addAvailableSensor(String sensorKey, ConnectableSensor sensor) {
    sensorMap.put(sensorKey, sensor);
    InputDeviceSpec device = deviceRegistry.getDevice(sensor.getSpec());
    int size = parentItemList.size();
    for (int i = 0; i < size; i++) {
      if (parentItemList.get(i).addSensorToDevice(device, sensorKey)) {
        // Possibly refresh icon
        notifyParentItemChanged(i);
        return true;
      }
    }

    return false;
  }

  @Override
  public void onSensorAddedElsewhere(String newKey, ConnectableSensor sensor) {
    sensorMap.put(newKey, sensor);
  }

  @Override
  public boolean removeSensor(String sensorKey) {
    return false;
  }

  @Override
  public void replaceSensor(String sensorKey, ConnectableSensor sensor) {
    // Doesn't happen
  }

  @Override
  public int getSensorCount() {
    // Is actually only used by available
    return 0;
  }

  @Override
  public void addAvailableService(
      String providerId, SensorDiscoverer.DiscoveredService service, boolean startSpinners) {
    String serviceId = service.getServiceId();
    if (indexOfService(serviceId) >= 0) {
      if (startSpinners) {
        setIsLoading(serviceId, true);
      }
      return;
    }
    boolean initallyCollapsed = initiallyCollapsedServiceIds.remove(service.getServiceId());
    boolean startsExpanded = !initallyCollapsed;
    ServiceParentListItem item = new ServiceParentListItem(providerId, service, startsExpanded);
    item.setIsLoading(true);
    parentItemList.add(item);
    notifyParentItemInserted(parentItemList.size() - 1);
  }

  @Override
  public void onServiceScanComplete(String serviceId) {
    setIsLoading(serviceId, false);
  }

  private void setIsLoading(String serviceId, boolean isLoading) {
    int i = indexOfService(serviceId);
    if (i >= 0) {
      parentItemList.get(i).setIsLoading(isLoading);
      notifyParentItemChanged(i);
    }
  }

  private int indexOfService(String serviceId) {
    for (int i = 0; i < parentItemList.size(); i++) {
      if (parentItemList.get(i).isService(serviceId)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void addAvailableDevice(SensorDiscoverer.DiscoveredDevice device) {
    for (InputDeviceSpec myDevice : myDevices) {
      // Don't add something that's already in my devices
      if (myDevice.isSameSensor(device.getSpec())) {
        return;
      }
    }

    int i = indexOfService(device.getServiceId());
    if (i < 0) {
      return;
    }
    ServiceParentListItem item = parentItemList.get(i);
    if (item.addDevice(device)) {
      notifyChildItemInserted(i, item.getChildItemList().size() - 1);
    }
  }

  /**
   * Set which devices are going to be listed in "My Devices" (and therefore should _not_ be listed
   * as "available".)
   *
   * @param devices
   */
  @Override
  public void setMyDevices(List<InputDeviceSpec> devices) {
    myDevices = new ArrayList<>(devices);
    for (int i = 0; i < parentItemList.size(); i++) {
      List<Integer> removedIndices = parentItemList.get(i).removeAnyOf(myDevices);
      for (Integer childIndex : removedIndices) {
        notifyChildItemRemoved(i, childIndex);
      }
    }
  }

  public class DeviceChildViewHolder extends ChildViewHolder {
    private final TextView nameView;
    private final ImageView icon;

    public DeviceChildViewHolder(View itemView) {
      super(itemView);
      nameView = (TextView) itemView.findViewById(R.id.device_name);
      icon = (ImageView) itemView.findViewById(R.id.device_icon);
    }

    public void bind(
        final DeviceWithSensors dws,
        SensorAppearanceProvider appearanceProvider,
        Map<String, ConnectableSensor> sensorMap) {
      nameView.setText(dws.getName());
      itemView.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              dws.addToRegistry(connectableSensorRegistry, sensorRegistry);
            }
          });
      icon.setImageDrawable(dws.getIconDrawable(icon.getContext(), appearanceProvider, sensorMap));
    }
  }
}
