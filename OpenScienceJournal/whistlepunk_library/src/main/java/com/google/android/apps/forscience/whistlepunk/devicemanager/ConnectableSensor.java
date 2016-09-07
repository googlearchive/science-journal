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
