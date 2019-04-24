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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import java.util.Arrays;

public class DeviceOptionsViewController {
  private static String TAG = "DeviceOptionsViewC";
  private static final String KEY_TYPE_STATE = "type_state";
  private static final String KEY_PIN_STATE = "pin_state";
  private static final String KEY_FREQ_STATE = "freq_state";

  private View view;
  private DataController dataController;
  private final String experimentId;
  private SensorTypeProvider sensorTypeProvider;
  private PinTypeProvider pinTypeProvider;
  private String sensorId;
  private ExternalSensorSpec sensor;
  private Spinner sensorTypeSpinner;
  private Spinner customPinSpinner;
  private CheckBox frequencyCheckbox;

  public DeviceOptionsViewController(
      Context context, DataController dataController, String experimentId) {
    this.dataController = dataController;
    this.experimentId = experimentId;
    this.view = LayoutInflater.from(context).inflate(R.layout.device_options_dialog, null);
    sensorTypeProvider = new SensorTypeProvider(context);
    final ArrayAdapter<SensorTypeProvider.SensorType> adapter =
        new ArrayAdapter<SensorTypeProvider.SensorType>(
            context, android.R.layout.simple_spinner_item, sensorTypeProvider.getSensors()) {
          @Override
          public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            Resources res = view.getContext().getResources();
            // The drawables are all white, so we need to color them black for this
            // dialog.
            Drawable drawable = res.getDrawable(getItem(position).getDrawableId()).mutate();
            drawable.setColorFilter(
                res.getColor(R.color.text_color_black), PorterDuff.Mode.MULTIPLY);
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null);
            view.setCompoundDrawablePadding(
                res.getDimensionPixelSize(R.dimen.device_type_drawable_padding));
            return view;
          }
        };
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sensorTypeSpinner = (Spinner) this.view.findViewById(R.id.sensor_type_spinner);
    sensorTypeSpinner.setAdapter(adapter);
    sensorTypeSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {

          public void onItemSelected(AdapterView<?> arg0, View view, int position, long id) {
            LinearLayout layout =
                (LinearLayout)
                    DeviceOptionsViewController.this.view.findViewById(R.id.spinner_custom_layout);
            if (adapter.getItem(position).isCustom()) {
              layout.setVisibility(LinearLayout.VISIBLE);
            } else {
              layout.setVisibility(LinearLayout.GONE);
            }
          }

          public void onNothingSelected(AdapterView<?> arg0) {}
        });

    pinTypeProvider = new PinTypeProvider();

    ArrayAdapter<PinTypeProvider.PinType> pinTypeAdapter =
        new ArrayAdapter<PinTypeProvider.PinType>(
            context, android.R.layout.simple_spinner_item, pinTypeProvider.getPins());
    pinTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    customPinSpinner = (Spinner) this.view.findViewById(R.id.spinner_custom_pin);
    customPinSpinner.setAdapter(pinTypeAdapter);

    frequencyCheckbox = (CheckBox) this.view.findViewById(R.id.spinner_custom_frequency_checkbox);
  }

  public void commit(final DeviceOptionsListener optionsListener) {
    final ExternalSensorSpec sensor = getOptions();

    maybeReplaceSensor(
        dataController,
        experimentId,
        sensorId,
        sensor,
        new LoggingConsumer<String>(TAG, "replacing sensor") {
          @Override
          public void success(String newSensorId) {
            optionsListener.onExperimentSensorReplaced(sensorId, newSensorId);
            sensorId = newSensorId;
          }
        });
  }

  /**
   * In experiment {@code experimentId}, replace the sensor with id {@code oldSensorId} with a new
   * sensor built from spec {@code newSensor}. If the replacement actually changes anything, {@code
   * onNewSensorId} is called with the id of the newly-built sensor.
   */
  public static void maybeReplaceSensor(
      final DataController dataController,
      final String experimentId,
      final String oldSensorId,
      ExternalSensorSpec newSensor,
      final MaybeConsumer<String> onNewSensorId) {
    dataController.addOrGetExternalSensor(
        newSensor,
        new LoggingConsumer<String>(TAG, "update external sensor") {
          @Override
          public void success(final String newSensorId) {
            if (!newSensorId.equals(oldSensorId)) {
              Consumer<Success> onSuccess =
                  new Consumer<Success>() {
                    @Override
                    public void take(Success success) {
                      onNewSensorId.success(newSensorId);
                    }
                  };
              dataController.replaceSensorInExperiment(
                  experimentId,
                  oldSensorId,
                  newSensorId,
                  MaybeConsumers.chainFailure(onNewSensorId, onSuccess));
            }
          }
        });
  }

  public void setSensor(String sensorId, ExternalSensorSpec sensor, Bundle savedInstanceState) {
    this.sensorId = sensorId;
    this.sensor = sensor;

    if (savedInstanceState != null) {
      sensorTypeSpinner.onRestoreInstanceState(savedInstanceState.getParcelable(KEY_TYPE_STATE));
      customPinSpinner.onRestoreInstanceState(savedInstanceState.getParcelable(KEY_PIN_STATE));
      frequencyCheckbox.onRestoreInstanceState(savedInstanceState.getParcelable(KEY_FREQ_STATE));
    } else if (BleSensorSpec.TYPE.equals(this.sensor.getType())) {
      // TODO: config for other sensor types?

      BleSensorSpec bleSensor = (BleSensorSpec) this.sensor;
      // Load from sensor if we don't have saved state.
      SensorTypeProvider.SensorType[] sensors = sensorTypeProvider.getSensors();
      @SensorTypeProvider.SensorKind int type = bleSensor.getSensorType();
      boolean found = false;
      for (int i = 0; i < sensors.length; i++) {
        if (sensors[i].getSensorKind() == type) {
          sensorTypeSpinner.setSelection(i);
          found = true;
          break;
        }
      }
      if (!found) {
        Log.e(TAG, "Expected to find sensor type " + type + " in " + Arrays.toString(sensors));
      }

      customPinSpinner.setSelection(findPinIndex(bleSensor.getCustomPin()));

      frequencyCheckbox.setChecked(bleSensor.getCustomFrequencyEnabled());
    }
  }

  private int findPinIndex(String customPin) {
    if (TextUtils.isEmpty(customPin)) {
      // Default to A0.
      // TODO: Better way to default without hard coding, although pins are not translatable.
      return 0;
    }
    PinTypeProvider.PinType[] pins = pinTypeProvider.getPins();
    for (int i = 0; i < pins.length; i++) {
      if (pins[i].toString().equals(customPin)) {
        return i;
      }
    }
    Log.e(TAG, "Expected to find pin " + customPin + " in " + Arrays.toString(pins));
    return 0;
  }

  public ExternalSensorSpec getOptions() {
    if (sensor == null) {
      return null;
    }

    SensorTypeProvider.SensorType item =
        (SensorTypeProvider.SensorType) sensorTypeSpinner.getSelectedItem();

    if (sensor.getType().equals(BleSensorSpec.TYPE)) {
      // TODO: what about other sensor types?

      BleSensorSpec bleSensor = (BleSensorSpec) sensor;
      bleSensor.setSensorType(item.getSensorKind());
      // TODO: test that these are always set correctly.
      PinTypeProvider.PinType pinItem =
          (PinTypeProvider.PinType) customPinSpinner.getSelectedItem();
      bleSensor.setCustomPin(pinItem.toString());
      bleSensor.setCustomFrequencyEnabled(frequencyCheckbox.isChecked());
    }
    return sensor;
  }

  public View getView() {
    return view;
  }

  public void onSaveInstanceState(Bundle outState) {
    outState.putParcelable(KEY_TYPE_STATE, sensorTypeSpinner.onSaveInstanceState());
    outState.putParcelable(KEY_PIN_STATE, customPinSpinner.onSaveInstanceState());
    outState.putParcelable(KEY_FREQ_STATE, frequencyCheckbox.onSaveInstanceState());
  }
}

