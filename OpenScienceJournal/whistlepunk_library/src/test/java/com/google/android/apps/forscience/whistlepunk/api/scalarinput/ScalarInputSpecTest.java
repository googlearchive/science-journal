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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;

import org.junit.Test;

public class ScalarInputSpecTest {
    @Test
    public void appearance() {
        String name = Arbitrary.string();
        SensorAppearance appearance = new ScalarInputSpec(name, "serviceId", "address", null, null,
                true, 1).getSensorAppearance();
        assertNotNull(appearance);
        assertEquals(name, appearance.getName(null));
    }

    @Test
    public void roundTripConfig() {
        String sensorName = Arbitrary.string();
        String serviceId = "serviceId";
        String address = Arbitrary.string();
        String loggingId = "loggingId";
        SensorAppearanceResources appearance = new SensorAppearanceResources();
        appearance.iconId = Arbitrary.integer();
        appearance.shortDescription = Arbitrary.string();
        appearance.units = Arbitrary.string();
        ScalarInputSpec spec = new ScalarInputSpec(sensorName, serviceId, address, loggingId,
                appearance, true, 1);
        ScalarInputSpec spec2 = roundTripConfig(spec);
        assertEquals(sensorName, spec2.getName());
        assertEquals(serviceId, spec2.getServiceId());
        assertEquals(address, spec2.getSensorAddressInService());
        assertEquals("serviceId&loggingId", spec2.getLoggingId());
        SensorAppearance appearance2 = spec2.getSensorAppearance();

        assertEquals(appearance.shortDescription, appearance2.getShortDescription(null));
        assertEquals(appearance.units, appearance2.getUnits(null));
    }

    @Test
    public void isSame() {
        ScalarInputSpec spec11 = new ScalarInputSpec("name", "service1", "address1", null, null,
                true, 1);
        ScalarInputSpec spec11b = new ScalarInputSpec("name2", "service1", "address1", null, null,
                true, 1);
        ScalarInputSpec spec12 = new ScalarInputSpec("name", "service1", "address2", null, null,
                true, 1);
        ScalarInputSpec spec21 = new ScalarInputSpec("name", "service2", "address1", null, null,
                true, 1);
        BleSensorSpec bleSpec = new BleSensorSpec("address1", "name");
        assertTrue(spec11.isSameSensorAndSpec(spec11));
        assertTrue(spec11.isSameSensorAndSpec(spec11b));
        assertFalse(spec11.isSameSensorAndSpec(spec12));
        assertFalse(spec11.isSameSensorAndSpec(spec21));
        assertFalse(spec11.isSameSensorAndSpec(bleSpec));
        assertFalse(spec11.isSameSensorAndSpec(null));
    }

    @Test
    public void addressIncludesService() {
        ScalarInputSpec spec11 = new ScalarInputSpec("name", "service1", "address1", null, null,
                true, 1);
        ScalarInputSpec spec11b = new ScalarInputSpec("name2", "service1", "address1", null, null,
                true, 1);
        ScalarInputSpec spec12 = new ScalarInputSpec("name", "service1", "address2", null, null,
                true, 1);
        ScalarInputSpec spec21 = new ScalarInputSpec("name", "service2", "address1", null, null,
                true, 1);
        BleSensorSpec bleSpec = new BleSensorSpec("address1", "name");
        assertTrue(spec11.isSameSensor(spec11));
        assertTrue(spec11.isSameSensor(spec11b));
        assertFalse(spec11.isSameSensor(spec12));
        assertFalse(spec11.isSameSensor(spec21));
        assertFalse(spec11.isSameSensor(bleSpec));
        assertFalse(spec11.isSameSensor(null));
    }

    @Test
    public void shouldShowOptionsOnConnect() {
        ScalarInputSpec specYes = roundTripConfig(
                new ScalarInputSpec("name", "serviceId", "address", "loggingId", null, true, 1));
        assertTrue(specYes.shouldShowOptionsOnConnect());

        ScalarInputSpec specNo = roundTripConfig(
                new ScalarInputSpec("name", "serviceId", "address", "loggingId", null, false, 1));
        assertFalse(specNo.shouldShowOptionsOnConnect());
    }

    @Test public void iconsChange() {
        ScalarInputSpec spec1 = roundTripConfig(
                new ScalarInputSpec("name", "serviceId", "address", "loggingId", null, true, 0));
        ScalarInputSpec spec2 = roundTripConfig(
                new ScalarInputSpec("name", "serviceId", "address", "loggingId", null, true, 1));
        ScalarInputSpec spec3 = roundTripConfig(
                new ScalarInputSpec("name", "serviceId", "address", "loggingId", null, true, 2));
        ScalarInputSpec spec4 = roundTripConfig(
                new ScalarInputSpec("name", "serviceId", "address", "loggingId", null, true, 3));
        ScalarInputSpec spec5 = roundTripConfig(
                new ScalarInputSpec("name", "serviceId", "address", "loggingId", null, true, 4));
        ScalarInputSpec spec6 = roundTripConfig(
                new ScalarInputSpec("name", "serviceId", "address", "loggingId", null, true, 5));
        assertEquals(R.drawable.ic_api_01_white_24dp, spec1.getDefaultIconId());
        assertEquals(R.drawable.ic_api_02_white_24dp, spec2.getDefaultIconId());
        assertEquals(R.drawable.ic_api_03_white_24dp, spec3.getDefaultIconId());
        assertEquals(R.drawable.ic_api_04_white_24dp, spec4.getDefaultIconId());
        assertEquals(R.drawable.ic_api_01_white_24dp, spec1.getDefaultIconId());
        assertEquals(R.drawable.ic_api_02_white_24dp, spec2.getDefaultIconId());
    }

    @NonNull
    private ScalarInputSpec roundTripConfig(ScalarInputSpec spec) {
        return new ScalarInputSpec(spec.getName(), spec.getConfig());
    }
}