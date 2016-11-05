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
import android.support.annotation.Nullable;

import com.bignerdranch.expandablerecyclerview.Model.ParentListItem;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ExpandableRecyclerView Model object that holds the specification of a device and the keys of all
 * of the sensors on that device.
 *
 * As currently used, a device item is created, and sensors are added to it.  If we start to
 * detect and indicate that sensors have been removed from a device, that will require some
 * additional code.
 */
public class DeviceParentListItem implements ParentListItem {
    private InputDeviceSpec mSpec;
    private final SensorAppearanceProvider mAppearanceProvider;
    private List<String> mSensorKeys = new ArrayList<>();
    private boolean mIsNowExpanded = true;

    public DeviceParentListItem(InputDeviceSpec spec, SensorAppearanceProvider appearanceProvider,
            boolean startsExpanded) {
        mSpec = Preconditions.checkNotNull(spec);
        mAppearanceProvider = appearanceProvider;
        mIsNowExpanded = startsExpanded;
    }

    @Override
    public List<?> getChildItemList() {
        return mSensorKeys;
    }

    @Override
    public boolean isInitiallyExpanded() {
        return mIsNowExpanded;
    }

    public boolean isCurrentlyExpanded() {
        return mIsNowExpanded;
    }

    public String getDeviceName() {
        return mSpec.getName();
    }

    public boolean isDevice(InputDeviceSpec device) {
        return mSpec.isSameSensor(device);
    }

    public void addSensor(String key) {
        mSensorKeys.add(Preconditions.checkNotNull(key));
    }

    public String getSensorKey(int i) {
        return mSensorKeys.get(i);
    }

    public int sensorIndexOf(String sensorKey) {
        return mSensorKeys.indexOf(sensorKey);
    }

    boolean isPhoneSensorParent(DeviceRegistry deviceRegistry) {
        return isDevice(deviceRegistry.getBuiltInDevice());
    }

    public Drawable getDeviceIcon(Context context, Map<String, ConnectableSensor> sensorMap) {
        Drawable selfDrawable = mSpec.getSensorAppearance().getIconDrawable(context);
        if (selfDrawable != null) {
            return selfDrawable;
        }
        return getArbitrarySensorIcon(context, sensorMap);
    }

    /**
     * If the device does not have an icon, choose an arbitrary icon from the sensors on the device
     * to represent the device (It will be fairly rare that a device both has no individual icon,
     * and multiple sensors with different icons, so this seems a reasonable heuristic.)
     */
    @Nullable
    private Drawable getArbitrarySensorIcon(Context context,
            Map<String, ConnectableSensor> sensorMap) {
        for (String sensorKey : mSensorKeys) {
            ConnectableSensor sensor = sensorMap.get(sensorKey);
            Drawable sensorDrawable = sensor.getAppearance(mAppearanceProvider).getIconDrawable(
                    context);
            if (sensorDrawable != null) {
                return sensorDrawable;
            }
        }
        return null;
    }

    public InputDeviceSpec getSpec() {
        return mSpec;
    }

    public void setIsCurrentlyExpanded(boolean isNowExpanded) {
        mIsNowExpanded = isNowExpanded;
    }
}
