package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.os.Parcel;
import android.os.Parcelable;

public class SensorAppearanceResources implements Parcelable {
    public int iconId = -1;
    public String units = "";
    public String shortDescription = "";

    public SensorAppearanceResources() {

    }

    // TODO(saff): test round-trip parcel!
    protected SensorAppearanceResources(Parcel in) {
        iconId = in.readInt();
        units = in.readString();
        shortDescription = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(iconId);
        dest.writeString(units);
        dest.writeString(shortDescription);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SensorAppearanceResources> CREATOR =
            new Creator<SensorAppearanceResources>() {
                @Override
                public SensorAppearanceResources createFromParcel(Parcel in) {
                    return new SensorAppearanceResources(in);
                }

                @Override
                public SensorAppearanceResources[] newArray(int size) {
                    return new SensorAppearanceResources[size];
                }
            };
}
