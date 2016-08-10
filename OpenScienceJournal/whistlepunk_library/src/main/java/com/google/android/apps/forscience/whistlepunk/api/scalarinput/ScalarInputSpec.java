package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

public class ScalarInputSpec extends ExternalSensorSpec {
    public static final String TYPE = "ScalarInput";
    private String mName;
    private String mAddress;

    public ScalarInputSpec(String name, String address) {
        mName = name;
        mAddress = address;
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
        return mAddress;
    }

    // TODO: implement all!
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
