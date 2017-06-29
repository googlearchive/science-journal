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

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.R;

import java.util.ArrayList;

/**
 * Activity for adding and editing triggers.
 */
public class EditTriggerActivity extends AppCompatActivity {
    public static final String EXTRA_SENSOR_ID = "sensor_id";
    public static final String EXTRA_EXPERIMENT_ID = "experiment_id";
    public static final String EXTRA_SENSOR_LAYOUT_BLOB = "sensor_layout_blob";
    public static final String EXTRA_TRIGGER_ID = "trigger_id";
    private static final String FRAGMENT_TAG = "fragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_trigger);

        Bundle extras = getIntent().getExtras();

        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null && extras != null) {
            String sensorId = extras.getString(EXTRA_SENSOR_ID, "");
            String experimentId = extras.getString(EXTRA_EXPERIMENT_ID, "");
            String triggerId = extras.getString(EXTRA_TRIGGER_ID, "");
            byte[] sensorLayoutBlob = extras.getByteArray(EXTRA_SENSOR_LAYOUT_BLOB);
            int position = extras.getInt(TriggerListActivity.EXTRA_LAYOUT_POSITION);
            ArrayList<String> triggerOrder = extras.getStringArrayList(
                    TriggerListActivity.EXTRA_TRIGGER_ORDER);
            EditTriggerFragment fragment = EditTriggerFragment.newInstance(sensorId, experimentId,
                    triggerId, sensorLayoutBlob, position, triggerOrder);
            getSupportFragmentManager().beginTransaction().add(R.id.container, fragment,
                    FRAGMENT_TAG).commit();
        }
    }
}
