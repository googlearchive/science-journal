package com.google.android.apps.forscience.whistlepunk.metadata;

import android.os.Parcel;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;

public class ExternalSensorSpecTest extends AndroidTestCase {
    public void testExternalSensorSpecParcelable() {
        String name = Arbitrary.string();
        String address = Arbitrary.string();
        String pin = Arbitrary.string();

        BleSensorSpec writtenSpec = new BleSensorSpec(address, name);
        writtenSpec.setCustomPin(pin);

        Parcel writtenParcel = Parcel.obtain();
        writtenParcel.writeParcelable(writtenSpec, 0);
        byte[] bytes = writtenParcel.marshall();
        writtenParcel.recycle();

        Parcel readParcel = Parcel.obtain();
        readParcel.unmarshall(bytes, 0, bytes.length);
        readParcel.setDataPosition(0);
        BleSensorSpec readSpec = readParcel.readParcelable(BleSensorSpec.class.getClassLoader());
        readParcel.recycle();

        assertEquals(name, readSpec.getName());
        assertEquals(address, readSpec.getAddress());
        assertEquals(pin, readSpec.getCustomPin());
    }
}