package com.google.android.apps.forscience.whistlepunk.wireapi;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;

import java.util.Collection;
import java.util.Map;

public class TransportableSensorOptions implements Parcelable {
    private Map<String, String> mValues;

    public TransportableSensorOptions(Map<String, String> values) {
        mValues = values;
    }

    protected TransportableSensorOptions(Parcel in) {
        mValues = new ArrayMap<>();
        in.readMap(mValues, getClass().getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeMap(mValues);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<TransportableSensorOptions> CREATOR =
            new Creator<TransportableSensorOptions>() {
                @Override
                public TransportableSensorOptions createFromParcel(Parcel in) {
                    return new TransportableSensorOptions(in);
                }

                @Override
                public TransportableSensorOptions[] newArray(int size) {
                    return new TransportableSensorOptions[size];
                }
            };

    public String getString(String key, String defaultValue) {
        if (mValues.containsKey(key)) {
            return mValues.get(key);
        } else {
            return defaultValue;
        }
    }

    public Collection<String> getWrittenKeys() {
        return mValues.keySet();
    }
}
