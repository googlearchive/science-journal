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
import android.preference.Preference;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciScalarInput;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.base.Preconditions;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

public class ScalarInputSpec extends ExternalSensorSpec {
    public static final String TYPE = "ScalarInput";
    private static final String EXTRA_KEY_SERVICE_ID = "serviceId";
    private static final String TAG = "ScalarInputSpec";
    private String mName;
    private GoosciScalarInput.ScalarInputConfig mConfig;

    public ScalarInputSpec(String sensorName, String serviceId, String address) {
        mName = sensorName;
        mConfig = new GoosciScalarInput.ScalarInputConfig();
        mConfig.serviceId = Preconditions.checkNotNull(serviceId);
        mConfig.address = address;
    }

    public ScalarInputSpec(String sensorName, byte[] config) {
        mName = sensorName;
        try {
            mConfig = GoosciScalarInput.ScalarInputConfig.parseFrom(config);
        } catch (InvalidProtocolBufferNanoException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "error parsing config", e);
            }
        }
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
        return mConfig.address;
    }

    // TODO: implement all!
    @Override
    public SensorAppearance getSensorAppearance() {
        // TODO: allow no name str id
        // TODO: better icon?
        int drawableId = R.drawable.ic_sensor_raw_white_24dp;
        return new SensorAppearance(0, drawableId) {
            @Override
            public String getName(Context context) {
                return mName;
            }
        };
    }

    @Override
    public byte[] getConfig() {
        return getBytes(mConfig);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    protected void loadFromConfig(byte[] data) {

    }

    public static void addServiceId(Preference pref, String serviceId) {
        pref.getExtras().putString(EXTRA_KEY_SERVICE_ID, serviceId);
    }

    public String getServiceId() {
        return mConfig.serviceId;
    }

    public static String getServiceId(Preference preference) {
        return preference.getExtras().getString(EXTRA_KEY_SERVICE_ID);
    }
}
