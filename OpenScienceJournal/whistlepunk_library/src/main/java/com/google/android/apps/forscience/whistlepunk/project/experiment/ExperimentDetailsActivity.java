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
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.project.MetadataActivity;

public class ExperimentDetailsActivity extends MetadataActivity {
    private static final String FRAGMENT_TAG = "ExperimentDetailsFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment_details);

        Bundle extras = getIntent().getExtras();
        if (savedInstanceState == null && extras != null) {
            String experimentId = extras.getString(ExperimentDetailsFragment.ARG_EXPERIMENT_ID);
            Label deletedLabel = extras.getParcelable(ExperimentDetailsFragment.ARG_DELETED_LABEL);
            boolean oldestAtTop = false;
            boolean disappearingActionBar = true;
            ExperimentDetailsFragment fragment = ExperimentDetailsFragment.newInstance(experimentId,
                    extras.getBoolean(ExperimentDetailsFragment.ARG_CREATE_TASK, false),
                    oldestAtTop, disappearingActionBar, deletedLabel);
            getFragmentManager().beginTransaction().add(R.id.container, fragment,
                    FRAGMENT_TAG).commit();
        } else if (savedInstanceState != null) {
            setExperimentId(
                    savedInstanceState.getString(ExperimentDetailsFragment.ARG_EXPERIMENT_ID));
        }
    }

    private void setExperimentId(String experimentId) {
        ExperimentDetailsFragment fragment = getFragment();
        if (fragment != null) {
            fragment.setExperimentId(experimentId);
        }
    }

    private ExperimentDetailsFragment getFragment() {
        return (ExperimentDetailsFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String experimentId = intent.getExtras().getString(
                ExperimentDetailsFragment.ARG_EXPERIMENT_ID);
        if (experimentId != null) {
            setExperimentId(experimentId);
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
        savedInstanceState.putString(ExperimentDetailsFragment.ARG_EXPERIMENT_ID,
                getExperimentId());
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

    public String getExperimentId() {
        ExperimentDetailsFragment fragment = getFragment();
        return fragment != null ? fragment.getExperimentId() : null;
    }

    @Override
    public void onBackPressed() {
        if (getFragment().handleOnBackPressed()) {
            return;
        }
        super.onBackPressed();
    }
}
