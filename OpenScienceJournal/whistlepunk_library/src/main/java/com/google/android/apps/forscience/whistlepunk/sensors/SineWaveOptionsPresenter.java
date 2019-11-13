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

/** OptionsPresenter for SineWave pseudo-sensor */
class SineWaveOptionsPresenter implements SensorPresenter.OptionsPresenter {
  @Override
  public View buildOptionsView(ActiveBundle activeBundle, Context context) {
    @SuppressLint("InflateParams")
    final View inflated = LayoutInflater.from(context).inflate(R.layout.sine_wave_options, null);
    final EditText frequencyEdit = getFrequencyEditText(inflated);
    frequencyEdit.setText(String.valueOf(getFrequency(activeBundle)));
    frequencyEdit.addTextChangedListener(
        new LongUpdatingWatcher(
            activeBundle, SineWavePseudoSensor.PREFS_KEY_FREQUENCY_MILLIS, frequencyEdit));

    return inflated;
  }

  private EditText getFrequencyEditText(View inflated) {
    return (EditText) inflated.findViewById(R.id.sine_wave_frequency_edit);
  }

  private long getFrequency(ActiveBundle activeBundle) {
    return activeBundle
        .getReadOnly()
        .getLong(
            SineWavePseudoSensor.PREFS_KEY_FREQUENCY_MILLIS,
            SineWavePseudoSensor.DEFAULT_FREQENCY_MILLIS);
  }

  @Override
  public void applyOptions(ReadableSensorOptions bundle) {
    // Nothing to preview.
  }
}
