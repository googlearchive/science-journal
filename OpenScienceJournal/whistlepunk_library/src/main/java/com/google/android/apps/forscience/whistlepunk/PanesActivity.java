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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.jakewharton.rxbinding2.view.RxView;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.SingleSubject;

public class PanesActivity extends AppCompatActivity implements RecordFragment.CallbacksProvider,
        CameraFragment.ListenerProvider, TextToolFragment.ListenerProvider {
    public static final int PERMISSIONS_AUDIO_RECORD_REQUEST = 1;
    private static final String TAG = "PanesActivity";
    private static final String EXTRA_EXPERIMENT_ID = "experimentId";
    private static final String KEY_SELECTED_TAB_INDEX = "selectedTabIndex";

    private ProgressBar mRecordingBar;
    private int mSelectedTabIndex;

    private static enum ToolTab {
        NOTES(R.string.tab_description_add_note, R.drawable.ic_comment_white_24dp) {
            @Override
            public Fragment createFragment(String experimentId) {
                return TextToolFragment.newInstance();
            }
        }, OBSERVE(R.string.tab_description_observe, R.drawable.sensortab_white_24dp) {
            @Override
            public Fragment createFragment(String experimentId) {
                return RecordFragment.newInstance(experimentId, true, false);
            }
        }, CAMERA(R.string.tab_description_camera, R.drawable.ic_camera_white_24dp) {
            @Override
            public Fragment createFragment(String experimentId) {
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

        public abstract Fragment createFragment(String experimentId);

        public int getContentDescriptionId() {
            return mContentDescriptionId;
        }

        public int getIconId() {
            return mIconId;
        }
    }

    public static void launch(Context context, String experimentId) {
        Intent intent = launchIntent(context, experimentId);
        context.startActivity(intent);
    }

    @NonNull
    public static Intent launchIntent(Context context, String experimentId) {
        Intent intent = new Intent(context, PanesActivity.class);
        intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
        return intent;
    }

    private ExperimentDetailsFragment mExperimentFragment = null;

    /**
     * SingleSubject remembers the loaded value (if any) and delivers it to any observers.
     * <p>
     * TODO: use mActiveExperiment for other places that need an experiment in this class and
     * fragments.
     */
    private SingleSubject<Experiment> mActiveExperiment = SingleSubject.create();
    private RxEvent mDestroyed = new RxEvent();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.panes_layout);

        mRecordingBar = (ProgressBar) findViewById(R.id.recording_progress_bar);

        String experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);

        mSelectedTabIndex = 0;
        if (savedInstanceState != null) {
            mSelectedTabIndex = savedInstanceState.getInt(KEY_SELECTED_TAB_INDEX);
        }

        // By adding the subscription to mUntilDestroyed, we make sure that we can disconnect from
        // the experiment stream when this activity is destroyed.
        mActiveExperiment.subscribe(experiment -> {
            setupViews(experiment);
            setExperimentFragmentId(experiment);
            AppSingleton.getInstance(this).getRecorderController().watchRecordingStatus()
                    .firstElement().subscribe(status -> {
                        if (status.state == RecordingState.ACTIVE) {
                            mRecordingBar.setVisibility(View.VISIBLE);
                            Log.d(TAG, "start recording");
                            mExperimentFragment.onStartRecording(
                                    status.currentRecording.getRunId());
                        } else {
                            mRecordingBar.setVisibility(View.GONE);
                        }
                    });
        });

        Single<Experiment> exp = whenSelectedExperiment(experimentId, getDataController());
        exp.takeUntil(mDestroyed.happensNext()).subscribe(mActiveExperiment);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_SELECTED_TAB_INDEX, mSelectedTabIndex);
        super.onSaveInstanceState(outState);
    }

    @VisibleForTesting
    public static Single<Experiment> whenSelectedExperiment(String experimentId,
            DataController dataController) {
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "Launching specified experiment id: " + experimentId);
        }
        return RxDataController.getExperimentById(dataController, experimentId);
    }

    public void onArchivedStateChanged(Experiment experiment) {
        setupViews(experiment);
        findViewById(R.id.container).requestLayout();
    }

    private void setupViews(Experiment experiment) {
        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        View bottomSheet = findViewById(R.id.bottom);
        TabLayout toolPicker = (TabLayout) findViewById(R.id.tool_picker);
        View toolPickerHolder = findViewById(R.id.tool_picker_holder);
        View experimentPane = findViewById(R.id.experiment_pane);
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) experimentPane.getLayoutParams();
        params.setBehavior(new CoordinatorLayout.Behavior() {
            @Override
            public boolean layoutDependsOn(CoordinatorLayout parent, View child,
                    View dependency) {
                if (dependency.getId() == R.id.bottom &&
                        dependency.getVisibility() != View.GONE) {
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

                if (desiredBottom != currentBottom && dependency.getVisibility() != View.GONE
                        && dependency.getId() == R.id.bottom) {
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

        if (!experiment.isArchived()) {
            bottomSheet.setVisibility(View.VISIBLE);
            findViewById(R.id.shadow).setVisibility(View.VISIBLE);

            final FragmentPagerAdapter adapter = new FragmentPagerAdapter(getFragmentManager()) {
                @Override
                public Fragment getItem(int position) {
                    if (position >= ToolTab.values().length) {
                        return null;
                    }
                    return ToolTab.values()[position].createFragment(experiment.getExperimentId());
                }

                @Override
                public int getCount() {
                    return 3;
                }
            };
            pager.setAdapter(adapter);

            if (toolPicker.getTabCount() > 0) {
                experimentPane.getLayoutParams().height = bottomSheet.getTop();
                experimentPane.setLayoutParams(params);
                toolPicker.getTabAt(mSelectedTabIndex).select();

                // It's already initialized. Don't do it again!
                return;
            }

            RxView.globalLayouts(bottomSheet).firstElement().subscribe(o -> {
                // After first layout, height is valid

                BottomSheetBehavior<View> bottom = BottomSheetBehavior.from(bottomSheet);
                bottom.setBottomSheetCallback(
                        new TriStateCallback(bottom, true, getWindow().getDecorView().getHeight(),
                                toolPickerHolder));
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
                    mSelectedTabIndex = toolTab.ordinal();
                    pager.setCurrentItem(mSelectedTabIndex, true);
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {

                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                    if (pager.getCurrentItem() != mSelectedTabIndex) {
                        // After archive/unarchive we can get a state where the tab is technically
                        // selected but the pager has not updated properly. This forces the
                        // update to the pager fragment.
                        onTabSelected(tab);
                    }
                }
            });
            toolPicker.getTabAt(mSelectedTabIndex).select();
        } else {
            bottomSheet.setVisibility(View.GONE);
            findViewById(R.id.shadow).setVisibility(View.GONE);
            params.height = ((ViewGroup) experimentPane.getParent()).getHeight();
            experimentPane.setLayoutParams(params);

            // Clear the tabs, which releases all cameras and removes all triggers etc.
            pager.setAdapter(null);
        }
    }

    private void setExperimentFragmentId(Experiment experiment) {
        if (mExperimentFragment == null) {
            boolean createTaskStack = false;
            Label deletedLabel = getDeletedLabel();
            mExperimentFragment =
                    ExperimentDetailsFragment.newInstance(experiment.getExperimentId(),
                            createTaskStack, deletedLabel);

            FragmentManager fragmentManager = getFragmentManager();
            fragmentManager.beginTransaction()
                           .replace(R.id.experiment_pane, mExperimentFragment)
                           .commit();
        } else {
            mExperimentFragment.setExperimentId(experiment.getExperimentId());
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
        mDestroyed.onHappened();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isMultiWindowEnabled()) {
            updateRecorderControllerForResume();
        }
    }

    @Override
    protected void onPause() {
        if (!isMultiWindowEnabled()) {
            updateRecorderControllerForPause();
        }
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isMultiWindowEnabled()) {
            updateRecorderControllerForResume();
        }
    }

    @Override
    protected void onStop() {
        if (isMultiWindowEnabled()) {
            updateRecorderControllerForPause();
        }
        super.onStop();
    }

    private boolean isMultiWindowEnabled() {
        return MultiWindowUtils.isMultiWindowEnabled(getApplicationContext());
    }

    private void updateRecorderControllerForResume() {
        RecorderController rc = AppSingleton.getInstance(this).getRecorderController();
        rc.setRecordActivityInForeground(true);
    }

    private void updateRecorderControllerForPause() {
        RecorderController rc = AppSingleton.getInstance(this).getRecorderController();
        rc.setRecordActivityInForeground(false);
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
            public void onLabelAdded(Label label, String trialId) {
                PanesActivity.this.onLabelAdded(label, trialId);
            }

            @Override
            public void onRecordingRequested(String experimentName) {
                if (mRecordingBar != null) {
                    mRecordingBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            void onRecordingStart(String trialId) {
                if (!TextUtils.isEmpty(trialId)) {
                    mExperimentFragment.onStartRecording(trialId);
                }
            }

            @Override
            void onRecordingStopped() {
                if (mRecordingBar != null) {
                    mRecordingBar.setVisibility(View.GONE);
                }
                mExperimentFragment.onStopRecording();
            }
        };
    }

    @Override
    public CameraFragment.CameraFragmentListener getCameraFragmentListener() {
        return new CameraFragment.CameraFragmentListener() {
            @Override
            public void onPictureLabelTaken(final Label label) {
                addNewLabel(label);
            }

            @Override
            public Observable<String> getActiveExperimentId() {
                return mActiveExperiment.map(e -> e.getExperimentId()).toObservable();
            }
        };
    }

    @Override
    public TextToolFragment.TextLabelFragmentListener getTextLabelFragmentListener() {
        return new TextToolFragment.TextLabelFragmentListener() {
            @Override
            public void onTextLabelTaken(Label result) {
                addNewLabel(result);
            }
        };
    }

    private void addNewLabel(Label label) {
        // Get the most recent experiment, or wait if none has been loaded yet.
        mActiveExperiment.subscribe(e -> {
            // if it is recording, add it to the recorded trial instead!
            String trialId = mExperimentFragment.getActiveRecordingId();
            if (TextUtils.isEmpty(trialId)) {
                e.addLabel(label);
            } else {
                e.getTrial(trialId).addLabel(label);
            }
            RxDataController.updateExperiment(getDataController(), e)
                    .subscribe(() -> onLabelAdded(label, trialId));
        });
    }

    private void onLabelAdded(Label label, String trialId) {
        if (TextUtils.isEmpty(trialId)) {
            // TODO: is this expensive?  Should we trigger a more incremental update?
            mExperimentFragment.loadExperiment();
        } else {
            mExperimentFragment.onRecordingTrialUpdated(trialId);
        }
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(PanesActivity.this).getDataController();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions,
                grantResults);
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
                // fourth of the height is taken up by bottom panel, shift to settling to middle.
                if (slideOffset > 0.25) {
                    setSettlingToMiddle(true);
                }
            }
        }
    }
}
