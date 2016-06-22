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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.R;

public class UpdateExperimentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_experiment);

        if (savedInstanceState == null) {
            String experimentId = getIntent().getExtras().getString(
                    UpdateExperimentFragment.ARG_EXPERIMENT_ID);

            UpdateExperimentFragment fragment = UpdateExperimentFragment.newInstance(experimentId,
                    getIntent().getBooleanExtra(UpdateExperimentFragment.ARG_NEW, false),
                    (ComponentName) getIntent().getParcelableExtra(
                            UpdateExperimentFragment.ARG_PARENT_COMPONENT));
            fragment.setRetainInstance(true);

            getSupportFragmentManager().beginTransaction().add(R.id.container, fragment)
                    .commit();
        }

    }

    public static void launch(Context context, String experimentId, boolean isNewExperiment) {
        launch(context, experimentId, isNewExperiment, null);
    }

    public static void launch(Context context, String experimentId, boolean isNewExperiment,
                              ComponentName parentComponent) {
        final Intent intent = getLaunchIntent(context, experimentId, isNewExperiment,
                parentComponent);
        context.startActivity(intent);
    }

    public static Intent getLaunchIntent(Context context, String experimentId,
            boolean isNewExperiment, ComponentName parentComponent) {
        final Intent intent = new Intent(context, UpdateExperimentActivity.class);
        intent.putExtra(UpdateExperimentFragment.ARG_EXPERIMENT_ID, experimentId);
        intent.putExtra(UpdateExperimentFragment.ARG_NEW, isNewExperiment);
        intent.putExtra(UpdateExperimentFragment.ARG_PARENT_COMPONENT, parentComponent);
        return intent;
    }
}
