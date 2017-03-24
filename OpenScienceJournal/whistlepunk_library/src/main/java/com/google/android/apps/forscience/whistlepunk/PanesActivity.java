/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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
package com.google.android.apps.forscience.whistlepunk;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;

import java.util.List;

public class PanesActivity extends AppCompatActivity {
    private static final String TAG = "PanesActivity";
    private ExperimentDetailsFragment mExperimentFragment = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.panes_layout);

        getFragmentManager().beginTransaction()
                            .replace(R.id.toolbox_pane, RecordFragment.newInstance())
                            .commit();

        getMetadataController().addExperimentChangeListener(TAG,
                new MetadataController.MetadataChangeListener() {
                    @Override
                    public void onMetadataChanged(Project newProject,
                            List<Experiment> newExperiments) {
                        String experimentId = newExperiments.get(0).getExperimentId();
                        if (mExperimentFragment == null) {
                            boolean createTaskStack = false;
                            mExperimentFragment =
                                    ExperimentDetailsFragment.newInstance(experimentId,
                                            createTaskStack);

                            FragmentManager fragmentManager = getFragmentManager();
                            fragmentManager.beginTransaction()
                                           .replace(R.id.experiment_pane, mExperimentFragment)
                                           .commit();
                        } else {
                            mExperimentFragment.setExperimentId(experimentId);
                        }
                    }
                });
    }

    @NonNull
    private MetadataController getMetadataController() {
        return AppSingleton.getInstance(this).getMetadataController();
    }

    @Override
    protected void onDestroy() {
        getMetadataController().removeExperimentChangeListener(TAG);
        super.onDestroy();
    }
}
