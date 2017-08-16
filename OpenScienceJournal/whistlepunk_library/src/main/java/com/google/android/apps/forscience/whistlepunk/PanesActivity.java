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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
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
import com.tbruyelle.rxpermissions2.RxPermissions;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;

public class PanesActivity extends AppCompatActivity implements RecordFragment.CallbacksProvider,
        CameraFragment.ListenerProvider, TextToolFragment.ListenerProvider {
    /**
     * Just for development, will be removed once shared control bar works
     */
    private static final boolean SHARED_CONTROL_BAR = false;

    private static final String TAG = "PanesActivity";
    private static final String EXTRA_EXPERIMENT_ID = "experimentId";
    private static final String KEY_SELECTED_TAB_INDEX = "selectedTabIndex";

    private ProgressBar mRecordingBar;
    private int mSelectedTabIndex;
    private PanesBottomSheetBehavior mBottomBehavior;
    private boolean mTabsInitialized;

    private static enum ToolTab {
        NOTES(R.string.tab_description_add_note, R.drawable.ic_comment_white_24dp) {
            @Override
            public Fragment createFragment(String experimentId, Activity activity) {
                return TextToolFragment.newInstance();
            }
        }, OBSERVE(R.string.tab_description_observe, R.drawable.sensortab_white_24dp) {
            @Override
            public Fragment createFragment(String experimentId, Activity activity) {
                return RecordFragment.newInstance(experimentId, true, false, !SHARED_CONTROL_BAR);
            }
        }, CAMERA(R.string.tab_description_camera, R.drawable.ic_camera_white_24dp) {
            @Override
            public Fragment createFragment(String experimentId, Activity activity) {
                // TODO: b/62022245
                return CameraFragment.newInstance(new RxPermissions(activity));
            }

            @Override
            public Runnable onGainedFocus(Object object) {
                CameraFragment fragment = (CameraFragment) object;
                fragment.onGainedFocus();
                return () -> fragment.onLosingFocus();
            }
        }, GALLERY(R.string.tab_description_gallery, R.drawable.ic_photo_white_24dp) {
            @Override
            public Fragment createFragment(String experimentId, Activity activity) {
                return GalleryFragment.newInstance(new RxPermissions(activity));
            }
        };

        private final int mContentDescriptionId;
        private final int mIconId;

        ToolTab(int contentDescriptionId, int iconId) {
            mContentDescriptionId = contentDescriptionId;
            mIconId = iconId;
        }

        public abstract Fragment createFragment(String experimentId, Activity activity);

        public int getContentDescriptionId() {
            return mContentDescriptionId;
        }

        public int getIconId() {
            return mIconId;
        }

        /**
         * Called when focus is gained by this tab.
         *
         * @return a Runnable that should be called when focus is lost, or null to do nothing.
         */
        public Runnable onGainedFocus(Object object) {
            // by default, do nothing
            return null;
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
                    showRecordingBar();
                    Log.d(TAG, "start recording");
                    mExperimentFragment.onStartRecording(
                            status.currentRecording.getRunId());
                } else {
                    hideRecordingBar();
                }
            });
        });

        Single<Experiment> exp = whenSelectedExperiment(experimentId, getDataController());
        exp.takeUntil(mDestroyed.happensNext()).subscribe(mActiveExperiment);

        AppSingleton.getInstance(this)
                    .whenLabelsAdded()
                    .takeUntil(mDestroyed.happens())
                    .subscribe(event -> onLabelAdded(event.getTrialId()));

        View bottomControlBar = findViewById(R.id.bottom_control_bar);
        if (SHARED_CONTROL_BAR) {
            bottomControlBar.setVisibility(View.VISIBLE);
        } else {
            bottomControlBar.setVisibility(View.GONE);
        }

        setCoordinatorBehavior(bottomControlBar, new BottomDependentBehavior() {
            @Override
            public boolean onDependentViewChanged(CoordinatorLayout parent, View child,
                    View dependency) {
                View holder = dependency.findViewById(R.id.tool_picker_holder);
                int childTop = child.getTop();
                int bottomOfTabs = dependency.getTop() + holder.getBottom();

                // Translate down enough to get out of the way of the tabs
                int translateY = Math.max(0, bottomOfTabs - childTop);
                if (child.getTranslationY() != translateY) {
                    child.setTranslationY(translateY);
                    return true;
                }

                return false;
            }
        });
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
        View experimentPane = findViewById(R.id.experiment_pane);

        if (!experiment.isArchived()) {
            setCoordinatorBehavior(experimentPane, new BottomDependentBehavior() {
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

            bottomSheet.setVisibility(View.VISIBLE);
            findViewById(R.id.shadow).setVisibility(View.VISIBLE);
            mBottomBehavior = (PanesBottomSheetBehavior)
                    ((CoordinatorLayout.LayoutParams) bottomSheet.getLayoutParams())
                            .getBehavior();

            // TODO: could this be FragmentStatePagerAdapter?  Would the fragment lifecycle methods
            //       get called in time to remove the camera preview views and avoid b/64442501?
            final FragmentPagerAdapter adapter = new FragmentPagerAdapter(getFragmentManager()) {
                // TODO: extract and test this.
                private int mPreviousPrimary = -1;
                private Runnable mOnLosingFocus = null;

                @Override
                public Fragment getItem(int position) {
                    if (position >= ToolTab.values().length) {
                        return null;
                    }
                    return getToolTab(position).createFragment(experiment.getExperimentId(),
                            PanesActivity.this);
                }

                private ToolTab getToolTab(int position) {
                    return ToolTab.values()[position];
                }

                @Override
                public int getCount() {
                    return ToolTab.values().length;
                }

                @Override
                public void setPrimaryItem(ViewGroup container, int position, Object object) {
                    if (position != mPreviousPrimary && mOnLosingFocus != null) {
                        mOnLosingFocus.run();
                        mOnLosingFocus = null;
                    }
                    super.setPrimaryItem(container, position, object);
                    if (position != mPreviousPrimary) {
                        mOnLosingFocus = getToolTab(position).onGainedFocus(object);
                        mPreviousPrimary = position;
                    }
                }

            };
            pager.setAdapter(adapter);

            if (toolPicker.getTabCount() > 0) {
                ViewGroup.LayoutParams layoutParams = experimentPane.getLayoutParams();
                layoutParams.height = bottomSheet.getTop();
                experimentPane.setLayoutParams(layoutParams);
                toolPicker.getTabAt(mSelectedTabIndex).select();

                // It's already initialized. Don't do it again!
                return;
            }

            mBottomBehavior.setPeekHeight(getResources().getDimensionPixelSize(
                    R.dimen.panes_toolbar_height));

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
                    openPaneIfNeeded();
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
                    } else {
                        // Pull it up if it's the already selected item.
                        openPaneIfNeeded();
                    }
                }
            });
            mTabsInitialized = false;
            toolPicker.getTabAt(mSelectedTabIndex).select();
            if (experiment.getLabelCount() > 0 || experiment.getTrialCount() > 0) {
                mBottomBehavior.setState(PanesBottomSheetBehavior.STATE_COLLAPSED);
            } else {
                mBottomBehavior.setState(PanesBottomSheetBehavior.STATE_MIDDLE);
            }
            mTabsInitialized = true;
        } else {
            bottomSheet.setVisibility(View.GONE);
            findViewById(R.id.shadow).setVisibility(View.GONE);
            experimentPane.setLayoutParams(new CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Clear the tabs, which releases all cameras and removes all triggers etc.
            pager.setAdapter(null);
        }
    }

    private void setCoordinatorBehavior(View view, BottomDependentBehavior behavior) {
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) view.getLayoutParams();
        params.setBehavior(behavior);
        view.setLayoutParams(params);
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
            public void onRecordingRequested(String experimentName) {
                showRecordingBar();
            }

            @Override
            void onRecordingStart(String trialId) {
                if (!TextUtils.isEmpty(trialId)) {
                    mExperimentFragment.onStartRecording(trialId);
                }
            }

            @Override
            void onRecordingStopped() {
                hideRecordingBar();
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
                return PanesActivity.this.getActiveExperimentId();
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
                            .subscribe(() -> onLabelAdded(trialId));
        });
    }

    private void onLabelAdded(String trialId) {
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

    private void showRecordingBar() {
        if (mRecordingBar != null) {
            mRecordingBar.setVisibility(View.VISIBLE);
            mBottomBehavior.setPeekHeight(
                    mRecordingBar.getResources().getDimensionPixelSize(
                            R.dimen.panes_toolbar_height) +
                    mRecordingBar.getResources().getDimensionPixelSize(
                            R.dimen.recording_indicator_height));
        }
    }

    private void hideRecordingBar() {
        if (mRecordingBar != null) {
            mRecordingBar.setVisibility(View.GONE);
            if (mBottomBehavior != null) {
                // Null if we are in an archived experiment.
                mBottomBehavior.setPeekHeight(mRecordingBar.getResources()
                        .getDimensionPixelSize(R.dimen.panes_toolbar_height));
            }
        }
    }

    private void openPaneIfNeeded() {
        // Only do the work if it is initialized. This keeps the pane from jumping open and closed
        // when the views are first loaded.
        if (mTabsInitialized) {
            // Clicking a tab raises the pane to middle if it was at the bottom.
            if (mBottomBehavior.getState() == PanesBottomSheetBehavior.STATE_COLLAPSED) {
                mBottomBehavior.setState(PanesBottomSheetBehavior.STATE_MIDDLE);
            }
        }
    }

    private Observable<String> getActiveExperimentId() {
        return mActiveExperiment.map(e -> e.getExperimentId()).toObservable();
    }

    private static class BottomDependentBehavior extends CoordinatorLayout.Behavior {
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
    }
}
