package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;

import com.google.android.apps.forscience.javalib.DataRefresher;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

public class SineWavePseudoSensor extends ScalarSensor {
    public static final String ID = "SINE_WAVE_X";
    public static final long DEFAULT_FREQENCY_MILLIS = 5000;
    public static final String PREFS_KEY_FREQUENCY_MILLIS = "prefs_frequency";
    private DataRefresher mDataRefresher;

    public SineWavePseudoSensor() {
        // TODO(katie): Replace placeholder drawable with appropriate "unknown" sensor symbol.
        super(ID);
    }

    @Override
    protected SensorRecorder makeScalarControl(final StreamConsumer c,
            final SensorEnvironment environment, Context context, final SensorStatusListener listener) {
        return new AbstractSensorRecorder() {
            private long mFrequencyMillis = DEFAULT_FREQENCY_MILLIS;

            @Override
            public void startObserving() {
                mDataRefresher =
                        new DataRefresher(new SystemScheduler(), environment.getDefaultClock()) {
                    @Override
                    public double getValue(long now) {
                        return computeValue(now);
                    }
                };
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
                mDataRefresher.setStreamConsumer(c);
                mDataRefresher.startStreaming();
            }

            private double computeValue(long now) {
                final double value = Math.sin(Math.PI * 2 * now / mFrequencyMillis);
                return value;
            }

            @Override
            public void stopObserving() {
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
                if (mDataRefresher != null) {
                    mDataRefresher.stopStreaming();
                    mDataRefresher = null;
                }
            }

            @Override
            public void applyOptions(ReadableSensorOptions settings) {
                mFrequencyMillis =
                        settings.getLong(PREFS_KEY_FREQUENCY_MILLIS, DEFAULT_FREQENCY_MILLIS);
            }
        };
    }

    @Override
    protected SensorPresenter.OptionsPresenter createAdditionalScalarOptionsPresenter() {
        return new SineWaveOptionsPresenter();
    }

}
