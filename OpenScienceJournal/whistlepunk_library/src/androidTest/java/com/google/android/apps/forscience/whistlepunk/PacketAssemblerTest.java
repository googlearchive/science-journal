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

package com.google.android.apps.forscience.whistlepunk;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;


public class PacketAssemblerTest extends AndroidTestCase {
    final private int testValue = 314159;
    final private int smallTestValue = 1;
    final private int testTime = 42;

    final private float floatTestValue = 10f;
    final private int intTestValue = 20;
    final private boolean digitalTestValue = false;
    final private int analogTestValue = 30;
    final private String stringTestValue = "Test";

    final private int[] packetStream = {5, 10, 15, 20, 25, 30};

    private class TestSystemClock implements Clock {
        @Override
        public long getNow() {
            return testTime;
        }
    };

    private static class Point {
        long x;
        double y;

        public Point(long x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class TestStreamConsumer implements StreamConsumer {
        private List<Point> mPoints = new ArrayList<>();;

        @Override
        public void addData(final long timestampMillis, final double value) {
            mPoints.add(new Point(timestampMillis, value));
        }

        public List<Point> getPoints() { return mPoints; }
    };

    private class GoosciSensorBuilder {
        private GoosciSensor.SensorData mSensorData;
        private GoosciSensor.Data mData;

        public GoosciSensorBuilder() {
            mSensorData = new GoosciSensor.SensorData();
            mData = new GoosciSensor.Data();
            mData.pin = new GoosciSensor.Pin();
        }

        public GoosciSensorBuilder setAnalogPin() {
            GoosciSensor.AnalogPin pin = new GoosciSensor.AnalogPin();
            mData.pin.setAnalogPin(pin);
            return this;
        }

        public GoosciSensorBuilder setDigitalPin() {
            GoosciSensor.DigitalPin pin = new GoosciSensor.DigitalPin();
            mData.pin.setDigitalPin(pin);
            return this;
        }

        public GoosciSensorBuilder setVirtualPin() {
            GoosciSensor.VirtualPin pin = new GoosciSensor.VirtualPin();
            mData.pin.setVirtualPin(pin);
            return this;
        }

        public GoosciSensorBuilder setAnalogValue(int value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.AnalogValue av = new GoosciSensor.AnalogValue();
            av.value = value;
            mData.setAnalogValue(av);
            return this;
        }

        public GoosciSensorBuilder setDigitalValue(boolean value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.DigitalValue dv = new GoosciSensor.DigitalValue();
            dv.value = value;
            mData.setDigitalValue(dv);
            return this;
        }

        public GoosciSensorBuilder setIntValue(int value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.IntValue iv = new GoosciSensor.IntValue();
            iv.value = value;
            mData.setIntValue(iv);
            return this;
        }

        public GoosciSensorBuilder setFloatValue(float value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.FloatValue fv = new GoosciSensor.FloatValue();
            fv.value = value;
            mData.setFloatValue(fv);
            return this;
        }

        public GoosciSensorBuilder setStringValue(String value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.StringValue sv = new GoosciSensor.StringValue();
            sv.value = value;
            mData.setStringValue(sv);
            return this;
        }

        public byte[] toByteArray() {
            mSensorData.setData(mData);
            return GoosciSensor.SensorData.toByteArray(mSensorData);
        }
    }

    private static void fakeFramedSensorData(PacketAssembler pa, byte[] value, int chunksize, int expectedNumPackets) {
        int length = (int) Math.ceil(value.length / (double)chunksize);
        assertTrue(length == expectedNumPackets);

        int start = 0;
        for (int i = 0; i < length; ++i) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int l = chunksize;
            boolean last = i == length-1;
            if (last) l=value.length % chunksize;
            outputStream.write((byte) l);
            outputStream.write((byte)(last ? 1 : 0));
            for (int j = 0; j < l; ++j)
                outputStream.write(value[start++]);

            pa.append(outputStream.toByteArray());
        }
        assertEquals(value.length, start);
    }

    private PacketAssembler createPacketAssembler(TestStreamConsumer tsc) {
        final BleSensorSpec bs = new BleSensorSpec("F7:83:CE:FE:56:C2", "name");
        final BluetoothSensor s = new BluetoothSensor("sensorId", bs,
                BluetoothSensor.ANNING_SERVICE_SPEC);
        final Clock cl = new TestSystemClock();
        final StubStatusListener ssl = new StubStatusListener();
        return new PacketAssembler(tsc, cl, s, ssl);
    }

    public void testSinglePacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogValue(smallTestValue, 0)
                .setAnalogPin()
                .toByteArray();

        // Test should work for any value above 0; but we'll use the size that BLE enforces.
        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double)smallTestValue);
    }

    public void testMultiPacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogValue(testValue, 0)
                .setAnalogPin()
                .toByteArray();

        // Test should work for any value above 0; but we'll use the size that BLE enforces.
        int chunksize = value.length - 1;

        fakeFramedSensorData(pa, value, chunksize, 2);

        List<Point> points = tsc.getPoints();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double) testValue);
    }

    public void testFloatValuePacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setFloatValue(floatTestValue, 0)
                .setVirtualPin()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double)floatTestValue);
    }

    public void testIntValuePacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setIntValue(intTestValue, 0)
                .setVirtualPin()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double)intTestValue);
    }

    public void testAnalogValuePacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogValue(analogTestValue, 0)
                .setAnalogPin()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double) analogTestValue);
    }

    public void testDigitalValuePacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setDigitalValue(digitalTestValue, 0)
                .setDigitalPin()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, pa.booleanToDigital(digitalTestValue));
    }

    public void testStringValuePacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setStringValue(stringTestValue, 0)
                .setVirtualPin()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(0, points.size());
    }

    public void testMismatchValuePinPacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setFloatValue(floatTestValue, 0)
                .setAnalogPin()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(0, points.size());
    }

    public void testMissingValuePacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogPin()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(0, points.size());
    }

    public void testMissingPinPacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogValue(smallTestValue, 0)
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(0, points.size());
    }

    public void testEmptyPacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        byte[] value = new GoosciSensorBuilder()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(0, points.size());
    }

    public void testPacketStream() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        for (int dataValue : packetStream) {
            byte[] value = new GoosciSensorBuilder()
                    .setAnalogPin()
                    .setAnalogValue(dataValue, dataValue)
                    .toByteArray();

            int chunksize = value.length - 1;

            fakeFramedSensorData(pa, value, chunksize, 2);
        }

        int timeSkew = 42 - packetStream[0];

        List<Point> points = tsc.getPoints();
        assertEquals(packetStream.length, points.size());

        for (int ii = 0; ii < packetStream.length; ii++) {
            assertEquals(points.get(ii).x, packetStream[ii] + timeSkew);
            assertEquals(points.get(ii).y, (double) packetStream[ii]);
        }
    }

    public void testMultiPacketStream() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final PacketAssembler pa = createPacketAssembler(tsc);

        for (int dataValue : packetStream) {
            byte[] value = new GoosciSensorBuilder()
                    .setAnalogPin()
                    .setAnalogValue(dataValue, dataValue)
                    .toByteArray();

            int chunksize = value.length - 1;

            fakeFramedSensorData(pa, value, chunksize, 2);
        }

        int timeSkew = 42 - packetStream[0];

        List<Point> points = tsc.getPoints();
        assertEquals(packetStream.length, points.size());

        for (int ii = 0; ii < packetStream.length; ii++) {
            assertEquals(points.get(ii).x, packetStream[ii] + timeSkew);
            assertEquals(points.get(ii).y, (double) packetStream[ii]);
        }
    }
}
