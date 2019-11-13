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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig;

/** Presents options for calculating and presenting frequency on the Android device. */
public class FrequencyOptionsPresenter implements SensorPresenter.OptionsPresenter {
  public static interface FilterChangeListener {
    /** Will be called every time one of the filter options is updated. */
    void setScalarFilter(ValueFilter filter);
  }

  private static final String PREFS_KEY_FREQUENCY_ENABLED = "frequency_enabled";
  private static final String PREFS_KEY_FREQUENCY_WINDOW = "frequency_window";
  private static final String PREFS_KEY_FREQUENCY_FILTER = "frequency_filter";
  private static final long DEFAULT_FREQUENCY_WINDOW = 2000;
  private static final float DEFAULT_FREQUENCY_FILTER = 10;

  private final SensorPresenter.OptionsPresenter additionalPresenter;
  private FilterChangeListener filterChangeListener;

  public FrequencyOptionsPresenter(
      FilterChangeListener filterChangeListener,
      SensorPresenter.OptionsPresenter additionalPresenter) {
    this.additionalPresenter = additionalPresenter;
    this.filterChangeListener = filterChangeListener;
  }

  @Override
  public View buildOptionsView(final ActiveBundle activeBundle, Context context) {
    @SuppressLint("InflateParams")
    final View inflated = LayoutInflater.from(context).inflate(R.layout.frequency_options, null);

    if (additionalPresenter != null) {
      final ViewGroup additionalView = (ViewGroup) inflated.findViewById(R.id.additional_options);
      additionalView.setVisibility(View.VISIBLE);
      additionalView.addView(additionalPresenter.buildOptionsView(activeBundle, context));
    }

    final EditText windowEditText = getWindowEditText(inflated);
    final ReadableSensorOptions roBundle = activeBundle.getReadOnly();
    windowEditText.setText(String.valueOf(getFrequencyWindow(roBundle)));
    windowEditText.addTextChangedListener(
        new LongUpdatingWatcher(activeBundle, PREFS_KEY_FREQUENCY_WINDOW, windowEditText));

    final EditText filterEditText = getFilterEditText(inflated);
    filterEditText.setText(String.valueOf(getFrequencyFilter(roBundle)));
    filterEditText.addTextChangedListener(
        new FloatUpdatingWatcher(activeBundle, PREFS_KEY_FREQUENCY_FILTER, filterEditText));

    CheckBox enableFrequencyBox = getFrequencyCheckbox(inflated);
    enableFrequencyBox.setChecked(getFrequencyChecked(roBundle));
    enableFrequencyBox.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            activeBundle.changeBoolean(PREFS_KEY_FREQUENCY_ENABLED, isChecked);
          }
        });

    return inflated;
  }

  private EditText getWindowEditText(View inflated) {
    return (EditText) inflated.findViewById(R.id.frequency_window_edit);
  }

  private EditText getFilterEditText(View inflated) {
    return (EditText) inflated.findViewById(R.id.frequency_filter_edit);
  }

  private CheckBox getFrequencyCheckbox(View inflated) {
    return (CheckBox) inflated.findViewById(R.id.frequency_enable_checkbox);
  }

  public long getFrequencyWindow(ReadableSensorOptions prefs) {
    return prefs.getLong(PREFS_KEY_FREQUENCY_WINDOW, DEFAULT_FREQUENCY_WINDOW);
  }

  public double getFrequencyFilter(ReadableSensorOptions prefs) {
    return prefs.getFloat(PREFS_KEY_FREQUENCY_FILTER, DEFAULT_FREQUENCY_FILTER);
  }

  private boolean getFrequencyChecked(ReadableSensorOptions prefs) {
    return prefs.getBoolean(PREFS_KEY_FREQUENCY_ENABLED, getDefaultFrequencyChecked());
  }

  protected boolean getDefaultFrequencyChecked() {
    return false;
  }

  protected GoosciSensorConfig.BleSensorConfig.ScaleTransform getDefaultScaleTransform() {
    return null;
  }

  @Override
  public void applyOptions(ReadableSensorOptions prefs) {
    final long newWindow = getFrequencyWindow(prefs);
    final double newFilter = getFrequencyFilter(prefs);
    final boolean newEnabled = getFrequencyChecked(prefs);
    // TODO: enable UI for setting scale transforms?
    filterChangeListener.setScalarFilter(
        ScalarSensor.computeValueFilter(
            newWindow, newFilter, newEnabled, getDefaultScaleTransform()));
    if (additionalPresenter != null) {
      additionalPresenter.applyOptions(prefs);
    }
  }
}
