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
