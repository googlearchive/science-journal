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

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a specification of an external sensor, including its name and address.  Subclasses
 * may include additional options.
 */
public abstract class ExternalSensorSpec implements Parcelable {
    private static final String TAG = "ExternalSensorSpec";

    protected ExternalSensorSpec(Parcel in) {
        // do nothing
    }

    protected ExternalSensorSpec() {
        // do nothing
    }

    public static byte[] getBytes(MessageNano config) {
        byte[] output = new byte[config.getSerializedSize()];

        CodedOutputByteBufferNano buffer = CodedOutputByteBufferNano.newInstance(output);
        try {
            config.writeTo(buffer);
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Could not serialize config", e);
            }
        }
        return output;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // do nothing
    }

    /**
     * Returns a suggested sensorId for the given spec.
     * Answer depends on spec address, type, and name, but _not_ configuration.
     */
    public static String getSensorId(ExternalSensorSpec spec, int suffix) {
        return spec.getType() + "-" + spec.getAddress() + "-" + spec.getName() + "-" + suffix;
    }

    /**
     * Returns redacted info suitable for logging.
     */
    public String getLoggingId() {
        return getType();
    }

    public abstract String getName();

    public abstract String getType();

    /**
     * @return the address at which this sensor sits.  This is opaque to whistlepunk; each sensor
     * that an
     * {@link com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer}
     * returns must have a unique address, but that address need not have any specific semantic
     * relationship to how the sensor is physically addressed.
     */
    public abstract String getAddress();

    public abstract SensorAppearance getSensorAppearance();

    /**
     * Returns a serialized version of internal state of the sensor, suitable for long term storage.
     */
    @VisibleForTesting
    public abstract byte[] getConfig();

    /**
     * @return true iff {@code spec} is the same type, at the same address, and the same
     * configuration.
     */
    public final boolean isSameSensorAndSpec(ExternalSensorSpec spec) {
        return isSameType(spec) && Arrays.equals(spec.getConfig(), getConfig());
    }

    private boolean isSameType(ExternalSensorSpec spec) {
        return spec != null && Objects.equals(spec.getType(), getType());
    }

    /**
     * @return true iff {@code spec} is the same type, at the same address (but potentially
     * different configuration.
     */
    public boolean isSameSensor(ExternalSensorSpec spec) {
        return isSameType(spec) && Objects.equals(spec.getAddress(), getAddress());
    }

    @Override
    public String toString() {
        return "ExternalSensorSpec(" + getType() + "," + getAddress() + ")";
    }
}
