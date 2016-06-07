package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.apps.forscience.javalib.DataRefresher;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

/**
 * Class to get sensor data from the Pressure Sensor (barometer).
 */
public class BarometerSensor extends ScalarSensor {
    public static final String ID = "BarometerSensor";
    private SensorEventListener mSensorEventListener;

    public BarometerSensor() {
        super(ID);
    }

    @Override
    protected SensorRecorder makeScalarControl(final StreamConsumer c,
            final SensorEnvironment environment, final Context context,
            final SensorStatusListener listener) {
        return new AbstractSensorRecorder() {
            @Override
            public void startObserving() {
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
                SensorManager sensorManager = getSensorManager(context);
                Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (mSensorEventListener != null) {
                    getSensorManager(context).unregisterListener(mSensorEventListener);
                }
                final Clock clock = environment.getDefaultClock();
                mSensorEventListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        c.addData(clock.getNow(), event.values[0]);
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {

                    }
                };
                sensorManager.registerListener(mSensorEventListener, sensor,
                        SensorManager.SENSOR_DELAY_UI);
            }

            @Override
            public void stopObserving() {
                getSensorManager(context).unregisterListener(mSensorEventListener);
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
            }
        };
    }

    public static boolean isBarometerSensorAvailable(Context context) {
        return isSensorAvailable(context, Sensor.TYPE_PRESSURE);
    }
}
