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
package com.google.android.apps.forscience.scalarapisample;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class DeviceSettingsPopupActivity extends Activity {
  private static final String EXTRA_SENSOR_NAME = "extra_sensor_name";
  private static final String EXTRA_SENSOR_TYPE_INT = "extra_sensor_type_int";

  private static final String PREF_KEY_SENSOR_PREFIX = "axis_for_";

  private static final String SENSOR_PREF_NAME = "sensors";

  public static PendingIntent getPendingIntent(Context context, Sensor sensor) {
    int flags = 0;
    Intent intent = new Intent(context, DeviceSettingsPopupActivity.class);
    intent.putExtra(EXTRA_SENSOR_NAME, sensor.getName());
    intent.putExtra(EXTRA_SENSOR_TYPE_INT, sensor.getType());
    return PendingIntent.getActivity(context, sensor.getType(), intent, flags);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.accelerometer_settings);

    Bundle extras = getIntent().getExtras();
    String sensorName = extras.getString(EXTRA_SENSOR_NAME, "Unknown");
    final int sensorType = extras.getInt(EXTRA_SENSOR_TYPE_INT);
    TextView header = (TextView) findViewById(R.id.header);
    header.setText("Select axis for " + sensorName + ":");

    Spinner spinner = (Spinner) findViewById(R.id.axis_spinner);
    spinner.setAdapter(
        new ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, new String[] {"X", "Y", "Z"}));
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            SharedPreferences prefs = getSensorPreferences(DeviceSettingsPopupActivity.this);
            prefs.edit().putInt(getAxisPrefKey(sensorType), position).apply();
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
    spinner.setSelection(getIndexForSensorType(sensorType, this));
  }

  public static int getIndexForSensorType(int sensorType, Context context) {
    return getSensorPreferences(context).getInt(getAxisPrefKey(sensorType), 0);
  }

  private static String getAxisPrefKey(int sensorType) {
    return PREF_KEY_SENSOR_PREFIX + sensorType;
  }

  private static SharedPreferences getSensorPreferences(Context context) {
    return context.getSharedPreferences(SENSOR_PREF_NAME, Context.MODE_PRIVATE);
  }
}
