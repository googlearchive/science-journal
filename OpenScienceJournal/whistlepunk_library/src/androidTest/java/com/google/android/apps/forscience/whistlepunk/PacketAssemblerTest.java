package com.google.android.apps.forscience.whistlepunk;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.ble.BleClientImpl;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;


public class PacketAssemblerTest extends AndroidTestCase {
    final private int testValue = 314159;
    final private int smallTestValue = 1;
    final private int testTime = 42;

    public class TestSystemClock implements Clock {
        @Override
        public long getNow() {
            return testTime;
        }
    };

    public class TestSensorEnvironment implements SensorEnvironment {
        private final BleClientImpl mBleClient = new BleClientImpl(getContext());

        private final Clock mDefaultClock = new TestSystemClock();
        @Override
        public RecordingDataController getDataController() {
            return null;
        }

        @Override
        public Clock getDefaultClock() {
            return mDefaultClock;
        }

        @Override
        public SensorHistoryStorage getSensorHistoryStorage() {
            return null;
        }

        @Override
        public BleClient getBleClient() {
            return mBleClient;
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

    private static void FakeFramedSensorData(PacketAssembler pa, byte[] value, int chunksize, int expectedNumPackets) {
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

    public void testSinglePacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final SensorEnvironment ev = new TestSensorEnvironment();
        final BleSensorSpec bs = new BleSensorSpec("F7:83:CE:FE:56:C2", "name");
        final BluetoothSensor s = new BluetoothSensor("sensorId", bs,
                BluetoothSensor.ANNING_SERVICE_SPEC);
        final Clock cl = new TestSystemClock();
        final PacketAssembler pa = new PacketAssembler(tsc, cl, s);

        GoosciSensor.SensorData sd = new GoosciSensor.SensorData();
        sd.timestampKey = 0;
        GoosciSensor.Data d = new GoosciSensor.Data();
        GoosciSensor.AnalogValue av = new GoosciSensor.AnalogValue();
        av.value = smallTestValue;
        d.setAnalogValue(av);
        sd.setData(d);
        byte[] value = GoosciSensor.SensorData.toByteArray(sd);
        // Test should work for any value above 0; but we'll use the size that BLE enforces.
        int chunksize = value.length + 1;

        FakeFramedSensorData(pa, value, chunksize, 1);

        List<Point> points = tsc.getPoints();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double)smallTestValue);
    }

    public void testMultiPacket() {
        final TestStreamConsumer tsc = new TestStreamConsumer();
        final SensorEnvironment ev = new TestSensorEnvironment();
        final BleSensorSpec bs = new BleSensorSpec("F7:83:CE:FE:56:C2", "name");
        final BluetoothSensor s = new BluetoothSensor("sensorId", bs,
                BluetoothSensor.ANNING_SERVICE_SPEC);
        final Clock cl = new TestSystemClock();
        final PacketAssembler pa = new PacketAssembler(tsc, cl, s);

        GoosciSensor.SensorData sd = new GoosciSensor.SensorData();
        sd.timestampKey = 0;
        GoosciSensor.Data d = new GoosciSensor.Data();
        GoosciSensor.AnalogValue av = new GoosciSensor.AnalogValue();
        av.value = testValue;
        d.setAnalogValue(av);

        d.pin = new GoosciSensor.Pin();
        GoosciSensor.AnalogPin pin = new GoosciSensor.AnalogPin();
        d.pin.setAnalogPin(pin);


        sd.setData(d);
        byte[] value = GoosciSensor.SensorData.toByteArray(sd);
        // Test should work for any value above 0; but we'll use the size that BLE enforces.
        int chunksize = value.length - 1;

        FakeFramedSensorData(pa, value, chunksize, 2);

        List<Point> points = tsc.getPoints();
        assertEquals(1, points.size());
        assertEquals(points.get(0).x, testTime);
        assertEquals(points.get(0).y, (double) testValue);
    }
}
