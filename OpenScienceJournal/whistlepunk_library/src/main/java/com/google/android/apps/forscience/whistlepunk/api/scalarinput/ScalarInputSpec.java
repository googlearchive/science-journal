package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

import java.util.Arrays;

public class ScalarInputSpec extends ExternalSensorSpec {
    public static final String TYPE = "ScalarInput";
    private String mName;
    private String mAddress;

    public ScalarInputSpec(String sensorName, String address) {
        mName = sensorName;
        mAddress = address;
    }

    public ScalarInputSpec(String sensorName, byte[] config) {
        this(sensorName, new String(config));
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
        return mAddress.getBytes();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    protected void loadFromConfig(byte[] data) {

    }
}
