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

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import java.util.ArrayList;

/**
 * Activity to display a list of SensorTriggers, both inactive and active, for a particular card and
 * sensor.
 */
public class TriggerListActivity extends AppCompatActivity {
  public static final String EXTRA_ACCOUNT_KEY = "account_key";
  public static final String EXTRA_SENSOR_ID = "sensor_id";
  public static final String EXTRA_EXPERIMENT_ID = "experiment_id";
  public static final String EXTRA_LAYOUT_POSITION = "sensor_layout_position";
  public static final String EXTRA_TRIGGER_ORDER = "trigger_order";
  private static final String FRAGMENT_TAG = "fragment";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_trigger_list);
    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    Bundle extras = getIntent().getExtras();

    if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null && extras != null) {
      // TODO(lizlooney): figure out if extras is ever null or if EXTRA_ACCOUNT_KEY is ever not set?
      AppAccount appAccount = WhistlePunkApplication.getAccount(this, extras, EXTRA_ACCOUNT_KEY);
      String sensorId = extras.getString(EXTRA_SENSOR_ID, "");
      String experimentId = extras.getString(EXTRA_EXPERIMENT_ID, "");
      int position = extras.getInt(EXTRA_LAYOUT_POSITION);
      ArrayList<String> triggerOrder = extras.getStringArrayList(EXTRA_TRIGGER_ORDER);
      TriggerListFragment fragment =
          TriggerListFragment.newInstance(
              appAccount, sensorId, experimentId, position, triggerOrder);
      fragment.setRetainInstance(true);
      getSupportFragmentManager()
          .beginTransaction()
          .add(R.id.container, fragment, FRAGMENT_TAG)
          .commit();
    }
  }
}
