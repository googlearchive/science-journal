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