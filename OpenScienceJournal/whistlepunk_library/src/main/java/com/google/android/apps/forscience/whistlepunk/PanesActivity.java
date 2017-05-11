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

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;

import io.reactivex.Maybe;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;

public class PanesActivity extends AppCompatActivity implements RecordFragment.CallbacksProvider,
        AddNoteDialog.ListenerProvider, CameraFragment.ListenerProvider {
    private static final String TAG = "PanesActivity";
    private ExperimentDetailsFragment mExperimentFragment = null;
    private AddNoteDialog mAddNoteDialog = null;

    /**
     * BehaviorSubject remembers the last loaded value (if any) and delivers it, and all subsequent
     * values, to any observers.
     *
     * TODO: use mActiveExperiment for other places that need an experiment in this class and
     *       fragments.
     *
     * (First use of RxJava.)
     */
    private BehaviorSubject<Experiment> mActiveExperiment = BehaviorSubject.create();

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
                        return RecordFragment.newInstance(true);
                    case 1:
                        return CameraFragment.newInstance();
                    case 2:
                        return mAddNoteDialog;
                }
                return null;
            }

            @Override
            public int getCount() {
                return mAddNoteDialog == null ? 2 : 3;
            }
        };
        pager.setAdapter(adapter);

        getMetadataController().addExperimentChangeListener(TAG,
                new MetadataController.MetadataChangeListener() {
                    @Override
                    public void onMetadataChanged(Experiment activeExperiment) {
                        mActiveExperiment.onNext(activeExperiment);
                        String experimentId = activeExperiment.getExperimentId();
                        setExperimentFragmentId(experimentId);
                        setNoteFragmentId(experimentId);
                    }

                    private void setExperimentFragmentId(String experimentId) {
                        if (mExperimentFragment == null) {
                            boolean createTaskStack = false;
                            boolean oldestAtTop = true;
                            mExperimentFragment =
                                    ExperimentDetailsFragment.newInstance(experimentId,
                                            createTaskStack, oldestAtTop);

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
        return AddNoteDialog.createWithDynamicTimestamp(RecorderController.NOT_RECORDING_RUN_ID,
                experimentId, R.string.add_experiment_note_placeholder_text);
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

    @Override
    public RecordFragment.UICallbacks getRecordFragmentCallbacks() {
        return new RecordFragment.UICallbacks() {
            @Override
            void onRecordingSaved(String runId, Experiment experiment) {
                mExperimentFragment.loadExperimentData(experiment);
            }

            @Override
            public void onLabelAdded(Label label) {
                // TODO: is this expensive?  Should we trigger a more incremental update?
                mExperimentFragment.loadExperiment();
            }
        };
    }

    @Override
    public AddNoteDialog.AddNoteDialogListener getAddNoteDialogListener() {
        return new AddNoteDialog.AddNoteDialogListener() {
            @Override
            public MaybeConsumer<Label> onLabelAdd() {
                return new LoggingConsumer<Label>(TAG, "refresh with added label") {
                    @Override
                    public void success(Label value) {
                        // TODO: avoid database round-trip?
                        mExperimentFragment.loadExperiment();
                    }
                };
            }
        };
    }

    @Override
    public CameraFragment.CameraFragmentListener getCameraFragmentListener() {
        return new CameraFragment.CameraFragmentListener() {
            @Override
            public void onPictureLabelTaken(final Label label) {
                // Get the most recent experiment, or wait if none has been loaded yet.
                Maybe<Experiment> experimentMaybe = mActiveExperiment.firstElement();
                experimentMaybe.subscribe(new Consumer<Experiment>() {
                    @Override
                    public void accept(Experiment e) throws Exception {
                        // TODO: change this to lambda once we can use Java 8.
                        e.addLabel(label);
                        AddNoteDialog.saveExperiment(getDataController(), e, label,
                                getAddNoteDialogListener().onLabelAdd());
                    }
                });
            }
        };
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(PanesActivity.this).getDataController();
    }
}
