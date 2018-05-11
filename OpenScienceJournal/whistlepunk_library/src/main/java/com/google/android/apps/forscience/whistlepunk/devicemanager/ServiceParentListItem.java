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

import android.content.Context;
import android.graphics.drawable.Drawable;
import com.bignerdranch.expandablerecyclerview.Model.ParentListItem;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;

public class ServiceParentListItem implements ParentListItem {
  // TODO: duplication with DeviceParentListItem
  private String providerId;
  private SensorDiscoverer.DiscoveredService service;
  private ArrayList<DeviceWithSensors> devices = Lists.newArrayList();
  private boolean isLoading;
  private boolean isNowExpanded;

  public ServiceParentListItem(
      String providerId, SensorDiscoverer.DiscoveredService service, boolean startsExpanded) {
    this.providerId = providerId;
    this.service = service;
    isNowExpanded = startsExpanded;
  }

  @Override
  public List<?> getChildItemList() {
    return devices;
  }

  @Override
  public boolean isInitiallyExpanded() {
    return isNowExpanded;
  }

  public boolean isCurrentlyExpanded() {
    return isNowExpanded;
  }

  public void setIsCurrentlyExpanded(boolean isNowExpanded) {
    this.isNowExpanded = isNowExpanded;
  }

  public String getServiceName() {
    return service.getName();
  }

  public Drawable getDeviceIcon(Context context) {
    return service.getIconDrawable(context);
  }

  public boolean isService(String serviceId) {
    return service.getServiceId().equals(serviceId);
  }

  public boolean addDevice(SensorDiscoverer.DiscoveredDevice device) {
    for (DeviceWithSensors spec : devices) {
      if (spec.isSameSensor(device.getSpec())) {
        return false;
      }
    }
    devices.add(new DeviceWithSensors(device.getSpec()));
    return true;
  }

  public List<Integer> removeAnyOf(ArrayList<InputDeviceSpec> myDevices) {
    List<Integer> indicesRemoved = Lists.newArrayList();
    for (InputDeviceSpec myDevice : myDevices) {
      for (int i = devices.size() - 1; i >= 0; i--) {
        if (devices.get(i).isSameSensor(myDevice)) {
          devices.remove(i);
          indicesRemoved.add(i);
        }
      }
    }
    return indicesRemoved;
  }

  public boolean addSensorToDevice(InputDeviceSpec device, String sensorKey) {
    for (DeviceWithSensors dws : devices) {
      if (dws.isSameSensor(device)) {
        dws.addSensorKey(sensorKey);
        return true;
      }
    }
    return false;
  }

  public SensorDiscoverer.ServiceConnectionError getConnectionErrorIfAny() {
    return service.getConnectionErrorIfAny();
  }

  public void setIsLoading(boolean isLoading) {
    this.isLoading = isLoading;
  }

  public boolean isLoading() {
    return isLoading;
  }

  public String getProviderId() {
    return providerId;
  }

  public String getGlobalServiceId() {
    return service.getServiceId();
  }

  public boolean containsSensorKey(String sensorKey) {
    for (DeviceWithSensors device : devices) {
      if (device.getSensorKeys().contains(sensorKey)) {
        return true;
      }
    }
    return false;
  }
}
