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

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.AnalogPin;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.AnalogValue;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.DigitalPin;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.DigitalValue;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.FloatValue;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.IntValue;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.Pin;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.StringValue;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.VirtualPin;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PacketAssemblerTest {
  private final int testValue = 314159;
  private final int smallTestValue = 1;
  private final int testTime = 42;

  private final float floatTestValue = 10f;
  private final int intTestValue = 20;
  private final boolean digitalTestValue = false;
  private final int analogTestValue = 30;
  private final String stringTestValue = "Test";

  private final int[] packetStream = {5, 10, 15, 20, 25, 30};

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
    private List<Point> points = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    @Override
    public void onError(@SensorStatusListener.Error int error, String errorMessage) {
      errors.add(errorMessage);
    }

    @Override
    public void onDataParsed(long timeStampMs, double data) {
      points.add(new Point(timeStampMs, data));
    }

    public List<String> getErrors() {
      return errors;
    }

    public List<Point> getData() {
      return points;
    }
  }

  private class GoosciSensorBuilder {
    private GoosciSensor.SensorData.Builder sensorData;

    private com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.Data.Builder data;

    GoosciSensorBuilder() {
      sensorData = GoosciSensor.SensorData.newBuilder().setTimestampKey(0);
      data =
          com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.Data.newBuilder()
              .setPin(Pin.getDefaultInstance());
      data.setPin(Pin.getDefaultInstance());
    }

    GoosciSensorBuilder setAnalogPin() {
      AnalogPin analogPin = AnalogPin.newBuilder().setPin(0).build();
      Pin newPin = data.getPin().toBuilder().setAnalogPin(analogPin).build();
      data.setPin(newPin);
      return this;
    }

    GoosciSensorBuilder setDigitalPin() {
      DigitalPin digitalPin = DigitalPin.newBuilder().setPin(0).build();
      Pin newPin = data.getPin().toBuilder().setDigitalPin(digitalPin).build();
      data.setPin(newPin);
      return this;
    }

    GoosciSensorBuilder setVirtualPin() {
      VirtualPin virtualPin = VirtualPin.newBuilder().setPin(0).build();
      Pin newPin = data.getPin().toBuilder().setVirtualPin(virtualPin).build();
      data.setPin(newPin);
      return this;
    }

    GoosciSensorBuilder setAnalogValue(int value, int timestampMs) {
      sensorData.setTimestampKey(timestampMs);
      AnalogValue av = AnalogValue.newBuilder().setValue(value).build();
      data.setAnalogValue(av);
      return this;
    }

    GoosciSensorBuilder setDigitalValue(boolean value, int timestampMs) {
      sensorData.setTimestampKey(timestampMs);
      DigitalValue dv = DigitalValue.newBuilder().setValue(value).build();
      data.setDigitalValue(dv);
      return this;
    }

    GoosciSensorBuilder setIntValue(int value, int timestampMs) {
      sensorData.setTimestampKey(timestampMs);
      IntValue iv = IntValue.newBuilder().setValue(value).build();
      data.setIntValue(iv);
      return this;
    }

    GoosciSensorBuilder setFloatValue(float value, int timestampMs) {
      sensorData.setTimestampKey(timestampMs);
      FloatValue fv = FloatValue.newBuilder().setValue(value).build();
      data.setFloatValue(fv);
      return this;
    }

    GoosciSensorBuilder setStringValue(String value, int timestampMs) {
      sensorData.setTimestampKey(timestampMs);
      StringValue sv = StringValue.newBuilder().setValue(value).build();
      data.setStringValue(sv);
      return this;
    }

    GoosciSensorBuilder commit() {
      sensorData.setData(data);
      return this;
    }

    byte[] toByteArray() {
      return sensorData.build().toByteArray();
    }
  }

  private static void fakeFramedSensorData(
      PacketAssembler pa, byte[] value, int chunksize, int expectedNumPackets) {
    int length = (int) Math.ceil(value.length / (double) chunksize);
    assertEquals(expectedNumPackets, length);

    int start = 0;
    for (int i = 0; i < length; ++i) {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      int l = chunksize;
      boolean last = i == length - 1;
      if (last) l = value.length % chunksize;
      outputStream.write((byte) l);
      outputStream.write((byte) (last ? 1 : 0));
      for (int j = 0; j < l; ++j) outputStream.write(value[start++]);

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

    byte[] value =
        new GoosciSensorBuilder()
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
    assertEquals(points.get(0).y, (double) smallTestValue, Double.MIN_VALUE);
  }

  @Test
  public void testMultiPacket() {
    final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
    final PacketAssembler pa = createPacketAssembler(tpal);

    byte[] value =
        new GoosciSensorBuilder()
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

    byte[] value =
        new GoosciSensorBuilder()
            .setFloatValue(floatTestValue, 0)
            .setVirtualPin()
            .commit()
            .toByteArray();

    int chunksize = value.length + 1;

    fakeFramedSensorData(pa, value, chunksize, 1);

    List<Point> points = tpal.getData();
    assertEquals(1, points.size());
    assertEquals(points.get(0).x, testTime);
    assertEquals(points.get(0).y, (double) floatTestValue, Double.MIN_VALUE);
  }

  @Test
  public void testIntValuePacket() {
    final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
    final PacketAssembler pa = createPacketAssembler(tpal);

    byte[] value =
        new GoosciSensorBuilder()
            .setIntValue(intTestValue, 0)
            .setVirtualPin()
            .commit()
            .toByteArray();

    int chunksize = value.length + 1;

    fakeFramedSensorData(pa, value, chunksize, 1);

    List<Point> points = tpal.getData();
    assertEquals(1, points.size());
    assertEquals(points.get(0).x, testTime);
    assertEquals(points.get(0).y, (double) intTestValue, Double.MIN_VALUE);
  }

  @Test
  public void testAnalogValuePacket() {
    final TestPacketAssemblerListener tpal = new TestPacketAssemblerListener();
    final PacketAssembler pa = createPacketAssembler(tpal);

    byte[] value =
        new GoosciSensorBuilder()
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

    byte[] value =
        new GoosciSensorBuilder()
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

    byte[] value =
        new GoosciSensorBuilder()
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

    byte[] value =
        new GoosciSensorBuilder()
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

    byte[] value = new GoosciSensorBuilder().setAnalogPin().commit().toByteArray();

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

    byte[] value =
        new GoosciSensorBuilder().setAnalogValue(smallTestValue, 0).commit().toByteArray();

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

    byte[] value = new GoosciSensorBuilder().toByteArray();

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
      byte[] value =
          new GoosciSensorBuilder()
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
      byte[] value =
          new GoosciSensorBuilder()
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
