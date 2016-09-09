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

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;

import org.junit.Test;

public class ScalarInputSpecTest {
    @Test
    public void appearance() {
        String name = Arbitrary.string();
        SensorAppearance appearance = new ScalarInputSpec(name, "serviceId",
                "address").getSensorAppearance();
        assertNotNull(appearance);
        assertEquals(name, appearance.getName(null));
    }

    @Test public void roundTripConfig() {
        String sensorName = Arbitrary.string();
        String serviceId = Arbitrary.string();
        String address = Arbitrary.string();
        ScalarInputSpec spec = new ScalarInputSpec(sensorName, serviceId, address);
        ScalarInputSpec spec2 = new ScalarInputSpec(sensorName, spec.getConfig());
        assertEquals(sensorName, spec2.getName());
        assertEquals(serviceId, spec2.getServiceId());
        assertEquals(address, spec2.getSensorAddressInService());
    }

    @Test public void isSame() {
        ScalarInputSpec spec11 = new ScalarInputSpec("name", "service1", "address1");
        ScalarInputSpec spec11b = new ScalarInputSpec("name2", "service1", "address1");
        ScalarInputSpec spec12 = new ScalarInputSpec("name", "service1", "address2");
        ScalarInputSpec spec21 = new ScalarInputSpec("name", "service2", "address1");
        BleSensorSpec bleSpec = new BleSensorSpec("address1", "name");
        assertTrue(spec11.isSameSensorAndSpec(spec11));
        assertTrue(spec11.isSameSensorAndSpec(spec11b));
        assertFalse(spec11.isSameSensorAndSpec(spec12));
        assertFalse(spec11.isSameSensorAndSpec(spec21));
        assertFalse(spec11.isSameSensorAndSpec(bleSpec));
        assertFalse(spec11.isSameSensorAndSpec(null));
    }

    @Test public void addressIncludesService() {
        ScalarInputSpec spec11 = new ScalarInputSpec("name", "service1", "address1");
        ScalarInputSpec spec11b = new ScalarInputSpec("name2", "service1", "address1");
        ScalarInputSpec spec12 = new ScalarInputSpec("name", "service1", "address2");
        ScalarInputSpec spec21 = new ScalarInputSpec("name", "service2", "address1");
        BleSensorSpec bleSpec = new BleSensorSpec("address1", "name");
        assertTrue(spec11.isSameSensor(spec11));
        assertTrue(spec11.isSameSensor(spec11b));
        assertFalse(spec11.isSameSensor(spec12));
        assertFalse(spec11.isSameSensor(spec21));
        assertFalse(spec11.isSameSensor(bleSpec));
        assertFalse(spec11.isSameSensor(null));
    }
}