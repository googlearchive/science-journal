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
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.ByteArrayOutputStream;

public class PacketAssembler {
    private static final String TAG = "PacketAssembler";

    private final Clock mDefaultClock;
    private final Listener mListener;

    private final ByteArrayOutputStream mPacketStream = new ByteArrayOutputStream();

    private long mTimeSkew = -1;

    private static float DIGITAL_HIGH = 1023f;
    private static float DIGITAL_LOW = 0f;

    public interface Listener {

        void onError(@SensorStatusListener.Error int error, String errorMessage);

        void onDataParsed(long timeStampMs, double data);
    }

    public PacketAssembler(final Clock defaultClock, final Listener listener) {
        mDefaultClock = defaultClock;
        mListener = listener;
    }

    @VisibleForTesting
    public double booleanToDigital(boolean digitalValue) {
        return (double) (digitalValue ? DIGITAL_HIGH : DIGITAL_LOW);
    }

    private void raiseError(String message) {
        mListener.onError(SensorStatusListener.ERROR_INVALID_PROTO, message);
    }

    private void parse() {
        byte[] bs = mPacketStream.toByteArray();
        mPacketStream.reset();

        GoosciSensor.SensorData sensorData;

        try {
            sensorData = GoosciSensor.SensorData.parseFrom(bs);
        } catch (InvalidProtocolBufferNanoException e) {
            raiseError(e.getLocalizedMessage());
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Failed to parse sensor value because " + e.getMessage());
            }
            return;
        }

        if (!sensorData.hasData()) {
            raiseError("Unable to read data from external sensor");
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sensor data missing");
            }
            return;
        }

        GoosciSensor.Data sensorValue = sensorData.getData();
        GoosciSensor.Pin sensorPin = sensorValue.pin;

        double data;

        if (sensorPin.hasAnalogPin() && sensorValue.hasAnalogValue()) {
            data = (double) sensorValue.getAnalogValue().value;
        } else if (sensorPin.hasDigitalPin() && sensorValue.hasDigitalValue()) {
            // TODO: Better support boolean values
            data = booleanToDigital(sensorValue.getDigitalValue().value);
        } else if (sensorPin.hasVirtualPin() && sensorValue.hasFloatValue()) {
            data = (double) sensorValue.getFloatValue().value;
        } else if (sensorPin.hasVirtualPin() && sensorValue.hasIntValue()) {
            data = (double) sensorValue.getIntValue().value;
        } else if (sensorPin.hasVirtualPin()) {
            // TODO: We support string messages in the proto but
            // there is no good way to convert to any value.
            raiseError("Unable to read data from external sensor");
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sensor data has unsupported virtual pin type");
            }
            return;
        } else {
            raiseError("Unable to read data from external sensor");
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sensor data has unknown pin or is missing sensor values");
            }
            return;
        }

        long relativeTime = sensorData.timestampKey;

        if (mTimeSkew == -1) {
            // Haven't seen a value yet. Let's calculate the time skew assuming no
            // delay.
            mTimeSkew = mDefaultClock.getNow() - relativeTime;
        }

        mListener.onDataParsed(relativeTime + mTimeSkew, data);

    }

    public void append(byte[] packet) {
        int length = (int) packet[0];
        boolean isLast = packet[1] == 1;

        mPacketStream.write(packet, 2, length);

        if (isLast) {
            parse();
        }
    }
}
