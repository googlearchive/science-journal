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
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.ByteArrayOutputStream;

public class PacketAssembler {
    private static final String TAG = "PacketAssembler";

    private final StreamConsumer mC;
    private final Clock mDefaultClock;
    private final BluetoothSensor mBluetoothSensor;
    private final SensorStatusListener mListener;

    private static final boolean DEBUG = false;
    private final ByteArrayOutputStream mOutputStream = new ByteArrayOutputStream();

    private static float DIGITAL_HIGH = 1023f;
    private static float DIGITAL_LOW = 0f;

    public PacketAssembler(final StreamConsumer c, final Clock defaultClock,
                           final BluetoothSensor bs, final SensorStatusListener listener) {
        mC = c;
        mDefaultClock = defaultClock;
        mBluetoothSensor = bs;
        mListener = listener;
    }

    @VisibleForTesting
    public double booleanToDigital(boolean digitalValue) {
        return (double) (digitalValue ? DIGITAL_HIGH : DIGITAL_LOW);
    }

    private void emitProtoError(String errorMessage) {
        mListener.onSourceError(mBluetoothSensor.getId(), SensorStatusListener.ERROR_INVALID_PROTO,
                errorMessage);
    }

    private void parse() {
        GoosciSensor.SensorData sensorData = null;
        byte[] bs = mOutputStream.toByteArray();
        mOutputStream.reset();
        try {
            sensorData = GoosciSensor.SensorData.parseFrom(bs);
        } catch (InvalidProtocolBufferNanoException e) {
            mListener.onSourceError(mBluetoothSensor.getId(),
                    SensorStatusListener.ERROR_INVALID_PROTO, e.getLocalizedMessage());
            Log.d(TAG, "Failed to parse sensor value because " + e.getMessage());
            return;
        }

        long relativeTime;
        double data;

        if (sensorData.hasData()) {
            GoosciSensor.Data sensorValue = sensorData.getData();
            GoosciSensor.Pin sensorPin = sensorValue.pin;
            if (sensorPin.hasAnalogPin() && sensorValue.hasAnalogValue()) {
                data = (double) sensorValue.getAnalogValue().value;
            } else if (sensorPin.hasDigitalPin() && sensorValue.hasDigitalValue()) {
                // TODO: Better support boolean values
                data = booleanToDigital(sensorValue.getDigitalValue().value);
            } else if (sensorPin.hasVirtualPin()) {
                if (sensorValue.hasFloatValue()) {
                    data = (double) sensorValue.getFloatValue().value;
                } else if (sensorValue.hasIntValue()) {
                    data = (double) sensorValue.getIntValue().value;
                } else {
                    // TODO: We support string messages in the proto but there is no good way to
                    // convert to any value.
                    emitProtoError("Unable to read data from external sensor");
                    Log.d(TAG, "Sensor data has unsupported virtual pin type");
                    return;
                }
            } else {
                emitProtoError("Unable to read data from external sensor");
                Log.d(TAG, "Sensor data has unknown pin or is missing sensor values");
                return;
            }

            relativeTime = sensorData.timestampKey;
            long timeSkew = mBluetoothSensor.getTimeSkew();
            if (timeSkew == -1) {
                if (DEBUG) Log.d(TAG, "Reset timeskew");
                // Haven't seen a value yet. Let's calculate the time skew assuming no
                // delay.
                timeSkew = mDefaultClock.getNow() - relativeTime;
                mBluetoothSensor.setTimeSkew(timeSkew);
            }

            if (DEBUG) {
                Log.d(TAG, "timestampKey: " + sensorData.timestampKey);
                Log.d(TAG, "relative time: " + relativeTime);
                Log.d(TAG, "timeSkew: " + timeSkew);
                Log.d(TAG, "add data time: " + (relativeTime + timeSkew));
            }
            mC.addData(relativeTime + timeSkew, data);
        } else {
            emitProtoError("Unable to read data from external sensor");
            Log.d(TAG, "Sensor data missing data");
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
