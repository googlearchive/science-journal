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

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;

import java.util.List;

public class PanesActivity extends AppCompatActivity {
    private static final String TAG = "PanesActivity";
    private ExperimentDetailsFragment mExperimentFragment = null;
    private AddNoteDialog mAddNoteDialog = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.panes_layout);

        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        final FragmentPagerAdapter adapter = new FragmentPagerAdapter(getFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                switch (position) {
                    case 0:
                        return RecordFragment.newInstance();
                    case 1:
                        return mAddNoteDialog;
                }
                return null;
            }

            @Override
            public int getCount() {
                return mAddNoteDialog == null ? 1 : 2;
            }
        };
        pager.setAdapter(adapter);

        getMetadataController().addExperimentChangeListener(TAG,
                new MetadataController.MetadataChangeListener() {
                    @Override
                    public void onMetadataChanged(Project newProject,
                            List<Experiment> newExperiments) {
                        String experimentId = newExperiments.get(0).getExperimentId();
                        setExperimentFragmentId(experimentId);
                        setNoteFragmentId(experimentId);
                    }

                    private void setExperimentFragmentId(String experimentId) {
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

                    private void setNoteFragmentId(String experimentId) {
                        if (mAddNoteDialog == null) {
                            mAddNoteDialog = makeNoteFragment(experimentId);
                            adapter.notifyDataSetChanged();
                        } else {
                            mAddNoteDialog.setExperimentId(experimentId);
                        }
                    }
                });
    }

    private AddNoteDialog makeNoteFragment(String experimentId) {
        // TODO: this is wrong, should be timestamp of _creation_
        long now = AppSingleton.getInstance(this).getSensorEnvironment().getDefaultClock().getNow();
        return AddNoteDialog.newInstance(now, RecorderController.NOT_RECORDING_RUN_ID, experimentId,
                R.string.add_experiment_note_placeholder_text);
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
