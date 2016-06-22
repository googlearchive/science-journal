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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * A generic class for a sensor object can provide a callback when the sensor values are updated.
 */
public abstract class SensorTool {
    private Context mContext;
    private SensorManager mSensorManager;
    private SensorEventListener mSensorEventListener;
    private int mSensorType;
    private Sensor mSensor;


    public SensorTool(Context context, int sensorType) {
        mContext = context.getApplicationContext();
        mSensorType = sensorType;
    }

    public void start() {
        mSensorManager = getSensorManager(mContext);
        mSensor = mSensorManager.getDefaultSensor(mSensorType);
        if (mSensorEventListener != null) {
            stop();
        }
        mSensorEventListener = createSensorEventListener();
        mSensorManager.registerListener(mSensorEventListener, mSensor,
                SensorManager.SENSOR_DELAY_UI);
    }

    public void stop() {
        getSensorManager(mContext).unregisterListener(mSensorEventListener);
    }

    public abstract void onSensorUpdate(float[] values);

    private SensorEventListener createSensorEventListener() {
        return new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                onSensorUpdate(sensorEvent.values);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };
    }

    private static SensorManager getSensorManager(Context context) {
        return (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public static boolean deviceHasSensor(Context context, int sensorType) {
        return getSensorManager(context).getDefaultSensor(sensorType) != null;
    }
}
