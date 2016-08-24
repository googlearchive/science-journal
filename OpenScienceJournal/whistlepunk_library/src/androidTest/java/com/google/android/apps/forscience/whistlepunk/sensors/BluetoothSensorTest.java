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

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.MemorySensorHistoryStorage;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorTypeProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FakeBleClient;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;

public class BluetoothSensorTest extends AndroidTestCase {
    private static final BluetoothSensor.BleServiceSpec SPEC =
            BluetoothSensor.ANNING_SERVICE_SPEC;

    public void testGetFrequency() {
        BleSensorSpec sensor = new BleSensorSpec("address", "name");
        sensor.setSensorType(SensorTypeProvider.TYPE_CUSTOM);

        sensor.setCustomFrequencyEnabled(true);
        assertTrue(new BluetoothSensor("sensorId", sensor, SPEC).getDefaultFrequencyChecked());
        sensor.setCustomFrequencyEnabled(false);
        assertFalse(new BluetoothSensor("sensorId", sensor, SPEC).getDefaultFrequencyChecked());
    }

    public void testGetScaleTransform() {
        BleSensorSpec sensor = new BleSensorSpec("address", "name");

        sensor.setSensorType(SensorTypeProvider.TYPE_CUSTOM);
        assertNull(new BluetoothSensor("sensorId", sensor, SPEC).getDefaultScaleTransform());

        GoosciSensorConfig.BleSensorConfig.ScaleTransform transform = new GoosciSensorConfig
                .BleSensorConfig.ScaleTransform();
        sensor.setCustomScaleTransform(transform);
        assertEquals(transform,
                new BluetoothSensor("sensorId", sensor, SPEC).getDefaultScaleTransform());
    }

    public void testConnect() {
        BleSensorSpec sensor = new BleSensorSpec("address", "name");
        FakeBleClient bleClient = new FakeBleClient(getContext());
        bleClient.expectedAddress = "address";
        MemorySensorEnvironment environment = new MemorySensorEnvironment(
                new InMemorySensorDatabase().makeSimpleRecordingController(), bleClient,
                new MemorySensorHistoryStorage());
        SensorRecorder recorder = new BluetoothSensor("sensorId", sensor, SPEC).createRecorder(
                getContext(), new RecordingSensorObserver(), new StubStatusListener(), environment);
        assertEquals(null, bleClient.mostRecentAddress);
        recorder.startObserving();
        assertEquals("address", bleClient.mostRecentAddress);
    }

    public void testVersionDecodeMajor() {
        BluetoothSensor.BleProtocolVersion versionDecoder;

        byte[] version_1_0_0 = {0x00, 0x08};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_1_0_0);
        assertEquals(1, versionDecoder.getMajorVersion());
        assertEquals(0, versionDecoder.getMinorVersion());
        assertEquals(0, versionDecoder.getPatchVersion());

        byte[] version_2_0_0 = {0x00, 0x10};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_2_0_0);
        assertEquals(2, versionDecoder.getMajorVersion());
        assertEquals(0, versionDecoder.getMinorVersion());
        assertEquals(0, versionDecoder.getPatchVersion());

        byte[] version_MAX_0_0 = {0x00, -0x08};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_MAX_0_0);
        assertEquals(versionDecoder.getMaxMajorVersion(), versionDecoder.getMajorVersion());
        assertEquals(0, versionDecoder.getMinorVersion());
        assertEquals(0, versionDecoder.getPatchVersion());
    }

    public void testVersionDecodeMinor() {
        BluetoothSensor.BleProtocolVersion versionDecoder;

        byte[] version_0_1_0 = {0x40, 0x00};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_0_1_0);
        assertEquals(0, versionDecoder.getMajorVersion());
        assertEquals(1, versionDecoder.getMinorVersion());
        assertEquals(0, versionDecoder.getPatchVersion());

        byte[] version_0_2_0 = {-0x80, 0x00};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_0_2_0);
        assertEquals(0, versionDecoder.getMajorVersion());
        assertEquals(2, versionDecoder.getMinorVersion());
        assertEquals(0, versionDecoder.getPatchVersion());

        byte[] version_0_MAX_0 = {-0x40, 0x07};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_0_MAX_0);
        assertEquals(0, versionDecoder.getMajorVersion());
        assertEquals(versionDecoder.getMaxMinorVersion(), versionDecoder.getMinorVersion());
        assertEquals(0, versionDecoder.getPatchVersion());
    }

    public void testVersionDecodePatch() {
        BluetoothSensor.BleProtocolVersion versionDecoder;

        byte[] version_0_0_1 = {0x01, 0x00};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_0_0_1);
        assertEquals(0, versionDecoder.getMajorVersion());
        assertEquals(0, versionDecoder.getMinorVersion());
        assertEquals(1, versionDecoder.getPatchVersion());

        byte[] version_0_0_2 = {0x02, 0x00};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_0_0_2);
        assertEquals(0, versionDecoder.getMajorVersion());
        assertEquals(0, versionDecoder.getMinorVersion());
        assertEquals(2, versionDecoder.getPatchVersion());

        byte[] version_0_0_MAX = {0x3F, 0x00};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_0_0_MAX);
        assertEquals(0, versionDecoder.getMajorVersion());
        assertEquals(0, versionDecoder.getMinorVersion());
        assertEquals(versionDecoder.getMaxPatchVersion(), versionDecoder.getPatchVersion());
    }

    public void testVersionDecodeAll() {
        BluetoothSensor.BleProtocolVersion versionDecoder;

        byte[] version_1_1_1 = {0x41, 0x08};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_1_1_1);
        assertEquals(1, versionDecoder.getMajorVersion());
        assertEquals(1, versionDecoder.getMinorVersion());
        assertEquals(1, versionDecoder.getPatchVersion());

        byte[] version_MAX_MAX_MAX = {-0x01, -0x01};
        versionDecoder = new BluetoothSensor.BleProtocolVersion(version_MAX_MAX_MAX);
        assertEquals(versionDecoder.getMaxMajorVersion(), versionDecoder.getMajorVersion());
        assertEquals(versionDecoder.getMaxMinorVersion(), versionDecoder.getMinorVersion());
        assertEquals(versionDecoder.getMaxPatchVersion(), versionDecoder.getPatchVersion());
    }

}