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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class PacketAssemblerTest {
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

    private static class TestPacketAssemblerListener implements PacketAssembler.Listener {
        private List<Point> mPoints = new ArrayList<>();
        private List<String> mErrors = new ArrayList<>();

        @Override
        public void onError(@SensorStatusListener.Error int error, String errorMessage) {
            mErrors.add(errorMessage);
        }

        @Override
        public void onDataParsed(long timeStampMs, double data) {
            mPoints.add(new Point(timeStampMs, data));
        }

        public List<String> getErrors() {
            return mErrors;
        }

        public List<Point> getData() {
            return mPoints;
        }
    }

    private class GoosciSensorBuilder {
        private GoosciSensor.SensorData mSensorData;
        private GoosciSensor.Data mData;

        GoosciSensorBuilder() {
            mSensorData = new GoosciSensor.SensorData();
            mData = new GoosciSensor.Data();
            mData.pin = new GoosciSensor.Pin();
        }

        GoosciSensorBuilder setAnalogPin() {
            GoosciSensor.AnalogPin pin = new GoosciSensor.AnalogPin();
            mData.pin.setAnalogPin(pin);
            return this;
        }

        GoosciSensorBuilder setDigitalPin() {
            GoosciSensor.DigitalPin pin = new GoosciSensor.DigitalPin();
            mData.pin.setDigitalPin(pin);
            return this;
        }

        GoosciSensorBuilder setVirtualPin() {
            GoosciSensor.VirtualPin pin = new GoosciSensor.VirtualPin();
            mData.pin.setVirtualPin(pin);
            return this;
        }

        GoosciSensorBuilder setAnalogValue(int value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.AnalogValue av = new GoosciSensor.AnalogValue();
            av.value = value;
            mData.setAnalogValue(av);
            return this;
        }

        GoosciSensorBuilder setDigitalValue(boolean value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.DigitalValue dv = new GoosciSensor.DigitalValue();
            dv.value = value;
            mData.setDigitalValue(dv);
            return this;
        }

        GoosciSensorBuilder setIntValue(int value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.IntValue iv = new GoosciSensor.IntValue();
            iv.value = value;
            mData.setIntValue(iv);
            return this;
        }

        GoosciSensorBuilder setFloatValue(float value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.FloatValue fv = new GoosciSensor.FloatValue();
            fv.value = value;
            mData.setFloatValue(fv);
            return this;
        }

        GoosciSensorBuilder setStringValue(String value, int timestampMs) {
            mSensorData.timestampKey = timestampMs;
            GoosciSensor.StringValue sv = new GoosciSensor.StringValue();
            sv.value = value;
            mData.setStringValue(sv);
            return this;
        }

        GoosciSensorBuilder commit() {
            mSensorData.setData(mData);
            return this;
        }

        byte[] toByteArray() {
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

    private PacketAssembler createPacketAssembler(TestPacketAssemblerListener tpal) {
        final Clock cl = new TestSystemClock();
        return new PacketAssembler(cl, tpal);
    }

    @Test
    public void testSinglePacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogValue(smallTestValue, 0)
                .setAnalogPin()
                .commit()
                .toByteArray();

        // Test should work for any value above 0; but we'll use the size that BLE enforces.
        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double)smallTestValue, Double.MIN_VALUE);
    }

    @Test
    public void testMultiPacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogValue(testValue, 0)
                .setAnalogPin()
                .commit()
                .toByteArray();

        // Test should work for any value above 0; but we'll use the size that BLE enforces.
        int chunksize = value.length - 1;

        fakeFramedSensorData(pa, value, chunksize, 2);

        List<Point> points = tpal.getData();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double) testValue, Double.MIN_VALUE);
    }

    @Test
    public void testFloatValuePacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setFloatValue(floatTestValue, 0)
                .setVirtualPin()
                .commit()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double)floatTestValue, Double.MIN_VALUE);
    }

    @Test
    public void testIntValuePacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setIntValue(intTestValue, 0)
                .setVirtualPin()
                .commit()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double)intTestValue, Double.MIN_VALUE);
    }

    @Test
    public void testAnalogValuePacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogValue(analogTestValue, 0)
                .setAnalogPin()
                .commit()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double) analogTestValue, Double.MIN_VALUE);
    }

    @Test
    public void testDigitalValuePacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setDigitalValue(digitalTestValue, 0)
                .setDigitalPin()
                .commit()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, pa.booleanToDigital(digitalTestValue), Double.MIN_VALUE);
    }

    @Test
    public void testStringValuePacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setStringValue(stringTestValue, 0)
                .setVirtualPin()
                .commit()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(0, points.size());
        List<String> errors = tpal.getErrors();
        assertEquals(1, errors.size());
    }

    @Test
    public void testMismatchValuePinPacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setFloatValue(floatTestValue, 0)
                .setAnalogPin()
                .commit()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(0, points.size());
        List<String> errors = tpal.getErrors();
        assertEquals(1, errors.size());
    }

    @Test
    public void testMissingValuePacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogPin()
                .commit()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(0, points.size());
        List<String> errors = tpal.getErrors();
        assertEquals(1, errors.size());
    }

    @Test
    public void testMissingPinPacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .setAnalogValue(smallTestValue, 0)
                .commit()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(0, points.size());
        List<String> errors = tpal.getErrors();
        assertEquals(1, errors.size());
    }

    @Test
    public void testEmptyPacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = new GoosciSensorBuilder()
                .toByteArray();

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(0, points.size());
        List<String> errors = tpal.getErrors();
        assertEquals(1, errors.size());
    }

    @Test
    public void testPacketStream() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        for (int dataValue : packetStream) {
            byte[] value = new GoosciSensorBuilder()
                    .setAnalogPin()
                    .setAnalogValue(dataValue, dataValue)
                    .commit()
                    .toByteArray();

            int chunksize = value.length - 1;

            fakeFramedSensorData(pa, value, chunksize, 2);
        }

        int timeSkew = 42 - packetStream[0];

        List<Point> points = tpal.getData();
        assertEquals(packetStream.length, points.size());

        for (int ii = 0; ii < packetStream.length; ii++) {
            assertEquals(points.get(ii).x, packetStream[ii] + timeSkew);
            assertEquals(points.get(ii).y, (double) packetStream[ii], Double.MIN_VALUE);
        }
    }

    @Test
    public void testMultiPacketStream() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        for (int dataValue : packetStream) {
            byte[] value = new GoosciSensorBuilder()
                    .setAnalogPin()
                    .setAnalogValue(dataValue, dataValue)
                    .commit()
                    .toByteArray();

            int chunksize = value.length - 1;

            fakeFramedSensorData(pa, value, chunksize, 2);
        }

        int timeSkew = 42 - packetStream[0];

        List<Point> points = tpal.getData();
        assertEquals(packetStream.length, points.size());

        for (int ii = 0; ii < packetStream.length; ii++) {
            assertEquals(points.get(ii).x, packetStream[ii] + timeSkew);
            assertEquals(points.get(ii).y, (double) packetStream[ii], Double.MIN_VALUE);
        }
    }

    @Test
    public void testInvalidPacket() {
        final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
        final PacketAssembler pa = createPacketAssembler(tpal);

        byte[] value = {0};

        int chunksize = value.length + 1;

        fakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tpal.getData();
        assertEquals(0, points.size());
        List<String> errors = tpal.getErrors();
        assertEquals(1, errors.size());
    }
}
