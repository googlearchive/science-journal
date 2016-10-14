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

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.test.AndroidTestCase;

import java.util.HashMap;

public class ScalarSensorServiceFinderTest extends AndroidTestCase {
    public void testUseFlattenedComponentName() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        ServiceConnection connection = ScalarSensorServiceFinder.makeServiceConnection(
                new HashMap<String, ServiceConnection>(), "packageName", callbacks, null);
        connection.onServiceConnected(new ComponentName("packageName", "packageName.MyClass"),
                new TestSensorDiscoverer("serviceName"));
        assertEquals("packageName/.MyClass", callbacks.serviceId);
    }

    public void testMetadataOverride() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        Bundle metaData = new Bundle();
        metaData.putString(ScalarSensorServiceFinder.METADATA_KEY_CLASS_NAME_OVERRIDE, "YourClass");
        ServiceConnection connection = ScalarSensorServiceFinder.makeServiceConnection(
                new HashMap<String, ServiceConnection>(), "packageName", callbacks,
                metaData);
        connection.onServiceConnected(new ComponentName("packageName", "packageName.MyClass"),
                new TestSensorDiscoverer("serviceName"));
        assertEquals("packageName/YourClass", callbacks.serviceId);
    }

    private static class RecordingCallbacks implements AppDiscoveryCallbacks {
        public String serviceId;

        @Override
        public void onServiceFound(String serviceId, ISensorDiscoverer service) {
            this.serviceId = serviceId;
        }

        @Override
        public void onDiscoveryDone() {

        }
    }
}