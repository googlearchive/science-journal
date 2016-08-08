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

import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.common.annotations.VisibleForTesting;

/**
 * Represents a specification of an external sensor, including its name and address.  Subclasses
 * may include additional options.
 */
public abstract class ExternalSensorSpec implements Parcelable {
    protected ExternalSensorSpec(Parcel in) {
        // do nothing
    }

    protected ExternalSensorSpec() {
        // do nothing
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

    public abstract String getAddress();

    public abstract SensorAppearance getSensorAppearance();

    /**
     * Returns a serialized version of internal state of the sensor, suitable for long term storage.
     */
    @VisibleForTesting
    public abstract byte[] getConfig();

    /**
     * Loads internal sensor data from the database.
     */
    abstract void loadFromConfig(byte[] data);
}
