package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

/**
 * Class to get sensor data from the Magnetic sensor.
 */
public class MagneticRotationSensor extends ScalarSensor {
    public static final String ID = "MagneticRotationSensor";
    private SensorEventListener mSensorEventListener;

    public MagneticRotationSensor() {
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
                Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                if (mSensorEventListener != null) {
                    getSensorManager(context).unregisterListener(mSensorEventListener);
                }
                final Clock clock = environment.getDefaultClock();
                mSensorEventListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        double x = event.values[0];
                        double y = event.values[1];
                        // Convert X and Y components to angle
                        // https://en.wikipedia.org/wiki/Atan2
                        double angle = Math.toDegrees(Math.atan2(y,x)) + 180.;
                        c.addData(clock.getNow(), angle);
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {

                    }
                };
                sensorManager.registerListener(mSensorEventListener, magnetometer,
                        SensorManager.SENSOR_DELAY_UI);
            }

            @Override
            public void stopObserving() {
                getSensorManager(context).unregisterListener(mSensorEventListener);
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
            }
        };
    }

    public static boolean isMagneticRotationSensorAvailable(Context applicationContext) {
        return isSensorAvailable(applicationContext, Sensor.TYPE_MAGNETIC_FIELD);
    }
}
