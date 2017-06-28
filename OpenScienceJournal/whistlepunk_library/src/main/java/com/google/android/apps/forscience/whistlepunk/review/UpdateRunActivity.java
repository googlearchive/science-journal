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

package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.R;

public class UpdateRunActivity extends AppCompatActivity {

    private static final String FRAGMENT_TAG = "fragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_run);

        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
            String runId = getIntent().getExtras().getString(
                    UpdateRunFragment.ARG_RUN_ID);
            String experimentId = getIntent().getExtras().getString(UpdateRunFragment.ARG_EXP_ID);
            UpdateRunFragment fragment = UpdateRunFragment.newInstance(runId, experimentId);
            fragment.setRetainInstance(true);

            getSupportFragmentManager().beginTransaction().add(R.id.container, fragment,
                    FRAGMENT_TAG).commit();
        }
    }

    public static void launch(Context context, String runId, String experimentId) {
        final Intent intent = new Intent(context, UpdateRunActivity.class);
        intent.putExtra(UpdateRunFragment.ARG_RUN_ID, runId);
        intent.putExtra(UpdateRunFragment.ARG_EXP_ID, experimentId);
        context.startActivity(intent);
    }
}
