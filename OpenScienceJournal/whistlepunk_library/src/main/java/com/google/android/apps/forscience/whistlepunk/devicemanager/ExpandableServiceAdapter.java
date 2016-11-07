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

import android.app.FragmentManager;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bignerdranch.expandablerecyclerview.Model.ParentListItem;
import com.bignerdranch.expandablerecyclerview.ViewHolder.ChildViewHolder;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that shows scannable services and available devices on each service
 */
public class ExpandableServiceAdapter extends
        CompositeSensitiveExpandableAdapter<ServiceParentViewHolder,
                ExpandableServiceAdapter.DeviceChildViewHolder> implements
        SensorGroup, CompositeRecyclerAdapter.CompositeSensitiveAdapter {
    private List<ServiceParentListItem> mParentItemList;
    private SensorRegistry mSensorRegistry;
    private ConnectableSensorRegistry mConnectableSensorRegistry;
    private ArrayList<InputDeviceSpec> mMyDevices;
    private final DeviceRegistry mDeviceRegistry;
    private final FragmentManager mFragmentManager;

    public static ExpandableServiceAdapter createEmpty(SensorRegistry sensorRegistry,
            ConnectableSensorRegistry connectableSensorRegistry, int uniqueId,
            DeviceRegistry deviceRegistry, FragmentManager fragmentManager) {
        return new ExpandableServiceAdapter(new ArrayList<ServiceParentListItem>(), sensorRegistry,
                connectableSensorRegistry, uniqueId, deviceRegistry, fragmentManager);
    }

    private ExpandableServiceAdapter(@NonNull List<ServiceParentListItem> parentItemList,
            SensorRegistry sensorRegistry, ConnectableSensorRegistry connectableSensorRegistry,
            int uniqueId, DeviceRegistry deviceRegistry, FragmentManager fragmentManager) {
        super(parentItemList, uniqueId);
        mParentItemList = parentItemList;
        mSensorRegistry = sensorRegistry;
        mConnectableSensorRegistry = connectableSensorRegistry;
        mDeviceRegistry = deviceRegistry;
        mFragmentManager = fragmentManager;
    }

    @Override
    public ServiceParentViewHolder onCreateParentViewHolder(
            ViewGroup parentViewGroup) {
        View viewGroup = LayoutInflater.from(parentViewGroup.getContext()).inflate(
                R.layout.service_expandable_recycler_item, parentViewGroup, false);
        return new ServiceParentViewHolder(viewGroup, this.offsetSupplier());
    }

    @Override
    public void onBindParentViewHolder(ServiceParentViewHolder parentViewHolder, final int position,
            ParentListItem parentListItem) {
        parentViewHolder.bind((ServiceParentListItem) parentListItem, mFragmentManager,
                new Runnable() {
                    @Override
                    public void run() {
                        // TODO: how to refresh?
                    }
                });
    }

    @Override
    public DeviceChildViewHolder onCreateChildViewHolder(ViewGroup childViewGroup) {
        View viewGroup = LayoutInflater.from(childViewGroup.getContext()).inflate(
                R.layout.available_device_recycler_item, childViewGroup, false);
        return new DeviceChildViewHolder(viewGroup);
    }

    @Override
    public void onBindChildViewHolder(DeviceChildViewHolder childViewHolder, int position,
            Object childListItem) {
        childViewHolder.bind((DeviceWithSensors) childListItem, mConnectableSensorRegistry);
    }

    @Override
    public boolean hasSensorKey(String sensorKey) {
        // We're not interested in sensors, just devices
        return true;
    }

    @Override
    public void addSensor(String sensorKey, ConnectableSensor sensor) {
        addAvailableSensor(sensorKey, sensor);
    }

    @Override
    public boolean addAvailableSensor(String sensorKey, ConnectableSensor sensor) {
        InputDeviceSpec device = mDeviceRegistry.getDevice(sensor.getSpec());
        for (ServiceParentListItem service : mParentItemList) {
            if (service.addSensorToDevice(device, sensorKey)) {
                return true;
            }
        }

        return false;
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
    public void addAvailableService(ExternalSensorDiscoverer.DiscoveredService service) {
        String serviceId = service.getServiceId();
        if (indexOfService(serviceId) >= 0) {
            setIsLoading(serviceId, true);
            return;
        }
        ServiceParentListItem item = new ServiceParentListItem(service);
        item.setIsLoading(true);
        mParentItemList.add(item);
        notifyParentItemInserted(mParentItemList.size() - 1);
    }

    @Override
    public void onServiceScanComplete(String serviceId) {
        setIsLoading(serviceId, false);
    }

    private void setIsLoading(String serviceId, boolean isLoading) {
        int i = indexOfService(serviceId);
        if (i >= 0) {
            mParentItemList.get(i).setIsLoading(isLoading);
            notifyParentItemChanged(i);
        }
    }

    private int indexOfService(String serviceId) {
        for (int i = 0; i < mParentItemList.size(); i++) {
            if (mParentItemList.get(i).isService(serviceId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void addAvailableDevice(ExternalSensorDiscoverer.DiscoveredDevice device) {
        for (InputDeviceSpec myDevice : mMyDevices) {
            // Don't add something that's already in my devices
            if (myDevice.isSameSensor(device.getSpec())) {
                return;
            }
        }

        int i = indexOfService(device.getServiceId());
        if (i < 0) {
            return;
        }
        ServiceParentListItem item = mParentItemList.get(i);
        if (item.addDevice(device)) {
            notifyChildItemInserted(i, item.getChildItemList().size() - 1);
        }
    }

    /**
     * Set which devices are going to be listed in "My Devices" (and therefore should _not_ be
     * listed as "available".)
     *
     * @param devices
     */
    @Override
    public void setMyDevices(List<InputDeviceSpec> devices) {
        mMyDevices = new ArrayList<>(devices);
        for (int i = 0; i < mParentItemList.size(); i++) {
            List<Integer> removedIndices = mParentItemList.get(i).removeAnyOf(mMyDevices);
            for (Integer childIndex : removedIndices) {
                notifyChildItemRemoved(i, childIndex);
            }
        }
    }

    public void setProgress(boolean isScanning) {
        // TODO: is this useful?
    }

    public class DeviceChildViewHolder extends ChildViewHolder {
        private final TextView mNameView;

        public DeviceChildViewHolder(View itemView) {
            super(itemView);
            mNameView = (TextView) itemView.findViewById(R.id.device_name);
        }

        public void bind(final DeviceWithSensors dws, final ConnectableSensorRegistry registry) {
            mNameView.setText(dws.getName());
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dws.addToRegistry(registry, mSensorRegistry);
                }
            });
        }
    }
}
