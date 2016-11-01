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

import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bignerdranch.expandablerecyclerview.Adapter.ExpandableRecyclerAdapter;
import com.bignerdranch.expandablerecyclerview.Model.ParentListItem;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExpandableDeviceAdapter extends
        ExpandableRecyclerAdapter<DeviceParentViewHolder, SensorChildViewHolder> implements
        SensorGroup, CompositeRecyclerAdapter.CompositeSensitiveAdapter {
    private final List<DeviceParentListItem> mDeviceParents;
    private final DeviceRegistry mDeviceRegistry;
    private Map<String, ConnectableSensor> mSensorMap = new ArrayMap<>();
    private ConnectableSensorRegistry mRegistry;
    private int mGlobalAdapterStartPosition = 0;
    private final SensorAppearanceProvider mAppearanceProvider;

    public static ExpandableDeviceAdapter createEmpty(final ConnectableSensorRegistry registry,
            DeviceRegistry deviceRegistry, SensorAppearanceProvider appearanceProvider) {
        return new ExpandableDeviceAdapter(registry,
                new ArrayList<DeviceParentListItem>(), deviceRegistry, appearanceProvider);
    }

    private ExpandableDeviceAdapter(final ConnectableSensorRegistry registry,
            List<DeviceParentListItem> deviceParents, DeviceRegistry deviceRegistry,
            SensorAppearanceProvider appearanceProvider) {
        super(deviceParents);
        mRegistry = Preconditions.checkNotNull(registry);
        mDeviceParents = deviceParents;
        mDeviceRegistry = deviceRegistry;
        mAppearanceProvider = appearanceProvider;
    }

    @Override
    public DeviceParentViewHolder onCreateParentViewHolder(
            ViewGroup parentViewGroup) {
        View viewGroup = LayoutInflater.from(parentViewGroup.getContext()).inflate(
                R.layout.device_expandable_recycler_item, parentViewGroup, false);
        return new DeviceParentViewHolder(viewGroup, new Supplier<Integer>() {
            @Override
            public Integer get() {
                return mGlobalAdapterStartPosition;
            }
        });
    }

    @Override
    public void onBindParentViewHolder(DeviceParentViewHolder parentViewHolder, int position,
            ParentListItem parentListItem) {
        parentViewHolder.bind((DeviceParentListItem) parentListItem, mSensorMap);
    }

    @Override
    public SensorChildViewHolder onCreateChildViewHolder(ViewGroup childViewGroup) {
        View viewGroup = LayoutInflater.from(childViewGroup.getContext()).inflate(
                R.layout.sensor_child_recycler_item, childViewGroup, false);
        return new SensorChildViewHolder(viewGroup, mAppearanceProvider);
    }

    @Override
    public void onBindChildViewHolder(SensorChildViewHolder childViewHolder,
            int position, Object childListItem) {
        childViewHolder.bind((String) childListItem, mSensorMap, mRegistry);
    }

    @Override
    public boolean hasSensorKey(String sensorKey) {
        return mSensorMap.containsKey(sensorKey);
    }

    @Override
    public void addSensor(String sensorKey, ConnectableSensor sensor) {
        boolean isReplacement = mSensorMap.containsKey(sensorKey);
        mSensorMap.put(sensorKey, sensor);
        if (isReplacement) {
            notifyChildItemChanged(findParentIndex(sensorKey), findChildIndex(sensorKey));
            return;
        }
        ExternalSensorSpec spec = sensor.getSpec();

        // Do we already have an item for this device?  If so, add the sensor there.
        InputDeviceSpec device = mDeviceRegistry.getDevice(spec);
        for (int i = 0; i < mDeviceParents.size(); i++) {
            DeviceParentListItem parent = mDeviceParents.get(i);
            if (parent.isDevice(device)) {
                parent.addSensor(sensorKey);
                notifyChildItemInserted(i, parent.getChildItemList().size() - 1);
                return;
            }
        }

        // Otherwise, add a new device item.
        DeviceParentListItem item = new DeviceParentListItem(device, mAppearanceProvider);
        item.addSensor(sensorKey);
        addDevice(item);
    }

    private void addDevice(DeviceParentListItem item) {
        if (item.isPhoneSensorParent(mDeviceRegistry)) {
            // add phone sensor container always at top
            mDeviceParents.add(0, item);
            notifyParentItemInserted(0);
        } else {
            mDeviceParents.add(item);
            int parentPosition = mDeviceParents.size() - 1;
            notifyParentItemInserted(parentPosition);
        }
    }

    private int findParentIndex(String sensorKey) {
        for (int i = 0; i < mDeviceParents.size(); i++) {
            if (mDeviceParents.get(i).sensorIndexOf(sensorKey) > -1) {
                return i;
            }
        }
        return -1;
    }

    private int findChildIndex(String sensorKey) {
        for (int i = 0; i < mDeviceParents.size(); i++) {
            int sensorIndex = mDeviceParents.get(i).sensorIndexOf(sensorKey);
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

        // TODO: implement "forget my device"
        return false;
    }

    @Override
    public void replaceSensor(String sensorKey, ConnectableSensor sensor) {
        addSensor(sensorKey, sensor);
    }

    @Override
    public int getSensorCount() {
        return mSensorMap.size();
    }

    public void setProgress(boolean isScanning) {
        // TODO: update UI to show scan status
    }

    DeviceParentListItem getDevice(int position) {
        return mDeviceParents.get(position);
    }

    ConnectableSensor getSensor(int deviceIndex, int sensorIndex) {
        return mSensorMap.get(getDevice(deviceIndex).getSensorKey(sensorIndex));
    }

    @Override
    public void informGlobalAdapterStartPosition(int startPosition) {
        mGlobalAdapterStartPosition = startPosition;
    }
}
