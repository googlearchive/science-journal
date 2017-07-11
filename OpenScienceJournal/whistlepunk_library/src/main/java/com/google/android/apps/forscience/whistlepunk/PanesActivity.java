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
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.jakewharton.rxbinding2.view.RxView;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;

public class PanesActivity extends AppCompatActivity implements RecordFragment.CallbacksProvider,
        AddNoteDialog.ListenerProvider, CameraFragment.ListenerProvider {
    private static final String TAG = "PanesActivity";
    private static final String EXTRA_EXPERIMENT_ID = "experimentId";

    private static enum ToolTab {
        NOTES(R.string.tab_description_add_note, R.drawable.ic_notes_white_24dp) {
            @Override
            public Fragment createFragment() {
                return AddNoteDialog.createWithNoExperimentYet(
                        R.string.add_experiment_note_placeholder_text);
            }
        }, OBSERVE(R.string.tab_description_observe, R.drawable.ic_observe_white_24dp) {
            @Override
            public Fragment createFragment() {
                return RecordFragment.newInstance(true, false);
            }
        }, CAMERA(R.string.tab_description_camera, R.drawable.ic_camera_white_24dp) {
            @Override
            public Fragment createFragment() {
                // TODO: b/62022245
                return CameraFragment.newInstance();
            }
        };

        private final int mContentDescriptionId;
        private final int mIconId;

        ToolTab(int contentDescriptionId, int iconId) {
            mContentDescriptionId = contentDescriptionId;
            mIconId = iconId;
        }

        public abstract Fragment createFragment();

        public int getContentDescriptionId() {
            return mContentDescriptionId;
        }

        public int getIconId() {
            return mIconId;
        }
    }

    public static void launch(Context context, String experimentId) {
        Intent intent = new Intent(context, PanesActivity.class);
        intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
        context.startActivity(intent);
    }

    private ExperimentDetailsFragment mExperimentFragment = null;
    private CompositeDisposable mUntilDestroyed = new CompositeDisposable();

    /**
     * BehaviorSubject remembers the last loaded value (if any) and delivers it, and all subsequent
     * values, to any observers.
     * <p>
     * TODO: use mActiveExperiment for other places that need an experiment in this class and
     * fragments.
     * <p>
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
                if (position >= ToolTab.values().length) {
                    return null;
                }
                return ToolTab.values()[position].createFragment();
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
        pager.setAdapter(adapter);

        View bottomSheet = findViewById(R.id.bottom);
        TabLayout toolPicker = (TabLayout) findViewById(R.id.tool_picker);
        RxView.globalLayouts(bottomSheet).firstElement().subscribe(o -> {
            // After first layout, height is valid

            BottomSheetBehavior<View> bottom = BottomSheetBehavior.from(bottomSheet);
            bottom.setBottomSheetCallback(
                    new TriStateCallback(bottom, true, getWindow().getDecorView().getHeight(),
                            toolPicker));
        });

        for (ToolTab tab : ToolTab.values()) {
            TabLayout.Tab layoutTab = toolPicker.newTab();
            layoutTab.setContentDescription(tab.getContentDescriptionId());
            layoutTab.setIcon(tab.getIconId());
            layoutTab.setTag(tab);
            toolPicker.addTab(layoutTab);
        }

        toolPicker.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                ToolTab toolTab = (ToolTab) tab.getTag();
                pager.setCurrentItem(toolTab.ordinal(), true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        // By adding the subscription to mUntilDestroyed, we make sure that we can disconnect from
        // the experiment stream when this activity is destroyed.
        mUntilDestroyed.add(mActiveExperiment.subscribe(experiment -> {
            setExperimentFragmentId(experiment.getExperimentId());
        }));

        // TODO: can I do this without a behavior subject?
        String experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);

        if (experimentId == null) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Launching most recent experiment");
            }
            RxDataController.loadOrCreateRecentExperiment(getDataController())
                            .toObservable()
                            .subscribe(mActiveExperiment);
        } else {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Launching specified experiment id: " + experimentId);
            }
            getDataController().getExperimentById(experimentId,
                    MaybeConsumers.fromObserver(mActiveExperiment));
        }

        View experimentPane = findViewById(R.id.experiment_pane);
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) experimentPane.getLayoutParams();
        params.setBehavior(new CoordinatorLayout.Behavior() {
            @Override
            public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
                if (dependency.getId() == R.id.bottom) {
                    return true;
                } else {
                    return super.layoutDependsOn(parent, child, dependency);
                }
            }

            @Override
            public boolean onDependentViewChanged(CoordinatorLayout parent, View child,
                    View dependency) {
                int desiredBottom = dependency.getTop();
                int currentBottom = child.getBottom();

                if (desiredBottom != currentBottom) {
                    ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                    layoutParams.height = desiredBottom - child.getTop();
                    child.setLayoutParams(layoutParams);
                    return true;
                } else {
                    return super.onDependentViewChanged(parent, child, dependency);
                }
            }
        });
        experimentPane.setLayoutParams(params);
    }

    private void setExperimentFragmentId(String experimentId) {
        if (mExperimentFragment == null) {
            boolean createTaskStack = false;
            boolean oldestAtTop = true;
            boolean disappearingActionBar = false;
            Label deletedLabel = getDeletedLabel();
            mExperimentFragment =
                    ExperimentDetailsFragment.newInstance(experimentId,
                            createTaskStack, oldestAtTop, disappearingActionBar, deletedLabel);

            FragmentManager fragmentManager = getFragmentManager();
            fragmentManager.beginTransaction()
                           .replace(R.id.experiment_pane, mExperimentFragment)
                           .commit();
        } else {
            mExperimentFragment.setExperimentId(experimentId);
        }
    }

    private Label getDeletedLabel() {
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            return null;
        } else {
            return extras.getParcelable(ExperimentDetailsFragment.ARG_DELETED_LABEL);
        }
    }

    @Override
    protected void onDestroy() {
        mUntilDestroyed.dispose();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mExperimentFragment.handleOnBackPressed()) {
            return;
        }
        super.onBackPressed();
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

            @Override
            public Single<String> whenExperimentId() {
                return mActiveExperiment.firstElement().toSingle().map(e -> e.getExperimentId());
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
                experimentMaybe.subscribe(e -> {
                    e.addLabel(label);
                    AddNoteDialog.saveExperiment(getDataController(), e, label)
                                 .subscribe(MaybeConsumers.toSingleObserver(
                                         getAddNoteDialogListener().onLabelAdd()));
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
