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

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig;
import com.google.android.apps.forscience.whistlepunk.devicemanager.PinTypeProvider;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorTypeProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

/**
 * Represents a specification of a BLE sensor which is exposing the Making Science service.
 */
public class BleSensorSpec extends ExternalSensorSpec {
    private static final String TAG = "BleSensorSpec";
    public static final String TYPE = "bluetooth_le";

    /**
     * Human readable name of the sensor.
     */
    private String mName;
    private GoosciSensorConfig.BleSensorConfig mConfig = new GoosciSensorConfig.BleSensorConfig();

    // TODO: read in descriptor.

    public BleSensorSpec(String address, String name) {
        mName = name;
        mConfig.address = address;
        // Configure default sensor options (b/27106781)
        setSensorType(SensorTypeProvider.TYPE_RAW);
    }

    public BleSensorSpec(String name, byte[] deviceConfig) {
        mName = name;
        loadFromConfig(deviceConfig);
    }

    @Override
    public String getAddress() {
        return mConfig.address;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public byte[] getConfig() {
        return getBytes(mConfig);
    }

    @VisibleForTesting
    public void loadFromConfig(byte[] data) {
        try {
            mConfig = GoosciSensorConfig.BleSensorConfig.parseFrom(data);
        } catch (InvalidProtocolBufferNanoException e) {
            Log.e(TAG, "Could not deserialize config", e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public SensorAppearance getSensorAppearance() {
        return SensorTypeProvider.getSensorAppearance(getSensorType(), this.getName());
    }

    // Methods that access mConfig proto fields
    public @SensorTypeProvider.SensorKind int getSensorType() {
        // TODO: SensorType is stored as a string in the proto, but we should store it as an enum.
        if (TextUtils.isEmpty(mConfig.sensorType) || !TextUtils.isDigitsOnly(mConfig.sensorType)) {
            // If the type isn't set, use a custom type.
            return SensorTypeProvider.TYPE_ROTATION;
        }
        return Integer.valueOf(mConfig.sensorType);
    }

    public void setSensorType(@SensorTypeProvider.SensorKind int sensorType) {
        mConfig.sensorType = String.valueOf(sensorType);
    }

    public String getCustomPin() {
        return mConfig.customPin;
    }

    public void setCustomPin(String pin) {
        mConfig.customPin = pin;
    }

    public boolean getCustomFrequencyEnabled() {
        return mConfig.customFrequency;
    }

    public boolean getFrequencyEnabled() {
        switch (getSensorType()) {
            case SensorTypeProvider.TYPE_ROTATION:
                return true;
            case SensorTypeProvider.TYPE_RAW:
                return false;
            case SensorTypeProvider.TYPE_CUSTOM:
                return getCustomFrequencyEnabled();
        }
        complainSensorType();
        return false;
    }

    public void setCustomFrequencyEnabled(boolean frequency) {
        mConfig.customFrequency = frequency;
    }

    public String getPin() {
        switch (getSensorType()) {
            case SensorTypeProvider.TYPE_ROTATION:
            case SensorTypeProvider.TYPE_RAW:
                return PinTypeProvider.DEFAULT_PIN.toString();
            case SensorTypeProvider.TYPE_CUSTOM:
                return getCustomPin();
        }
        complainSensorType();
        return null;
    }

    public void setCustomScaleTransform(
            GoosciSensorConfig.BleSensorConfig.ScaleTransform transform) {
        mConfig.customScaleTransform = transform;
    }

    public GoosciSensorConfig.BleSensorConfig.ScaleTransform getScaleTransform() {
        switch (getSensorType()) {
            case SensorTypeProvider.TYPE_ROTATION:
                return null;
            case SensorTypeProvider.TYPE_RAW:
                return tenBitsToPercent();
            case SensorTypeProvider.TYPE_CUSTOM:
                return mConfig.customScaleTransform;
        }
        complainSensorType();
        return null;
    }

    @Override
    public String getLoggingId() {
        return super.getLoggingId() + ":" + getLogTagFromType(getSensorType());
    }

    private String getLogTagFromType(int type) {
        switch (getSensorType()) {
            case SensorTypeProvider.TYPE_ROTATION:
                return "rot";
            case SensorTypeProvider.TYPE_RAW:
                return "raw";
            case SensorTypeProvider.TYPE_CUSTOM:
                return "cus";
        }
        complainSensorType();
        return null;
    }

    private GoosciSensorConfig.BleSensorConfig.ScaleTransform tenBitsToPercent() {
        GoosciSensorConfig.BleSensorConfig.ScaleTransform transform = new GoosciSensorConfig
                .BleSensorConfig.ScaleTransform();
        transform.sourceBottom = 0;
        transform.sourceTop = 1023;
        transform.destBottom = 0;
        transform.destTop = 100;
        return transform;
    }

    @SuppressLint("WrongConstant")
    private void complainSensorType() {
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Invalid sensor type: " + getSensorType());
        }
    }

    @VisibleForTesting
    @Override
    public String toString() {
        return "BleSensorSpec{" +
                "mConfig=" + mConfig +
                '}';
    }

    @Override
    public boolean shouldShowOptionsOnConnect() {
        return true;
    }

    @VisibleForTesting
    public boolean equals(Object other) {
        // Cheating!  Only for tests
        return toString().equals(other.toString());
    }
}
