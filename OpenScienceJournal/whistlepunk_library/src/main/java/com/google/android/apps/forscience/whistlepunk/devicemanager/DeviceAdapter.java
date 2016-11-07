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

import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> implements
        SensorGroup {
    private final SensorAppearanceProvider mSensorAppearanceProvider;
    List<String> mSensorKeys = new ArrayList<>();
    List<ConnectableSensor> mSensors = new ArrayList<>();
    private boolean mIsPaired;
    private ConnectableSensorRegistry mRegistry;
    private final SensorRegistry mSensorRegistry;

    public DeviceAdapter(boolean isPaired, final ConnectableSensorRegistry registry,
            SensorAppearanceProvider appearanceProvider, SensorRegistry sensorRegistry) {
        mIsPaired = isPaired;
        mRegistry = Preconditions.checkNotNull(registry);
        mSensorAppearanceProvider = appearanceProvider;
        mSensorRegistry = sensorRegistry;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View viewGroup = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.device_collapsed_recycler_item, parent, false);
        return new ViewHolder(viewGroup);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.bind(mSensors.get(position));
        final String sensorKey = mSensorKeys.get(position);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsPaired) {
                    mRegistry.showSensorOptions(sensorKey);
                } else {
                    holder.setIsPairing();
                    mRegistry.pair(sensorKey, mSensorRegistry);
                }
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.device_collapsed_recycler_item;
    }

    @Override
    public int getItemCount() {
        return getSensorCount();
    }

    @Override
    public boolean hasSensorKey(String sensorKey) {
        return mSensorKeys.contains(sensorKey);
    }

    @Override
    public void addSensor(String sensorKey, ConnectableSensor sensor) {
        int previousIndex = mSensorKeys.indexOf(sensorKey);
        if (previousIndex >= 0) {
            mSensors.set(previousIndex, sensor);
            notifyItemChanged(previousIndex);
        } else {
            mSensorKeys.add(sensorKey);
            mSensors.add(sensor);
            notifyItemInserted(mSensors.size() - 1);
        }
    }

    @Override
    public boolean removeSensor(String sensorKey) {
        int previousIndex = mSensorKeys.indexOf(sensorKey);
        if (previousIndex >= 0) {
            mSensorKeys.remove(previousIndex);
            mSensors.remove(previousIndex);
            notifyItemRemoved(previousIndex);
            return true;
        }
        return false;
    }

    @Override
    public void replaceSensor(String sensorKey, ConnectableSensor sensor) {
        addSensor(sensorKey, sensor);
    }

    @Override
    public int getSensorCount() {
        return mSensors.size();
    }

    @Override
    public void addAvailableService(ExternalSensorDiscoverer.DiscoveredService service) {
        // This view doesn't track services
    }

    @Override
    public void onServiceScanComplete(String serviceId) {
        // This view doesn't track services
    }

    @Override
    public void addAvailableDevice(ExternalSensorDiscoverer.DiscoveredDevice device) {
        // This view doesn't track devices
    }

    @Override
    public void setMyDevices(List<InputDeviceSpec> device) {
        // This view doesn't track devices
    }

    @Override
    public boolean addAvailableSensor(String sensorKey, ConnectableSensor sensor) {
        // This view doesn't track devices
        return false;
    }

    public void setProgress(boolean isScanning) {
        // TODO: update UI to show scan status
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mNameView;
        private TextView mDeviceNameView;
        private ImageView mIcon;

        public ViewHolder(View itemView) {
            super(itemView);
            mNameView = (TextView) itemView.findViewById(R.id.sensor_name);
            mDeviceNameView = (TextView) itemView.findViewById(R.id.device_name);
            mIcon = (ImageView) itemView.findViewById(R.id.device_icon);
        }

        public void bind(ConnectableSensor connectableSensor) {
            SensorAppearance appearance = connectableSensor.getAppearance(
                    mSensorAppearanceProvider);
            mNameView.setText(appearance.getName(mNameView.getContext()));

            Drawable icon = appearance.getIconDrawable(mIcon.getContext());
            mIcon.setImageDrawable(icon);

            // TODO: need to get name
            mDeviceNameView.setText(connectableSensor.getDeviceAddress());
        }

        public void setIsPairing() {
            mNameView.setText(R.string.external_devices_pairing);
        }
    }

    @Override
    public String toString() {
        return "DeviceAdapter{" +
                "mIsPaired=" + mIsPaired +
                ", mSensorKeys=" + mSensorKeys.size() +
                '}';
    }
}
