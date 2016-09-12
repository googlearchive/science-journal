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

import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.base.Preconditions;

public class ConnectableSensor {
    private ExternalSensorSpec mSpec;

    private String mConnectedSensorId;

    public static ConnectableSensor connected(ExternalSensorSpec spec, String connectedSensorId) {
        return new ConnectableSensor(spec, connectedSensorId);
    }

    public static ConnectableSensor disconnected(ExternalSensorSpec spec) {
        return new ConnectableSensor(spec, null);
    }

    /**
     * @param spec   specification of the sensor
     * @param paired non-null if we've already paired with this sensor, and so there's already a
     *               sensorId in the database for this sensor.  Otherwise, it's null; we could
     *               connect, but a sensorId would need to be created if we did
     */
    private ConnectableSensor(ExternalSensorSpec spec, String connectedSensorId) {
        mSpec = Preconditions.checkNotNull(spec);
        mConnectedSensorId = connectedSensorId;
    }

    public boolean isPaired() {
        return mConnectedSensorId != null;
    }

    public ExternalSensorSpec getSpec() {
        return mSpec;
    }

    public String getName() {
        return mSpec.getName();
    }

    public String getAddress() {
        return mSpec.getAddress();
    }

    public String getConnectedSensorId() {
        return mConnectedSensorId;
    }

    @Override
    public String toString() {
        return "ConnectableSensor{" +
                "mSpec=" + mSpec +
                ", mConnectedSensorId='" + mConnectedSensorId + '\'' +
                '}';
    }
}
