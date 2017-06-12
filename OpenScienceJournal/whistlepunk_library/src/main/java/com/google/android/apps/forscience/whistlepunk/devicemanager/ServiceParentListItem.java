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
    private String mProviderId;
    private SensorDiscoverer.DiscoveredService mService;
    private ArrayList<DeviceWithSensors> mDevices = Lists.newArrayList();
    private boolean mIsLoading;
    private boolean mIsNowExpanded;

    public ServiceParentListItem(String providerId,
            SensorDiscoverer.DiscoveredService service,
            boolean startsExpanded) {
        mProviderId = providerId;
        mService = service;
        mIsNowExpanded = startsExpanded;
    }

    @Override
    public List<?> getChildItemList() {
        return mDevices;
    }

    @Override
    public boolean isInitiallyExpanded() {
        return mIsNowExpanded;
    }

    public boolean isCurrentlyExpanded() {
        return mIsNowExpanded;
    }

    public void setIsCurrentlyExpanded(boolean isNowExpanded) {
        mIsNowExpanded = isNowExpanded;
    }

    public String getServiceName() {
        return mService.getName();
    }

    public Drawable getDeviceIcon(Context context) {
        return mService.getIconDrawable(context);
    }

    public boolean isService(String serviceId) {
        return mService.getServiceId().equals(serviceId);
    }

    public boolean addDevice(SensorDiscoverer.DiscoveredDevice device) {
        for (DeviceWithSensors spec : mDevices) {
            if (spec.isSameSensor(device.getSpec())) {
                return false;
            }
        }
        mDevices.add(new DeviceWithSensors(device.getSpec()));
        return true;
    }

    public List<Integer> removeAnyOf(ArrayList<InputDeviceSpec> myDevices) {
        List<Integer> indicesRemoved = Lists.newArrayList();
        for (InputDeviceSpec myDevice : myDevices) {
            for (int i = mDevices.size() - 1; i >= 0; i--) {
                if (mDevices.get(i).isSameSensor(myDevice)) {
                    mDevices.remove(i);
                    indicesRemoved.add(i);
                }
            }
        }
        return indicesRemoved;
    }

    public boolean addSensorToDevice(InputDeviceSpec device, String sensorKey) {
        for (DeviceWithSensors dws : mDevices) {
            if (dws.isSameSensor(device)) {
                dws.addSensorKey(sensorKey);
                return true;
            }
        }
        return false;
    }

    public SensorDiscoverer.ServiceConnectionError getConnectionErrorIfAny() {
        return mService.getConnectionErrorIfAny();
    }

    public void setIsLoading(boolean isLoading) {
        mIsLoading = isLoading;
    }

    public boolean isLoading() {
        return mIsLoading;
    }

    public String getProviderId() {
        return mProviderId;
    }

    public String getGlobalServiceId() {
        return mService.getServiceId();
    }

    public boolean containsSensorKey(String sensorKey) {
        for (DeviceWithSensors device : mDevices) {
            if (device.getSensorKeys().contains(sensorKey)) {
                return true;
            }
        }
        return false;
    }
}
