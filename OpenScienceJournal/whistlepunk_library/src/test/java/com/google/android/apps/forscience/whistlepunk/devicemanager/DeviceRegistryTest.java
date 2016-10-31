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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;

import org.junit.Test;

public class DeviceRegistryTest {
    @Test
    public void differentTypesAreDifferentDevices() {
        DeviceRegistry registry = new DeviceRegistry(null);
        registry.addDevice("type1", new InputDeviceSpec("address", "name1"));
        registry.addDevice("type2", new InputDeviceSpec("address", "name2"));
        assertEquals("name1", registry.getDevice("type1", "address").getName());
        assertEquals("name2", registry.getDevice("type2", "address").getName());
    }

    @Test public void generateSyntheticDevice() {
        DeviceRegistry registry = new DeviceRegistry(null);
        assertEquals("name", registry.getDevice(new BleSensorSpec("address", "name")).getName());
    }

    @Test public void useBuiltInDevice() {
        String name = Arbitrary.string();
        DeviceRegistry registry = new DeviceRegistry(new InputDeviceSpec("address", name));
        assertEquals(name, registry.getDevice(null).getName());
    }
}