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

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.project.MetadataActivity;

public class ExperimentDetailsActivity extends MetadataActivity {
    private String mExperimentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment_details);

        Bundle extras = getIntent().getExtras();
        if (savedInstanceState == null && extras != null) {
            mExperimentId = extras.getString(
                    ExperimentDetailsFragment.ARG_EXPERIMENT_ID);
            ExperimentDetailsFragment fragment = ExperimentDetailsFragment.newInstance(
                    mExperimentId, extras.getBoolean(ExperimentDetailsFragment.ARG_CREATE_TASK,
                            false));
            getFragmentManager().beginTransaction().add(R.id.container, fragment).commit();
        } else if (savedInstanceState != null){
            mExperimentId = savedInstanceState.getString(
                    ExperimentDetailsFragment.ARG_EXPERIMENT_ID);
        }

    }

    public static void launch(Context context, String experimentId) {
        launch(context, experimentId, false /* no need to create task stack */);
    }

    public static void launch(Context context, String experimentId, boolean createTaskStack) {
        final Intent intent = getLaunchIntent(context, experimentId, createTaskStack);
        context.startActivity(intent);
    }

    @NonNull
    public static Intent getLaunchIntent(Context context, String experimentId,
            boolean createTaskStack) {
        final Intent intent = new Intent(context, ExperimentDetailsActivity.class);
        intent.putExtra(ExperimentDetailsFragment.ARG_EXPERIMENT_ID, experimentId);
        intent.putExtra(ExperimentDetailsFragment.ARG_CREATE_TASK, createTaskStack);
        return intent;
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(ExperimentDetailsFragment.ARG_EXPERIMENT_ID, mExperimentId);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        PictureUtils.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

}
