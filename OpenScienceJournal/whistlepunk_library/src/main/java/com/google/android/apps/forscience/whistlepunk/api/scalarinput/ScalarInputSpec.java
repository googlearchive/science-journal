package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

public class ScalarInputSpec extends ExternalSensorSpec {
    public static final String TYPE = "ScalarInput";

    // TODO: implement all!

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getAddress() {
        return null;
    }

    @Override
    public SensorAppearance getSensorAppearance() {
        return null;
    }

    @Override
    public byte[] getConfig() {
        return new byte[0];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    protected void loadFromConfig(byte[] data) {

    }
}
