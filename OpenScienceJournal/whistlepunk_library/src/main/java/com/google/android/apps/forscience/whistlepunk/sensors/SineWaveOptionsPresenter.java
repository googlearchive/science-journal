package com.google.android.apps.forscience.whistlepunk.sensors;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ActiveBundle;
import com.google.android.apps.forscience.whistlepunk.sensorapi.LongUpdatingWatcher;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;

/**
 * OptionsPresenter for SineWave pseudo-sensor
 */
class SineWaveOptionsPresenter implements SensorPresenter.OptionsPresenter {
    @Override
    public View buildOptionsView(ActiveBundle activeBundle, Context context) {
        @SuppressLint("InflateParams") final View inflated =
                LayoutInflater.from(context).inflate(R.layout.sine_wave_options, null);
        final EditText frequencyEdit = getFrequencyEditText(inflated);
        frequencyEdit.setText(String.valueOf(getFrequency(activeBundle)));
        frequencyEdit.addTextChangedListener(new LongUpdatingWatcher(activeBundle,
                SineWavePseudoSensor.PREFS_KEY_FREQUENCY_MILLIS, frequencyEdit));

        return inflated;
    }

    private EditText getFrequencyEditText(View inflated) {
        return (EditText) inflated.findViewById(R.id.sine_wave_frequency_edit);
    }

    private long getFrequency(ActiveBundle activeBundle) {
        return activeBundle.getReadOnly().getLong(SineWavePseudoSensor.PREFS_KEY_FREQUENCY_MILLIS,
                SineWavePseudoSensor.DEFAULT_FREQENCY_MILLIS);
    }

    @Override
    public void applyOptions(ReadableSensorOptions bundle) {
        // Nothing to preview.
    }
}
