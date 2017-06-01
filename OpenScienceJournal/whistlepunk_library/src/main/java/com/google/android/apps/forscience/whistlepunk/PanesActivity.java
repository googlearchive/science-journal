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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.jakewharton.rxbinding2.view.RxView;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;

public class PanesActivity extends AppCompatActivity implements RecordFragment.CallbacksProvider,
        AddNoteDialog.ListenerProvider, CameraFragment.ListenerProvider {
    private static final String TAG = "PanesActivity";
    private static final String EXTRA_EXPERIMENT_ID = "experimentId";

    public static void launch(Context context, String experimentId) {
        Intent intent = new Intent(context, PanesActivity.class);
        intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
        context.startActivity(intent);
    }

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
                        return RecordFragment.newInstance(true, false);
                    case 1:
                        // TODO: b/62022245
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

        View bottomSheet = findViewById(R.id.bottom);
        RxView.globalLayouts(bottomSheet).firstElement().subscribe(o -> {
            // After first layout, height is valid

            BottomSheetBehavior<View> bottom = BottomSheetBehavior.from(bottomSheet);
            bottom.setBottomSheetCallback(
                    new TriStateCallback(bottom, true, getWindow().getDecorView().getHeight(),
                            findViewById(R.id.tool_picker)));
        });

        mActiveExperiment.subscribe(experiment -> {
            String experimentId = experiment.getExperimentId();
            setExperimentFragmentId(experimentId);
            setNoteFragmentId(adapter, experimentId);
        });

        String experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);


        if (experimentId == null) {
            getMetadataController().addExperimentChangeListener(TAG,
                    new MetadataController.MetadataChangeListener() {
                        @Override
                        public void onMetadataChanged(Experiment activeExperiment) {
                            if (Log.isLoggable(TAG, Log.INFO)) {
                                Log.i(TAG, "Launching most recent experiment id: "
                                           + activeExperiment.getExperimentId());
                            }
                            mActiveExperiment.onNext(activeExperiment);
                        }
                    });
        } else {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Launching specified experiment id: " + experimentId);
            }
            getDataController().getExperimentById(experimentId,
                    MaybeConsumers.fromObserver(mActiveExperiment));
        }
    }

    private void setExperimentFragmentId(String experimentId) {
        if (mExperimentFragment == null) {
            boolean createTaskStack = false;
            boolean oldestAtTop = true;
            boolean disappearingActionBar = false;
            mExperimentFragment =
                    ExperimentDetailsFragment.newInstance(experimentId,
                            createTaskStack, oldestAtTop, disappearingActionBar);

            FragmentManager fragmentManager = getFragmentManager();
            fragmentManager.beginTransaction()
                           .replace(R.id.experiment_pane, mExperimentFragment)
                           .commit();
        } else {
            mExperimentFragment.setExperimentId(experimentId);
        }
    }

    private void setNoteFragmentId(FragmentPagerAdapter adapter, String experimentId) {
        if (mAddNoteDialog == null) {
            mAddNoteDialog = makeNoteFragment(experimentId);
            adapter.notifyDataSetChanged();
        } else {
            mAddNoteDialog.setExperimentId(experimentId);
        }
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
                        AddNoteDialog.saveExperiment(getDataController(), e, label)
                                     .subscribe(MaybeConsumers.toSingleObserver(
                                             getAddNoteDialogListener().onLabelAdd()));
                    }
                });
            }

            @Override
            public Observable<String> getActiveExperimentId() {
                return mActiveExperiment.map(e -> e.getExperimentId());
            }
        };
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(PanesActivity.this).getDataController();
    }

    // TODO: this is acceptable, but still a bit wonky.  For example, it's hard to get from bottom
    // back to middle without going to top first.
    // Keep adjusting.

    /**
     * A callback that allows a bottom call sheet to snap to middle, bottom, and top positions.
     */
    private static class TriStateCallback extends BottomSheetBehavior.BottomSheetCallback {
        private boolean mCurrentlySettlingToMiddle;
        private int mFullHeight;
        private final View mVisibleOnBottom;
        private BottomSheetBehavior<View> mBottom;

        public TriStateCallback(BottomSheetBehavior<View> bottom,
                boolean currentlySettlingToMiddle, int fullHeight, View visibleOnBottom) {
            mBottom = bottom;
            mFullHeight = fullHeight;
            mVisibleOnBottom = visibleOnBottom;
            setSettlingToMiddle(currentlySettlingToMiddle);
        }

        private void adjustSettling() {
            if (mCurrentlySettlingToMiddle) {
                // set peek height to halfway up page
                mBottom.setPeekHeight(mFullHeight / 2);

                // Hideable is true so that we can drag _beneath_ peek height
                mBottom.setHideable(true);
            } else {
                // set peek height to bottom
                mBottom.setPeekHeight(mVisibleOnBottom.getHeight());

                // Hideable is false so that we don't actually drag off screen (hopefully)
                mBottom.setHideable(false);
            }
        }

        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                // Should never _actually_ hide.  Instead, reset to bottom peeking.
                setSettlingToMiddle(false);
                mBottom.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        }

        private void setSettlingToMiddle(boolean currentlySettlingToMiddle) {
            mCurrentlySettlingToMiddle = currentlySettlingToMiddle;
            adjustSettling();
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            if (mCurrentlySettlingToMiddle) {
                // If we're currently settling to middle, but we've slid such that less than a
                // fourth of the height is taken up by the bottom panel, shift to settling to the
                // bottom
                if (slideOffset < -0.5) {
                    setSettlingToMiddle(false);
                }
            } else {
                // If we're currently settling to bottom, but we've slid such that more than a
                // fourth of the height is taken up by bottom panel, shift to settlingt to middle.
                if (slideOffset > 0.25) {
                    setSettlingToMiddle(true);
                }
            }
        }
    }
}
