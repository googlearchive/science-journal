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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.html.HtmlEscapers;

/**
 * An "ExternalSensorSpec" that identifies a device, which is, for our purposes, an abstract
 * grouping of connectable sensors that live on the same physical piece of hardware, in order to
 * group things helpfully for users that live in the physical world.
 *
 * An input device, like an external sensor, has a name, address, and appearance, but it does
 * not allow one to actually create SensorChoices directly; SensorChoices are created from actual
 * sensor specs.
 */
public class InputDeviceSpec extends ExternalSensorSpec {
    public static final String TYPE = "InputDevice";
    private static final String TAG = "InputDeviceSpec";

    /**
     * The "address" given to the device on which the app is running.
     */
    public static final String BUILT_IN_DEVICE_ADDRESS = "BUILT_IN_DEVICE";

    private String mProviderType;
    private String mDeviceAddress;
    private String mName;

    // TODO: needs a proto to survive round-trip!
    public InputDeviceSpec(String providerType, String deviceAddress, String deviceName) {
        mProviderType = providerType;
        mDeviceAddress = deviceAddress;
        mName = deviceName;
    }

    /**
     * Given two arbitrary strings representing parts of an address, join them in a
     * guaranteed-unique way, by HTML-escaping both parts and joining them with '&'
     */
    @NonNull
    public static String joinAddresses(String firstPart, String secondPart) {
        return escape(firstPart) + "&" + escape(secondPart);
    }

    @Override
    public SensorAppearance getSensorAppearance() {
        return new EmptySensorAppearance() {
            @Override
            public Drawable getIconDrawable(Context context) {
                if (mDeviceAddress.equals(BUILT_IN_DEVICE_ADDRESS)) {
                    // TODO: this isn't right (b/32579791)
                    return context.getDrawable(android.R.drawable.ic_menu_call);
                }
                return null;
            }

            @Override
            public String getName(Context context) {
                return InputDeviceSpec.this.getName();
            }
        };
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
    public String getAddress() {
        return joinAddresses("DEVICE", mDeviceAddress);
    }

    public static String escape(String string) {
        return HtmlEscapers.htmlEscaper().escape(string);
    }

    @Override
    public byte[] getConfig() {
        return new byte[0];
    }

    @Override
    public boolean shouldShowOptionsOnConnect() {
        return false;
    }

    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    public static InputDeviceSpec builtInDevice(Context context) {
        return new InputDeviceSpec(TYPE, BUILT_IN_DEVICE_ADDRESS,
                context.getString(R.string.phone_sensors));
    }

    public String getProviderType() {
        return mProviderType;
    }

    @Override
    public String getGlobalDeviceAddress() {
        return InputDeviceSpec.joinAddresses(getProviderType(), getDeviceAddress());
    }
}
