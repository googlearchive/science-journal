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

import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.ByteArrayOutputStream;

public class PacketAssembler {
  private static final String TAG = "PacketAssembler";

  private final StreamConsumer mC;
  private final Clock mDefaultClock;
  private BluetoothSensor mBluetoothSensor;

  private static final boolean DEBUG = false;
  private final ByteArrayOutputStream mOutputStream = new ByteArrayOutputStream();


  public PacketAssembler(final StreamConsumer c, final Clock defaultClock, final BluetoothSensor bs) {
    mC = c;
    mDefaultClock = defaultClock;
    mBluetoothSensor = bs;
  }


  private void parse() {
    GoosciSensor.SensorData sensorData = null;
    byte[] bs = mOutputStream.toByteArray();
    mOutputStream.reset();
    try {
      sensorData = GoosciSensor.SensorData.parseFrom(bs);
    } catch (InvalidProtocolBufferNanoException e) {
      Log.e(TAG, "Failed to parse sensor value because " + e.getMessage());
      return;
    }

    int counter = 0;
    long relativeTime;
    double data;

    if (sensorData.getData() != null && sensorData.getData().getAnalogValue() != null) {

      relativeTime = sensorData.timestampKey;
      data = (double) sensorData.getData().getAnalogValue().value;
      long timeSkew = mBluetoothSensor.getTimeSkew();
      if (timeSkew == -1) {
        if (DEBUG) Log.e(TAG, "Reset timeskew");
        // Haven't seen a value yet. Let's calculate the time skew assuming no
        // delay.
        timeSkew = mDefaultClock.getNow() - relativeTime;
        mBluetoothSensor.setTimeSkew(timeSkew);
      }

      if (DEBUG) {
        Log.e(TAG, "timestampKey: " + sensorData.timestampKey);
        Log.e(TAG, "relative time: " + relativeTime);
        Log.e(TAG, "timeSkew: " + timeSkew);
        Log.e(TAG, "add data time: " + (relativeTime + timeSkew));

      }
      mC.addData(relativeTime + timeSkew, data);
    } else {
      // TODO: Send an error to the listener here if possible, so the user knows
      // why data isn't showing up.
      Log.e(TAG, "Sensor data missing data or analog value");
    }
  }

  public void append(byte[] value) {
    int len = (int) value[0];

    boolean last = value[1] == 1;

    for (int i = 0; i < len; ++i) {
      mOutputStream.write(value[2 + i]);
    }

    if (last) {
      parse();
    }
  }
}
