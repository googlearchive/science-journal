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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.viewpager.widget.ViewPager;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.common.collect.ImmutableList;
import com.jakewharton.rxbinding2.view.RxView;
import com.tbruyelle.rxpermissions2.RxPermissions;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.SingleSubject;
import java.util.ArrayList;
import java.util.List;

public class PanesActivity extends AppCompatActivity
    implements RecordFragment.CallbacksProvider,
        CameraFragment.ListenerProvider,
        TextToolFragment.ListenerProvider,
        GalleryFragment.ListenerProvider,
        PanesToolFragment.EnvProvider,
        ExperimentDetailsFragment.ListenerProvider {
  private static final String TAG = "PanesActivity";
  public static final String EXTRA_ACCOUNT_KEY = "accountKey";
  public static final String EXTRA_EXPERIMENT_ID = "experimentId";
  public static final String EXTRA_CLAIM_EXPERIMENTS_MODE = "claimExperimentsMode";
  private static final String KEY_SELECTED_TAB_INDEX = "selectedTabIndex";
  private static final String KEY_DRAWER_STATE = "drawerState";
  private final SnackbarManager snackbarManager;

  private ProgressBar recordingBar;
  private int selectedTabIndex;
  private PanesBottomSheetBehavior bottomBehavior;
  private boolean tabsInitialized;
  private BehaviorSubject<Integer> activityHeight = BehaviorSubject.create();
  private BehaviorSubject<Integer> bottomSheetState = BehaviorSubject.create();
  private ImageButton grabber;
  protected List<ToolTab> toolTabs;
  private RxPermissions permissions;
  private int initialDrawerState = -1;
  private RxEvent paused = new RxEvent();
  private AppAccount appAccount;
  private boolean claimExperimentsMode;
  private String experimentId;

  /**
   * Displays the experiment and the tool drawer.
   *
   * @deprecated Moving to {@link ExperimentActivity} to use the new action area.
   */
  @Deprecated
  public PanesActivity() {
    snackbarManager = new SnackbarManager();
  }

  protected List<ToolTab> getToolTabs() {
    if (toolTabs == null) {
      toolTabs = new ArrayList<>(
          ImmutableList.of(ToolTab.NOTES, ToolTab.OBSERVE, ToolTab.CAMERA, ToolTab.GALLERY));
    }
    return toolTabs;
  }

  private DrawerLayoutState newDrawerLayoutState(
      int activityHeight, int drawerState, Experiment experiment) {
    return new DrawerLayoutState(activityHeight, drawerState, experiment, claimExperimentsMode);
  }

  public static class DrawerLayoutState {
    private final int activityHeight;
    private final int drawerState;
    private final Experiment experiment;
    private final boolean claimExperimentsMode;

    private DrawerLayoutState(
        int activityHeight, int drawerState, Experiment experiment, boolean claimExperimentsMode) {
      this.activityHeight = activityHeight;
      this.drawerState = drawerState;
      this.experiment = experiment;
      this.claimExperimentsMode = claimExperimentsMode;
    }

    public int getAvailableHeight() {
      if (claimExperimentsMode) {
        // No control bar in claim experiments mode.
        return 0;
      }

      if (experiment.isArchived()) {
        // No matter the state, the control bar is hidden when archived.
        return 0;
      }

      switch (drawerState) {
        case PanesBottomSheetBehavior.STATE_COLLAPSED:
          return 0;
        case PanesBottomSheetBehavior.STATE_EXPANDED:
          return activityHeight;
        case PanesBottomSheetBehavior.STATE_MIDDLE:
          return activityHeight / 2;
      }

      // Filter out other states
      return -1;
    }

    public int getDrawerState() {
      return drawerState;
    }
  }

  /** Tab which exposes the UI for a given observation type */
  public abstract static class ToolTab {
    private static ToolTab NOTES =
        new ToolTab(R.string.tab_description_add_note, R.drawable.ic_comment_white_24dp, "NOTES") {
          @Override
          public Fragment createFragment(
              AppAccount appAccount, String experimentId, AppCompatActivity activity) {
            return TextToolFragment.newInstance();
          }

          @Override
          public View connectControls(
              Fragment fragment,
              FrameLayout controlBar,
              ControlBarController controlBarController,
              Observable<DrawerLayoutState> layoutState) {
            TextToolFragment ttf = (TextToolFragment) fragment;
            LayoutInflater.from(controlBar.getContext())
                .inflate(R.layout.text_action_bar, controlBar, true);
            ttf.attachButtons(controlBar);
            ttf.listenToAvailableHeight(layoutState.map(state -> state.getAvailableHeight()));
            return ttf.getViewToKeepVisible();
          }
        };

    private static ToolTab OBSERVE =
        new ToolTab(R.string.tab_description_observe, R.drawable.sensortab_white_24dp, "OBSERVE") {
          @Override
          public Fragment createFragment(
              AppAccount appAccount, String experimentId, AppCompatActivity activity) {
            return RecordFragment.newInstance(appAccount, experimentId);
          }

          @Override
          public View connectControls(
              Fragment fragment,
              FrameLayout controlBar,
              ControlBarController controlBarController,
              Observable<DrawerLayoutState> availableHeight) {
            LayoutInflater.from(controlBar.getContext())
                .inflate(R.layout.observe_action_bar, controlBar, true);
            controlBarController.attachRecordButtons(
                controlBar, fragment.getChildFragmentManager());
            controlBarController.attachElapsedTime(controlBar, (RecordFragment) fragment);
            return null;
          }
        };

    private static ToolTab CAMERA =
        new ToolTab(R.string.tab_description_camera, R.drawable.ic_camera_white_24dp, "CAMERA") {
          @Override
          public Fragment createFragment(
              AppAccount appAccount, String experimentId, AppCompatActivity activity) {
            return CameraFragment.newInstance(appAccount);
          }

          @Override
          public View connectControls(
              Fragment fragment,
              FrameLayout controlBar,
              ControlBarController controlBarController,
              Observable<DrawerLayoutState> layoutState) {
            CameraFragment cf = (CameraFragment) fragment;
            LayoutInflater.from(controlBar.getContext())
                .inflate(R.layout.camera_action_bar, controlBar, true);
            cf.attachButtons(controlBar);
            return null;
          }
        };

    private static ToolTab GALLERY =
        new ToolTab(R.string.tab_description_gallery, R.drawable.ic_photo_white_24dp, "GALLERY") {
          @Override
          public Fragment createFragment(
              AppAccount appAccount, String experimentId, AppCompatActivity activity) {
            return GalleryFragment.newInstance(appAccount);
          }

          @Override
          public View connectControls(
              Fragment fragment,
              FrameLayout controlBar,
              ControlBarController controlBarController,
              Observable<DrawerLayoutState> availableHeight) {
            // TODO: is this duplicated code?
            GalleryFragment gf = (GalleryFragment) fragment;
            LayoutInflater.from(controlBar.getContext())
                .inflate(R.layout.gallery_action_bar, controlBar, true);
            gf.attachAddButton(controlBar);
            return null;
          }
        };
    private final int contentDescriptionId;
    private final int iconId;
    private final String loggingName;

    public ToolTab(int contentDescriptionId, int iconId, String loggingName) {
      this.contentDescriptionId = contentDescriptionId;
      this.iconId = iconId;
      this.loggingName = loggingName;
    }

    public abstract Fragment createFragment(
        AppAccount appAccount, String experimentId, AppCompatActivity activity);

    public int getContentDescriptionId() {
      return contentDescriptionId;
    }

    public int getIconId() {
      return iconId;
    }

    public String getLoggingName() {
      return loggingName;
    }

    /** @return a View to attempt to keep visible by resizing the drawer if possible */
    public abstract View connectControls(
        Fragment fragment,
        FrameLayout controlBar,
        ControlBarController controlBarController,
        Observable<DrawerLayoutState> layoutState);
  }

  public static void launch(
      Context context, AppAccount appAccount, String experimentId, boolean claimExperimentsMode) {
    Intent intent = launchIntent(context, appAccount, experimentId, claimExperimentsMode);
    context.startActivity(intent);
  }

  @NonNull
  public static Intent launchIntent(
      Context context, AppAccount appAccount, String experimentId, boolean claimExperimentsMode) {
    Intent intent = new Intent(context, PanesActivity.class);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    intent.putExtra(EXTRA_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
    return intent;
  }

  private ExperimentDetailsFragment experimentFragment = null;

  /**
   * SingleSubject remembers the loaded value (if any) and delivers it to any observers.
   *
   * <p>TODO: use activeExperiment for other places that need an experiment in this class and
   * fragments.
   */
  private SingleSubject<Experiment> activeExperiment = SingleSubject.create();

  private RxEvent destroyed = new RxEvent();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    claimExperimentsMode = getIntent().getBooleanExtra(EXTRA_CLAIM_EXPERIMENTS_MODE, false);
    if (claimExperimentsMode) {
      setTheme(R.style.preview_experiment_details);
    }
    super.onCreate(savedInstanceState);
    permissions = new RxPermissions(this);
    PerfTrackerProvider perfTracker = WhistlePunkApplication.getPerfTrackerProvider(this);
    PerfTrackerProvider.TimerToken experimentLoad = perfTracker.startTimer();
    setContentView(R.layout.panes_layout);
    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    RxView.layoutChangeEvents(findViewById(R.id.container))
        .subscribe(
            event -> {
              int bottom = event.bottom();
              int top = event.top();
              int height = bottom - top;
              activityHeight.onNext(height);
            });

    recordingBar = findViewById(R.id.recording_progress_bar);
    grabber = findViewById(R.id.grabber);

    appAccount = WhistlePunkApplication.getAccount(this, getIntent(), EXTRA_ACCOUNT_KEY);
    experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);

    selectedTabIndex = 0;
    if (savedInstanceState != null) {
      selectedTabIndex = savedInstanceState.getInt(KEY_SELECTED_TAB_INDEX);
      initialDrawerState = savedInstanceState.getInt(KEY_DRAWER_STATE);
    }

    // By adding the subscription to mUntilDestroyed, we make sure that we can disconnect from
    // the experiment stream when this activity is destroyed.
    activeExperiment.subscribe(
        experiment -> {
          setupViews(experiment);
          setExperimentFragmentId(experiment);
          AppSingleton.getInstance(this)
              .getRecorderController(appAccount)
              .watchRecordingStatus()
              .firstElement()
              .subscribe(
                  status -> {
                    if (status.state == RecordingState.ACTIVE) {
                      showRecordingBar();
                      Log.d(TAG, "start recording");
                      experimentFragment.onStartRecording(status.currentRecording.getRunId());
                    } else {
                      hideRecordingBar();
                    }
                    perfTracker.stopTimer(
                        experimentLoad, TrackerConstants.PRIMES_EXPERIMENT_LOADED);
                    perfTracker.onAppInteractive();
                  });
        });

    Single<Experiment> exp = whenSelectedExperiment(experimentId, getDataController());
    exp.takeUntil(destroyed.happensNext())
        .subscribe(activeExperiment::onSuccess, error -> finish());

    AppSingleton.getInstance(this)
        .whenLabelsAdded(appAccount)
        .takeUntil(destroyed.happens())
        .subscribe(event -> onLabelAdded(event.getTrialId()));

    View bottomControlBar = findViewById(R.id.bottom_control_bar);
    setCoordinatorBehavior(
        bottomControlBar,
        new BottomDependentBehavior() {
          @Override
          public boolean onDependentViewChanged(
              CoordinatorLayout parent, View child, View dependency) {
            int dependencyTop = dependency.getTop();
            int belowHalf = dependencyTop - (parent.getHeight() / 2);

            // Translate down once the drawer is below halfway
            int translateY = Math.max(0, belowHalf);
            if (child.getTranslationY() != translateY) {
              child.setTranslationY(translateY);
              return true;
            }

            return false;
          }
        });
    WhistlePunkApplication.getUsageTracker(this).trackScreenView(TrackerConstants.SCREEN_PANES);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putInt(KEY_SELECTED_TAB_INDEX, selectedTabIndex);
    outState.putInt(KEY_DRAWER_STATE, getBottomDrawerState());
    super.onSaveInstanceState(outState);
  }

  private int getBottomDrawerState() {
    if (bottomBehavior == null) {
      return PanesBottomSheetBehavior.STATE_MIDDLE;
    }
    return bottomBehavior.getState();
  }

  @VisibleForTesting
  public static Single<Experiment> whenSelectedExperiment(
      String experimentId, DataController dataController) {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, "Launching specified experiment id: " + experimentId);
    }
    return RxDataController.getExperimentById(dataController, experimentId);
  }

  public void onArchivedStateChanged(Experiment experiment) {
    setupViews(experiment);
    findViewById(R.id.container).requestLayout();
  }

  private void updateGrabberContentDescription() {
    int state = getBottomDrawerState();
    if (state == PanesBottomSheetBehavior.STATE_COLLAPSED) {
      grabber.setContentDescription(getResources().getString(R.string.btn_show_tools));
    } else if (state == PanesBottomSheetBehavior.STATE_MIDDLE) {
      grabber.setContentDescription(getResources().getString(R.string.btn_expand_tools));
    } else if (state == PanesBottomSheetBehavior.STATE_EXPANDED) {
      grabber.setContentDescription(getResources().getString(R.string.btn_hide_tools));
    }

    // Leave unchanged when in interstitial states
  }

  private void setupViews(Experiment experiment) {
    ControlBarController controlBarController =
        new ControlBarController(appAccount, experiment.getExperimentId(), snackbarManager);

    ViewPager pager = findViewById(R.id.pager);
    View bottomSheet = findViewById(R.id.bottom);
    TabLayout toolPicker = findViewById(R.id.tool_picker);
    View experimentPane = findViewById(R.id.experiment_pane);
    View controlBarSpacer = findViewById(R.id.control_bar_spacer);
    FrameLayout controlBar = findViewById(R.id.bottom_control_bar);

    if (!experiment.isArchived() && !claimExperimentsMode) {
      setCoordinatorBehavior(
          experimentPane,
          new BottomDependentBehavior() {
            @Override
            public boolean onDependentViewChanged(
                CoordinatorLayout parent, View child, View dependency) {
              int desiredBottom = dependency.getTop();
              int currentBottom = child.getBottom();

              if (desiredBottom != currentBottom
                  && dependency.getVisibility() != View.GONE
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

      controlBarSpacer.setVisibility(View.VISIBLE);
      controlBar.setVisibility(View.VISIBLE);
      bottomSheet.setVisibility(View.VISIBLE);
      findViewById(R.id.shadow).setVisibility(View.VISIBLE);
      bottomBehavior =
          (PanesBottomSheetBehavior)
              ((CoordinatorLayout.LayoutParams) bottomSheet.getLayoutParams()).getBehavior();
      bottomBehavior.setBottomSheetCallback(
          new PanesBottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
              bottomSheetState.onNext(newState);
              if (getBottomDrawerState() == PanesBottomSheetBehavior.STATE_COLLAPSED) {
                // We no longer need to know what happens when the keyboard closes:
                // Stay closed.
                KeyboardUtil.closeKeyboard(PanesActivity.this).subscribe();
              }
              updateGrabberContentDescription();
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
          });
      bottomBehavior.setAllowHalfHeightDrawerInLandscape(
          getResources().getBoolean(R.bool.allow_half_height_drawer_in_landscape));

      // TODO: could this be FragmentStatePagerAdapter?  Would the fragment lifecycle methods
      //       get called in time to remove the camera preview views and avoid b/64442501?
      final FragmentPagerAdapter adapter =
          new FragmentPagerAdapter(getSupportFragmentManager()) {
            // TODO: extract and test this.
            private int previousPrimary = -1;
            private Runnable onLosingFocus = null;

            @Override
            public Fragment getItem(int position) {
              if (position >= getToolTabs().size()) {
                return null;
              }
              return getToolTab(position)
                  .createFragment(appAccount, experiment.getExperimentId(), PanesActivity.this);
            }

            private ToolTab getToolTab(int position) {
              return getToolTabs().get(position);
            }

            @Override
            public int getCount() {
              return getToolTabs().size();
            }

            @Override
            public void setPrimaryItem(ViewGroup container, int position, Object object) {
              if (position != previousPrimary && onLosingFocus != null) {
                onLosingFocus.run();
                onLosingFocus = null;
              }
              super.setPrimaryItem(container, position, object);
              if (position != previousPrimary) {
                ToolTab toolTab = getToolTab(position);
                FrameLayout controlBar = findViewById(R.id.bottom_control_bar);
                controlBar.removeAllViews();
                PanesToolFragment fragment = (PanesToolFragment) object;
                fragment
                    .whenNextView()
                    .subscribe(
                        v -> {
                          bottomBehavior.setScrollingChild(v);

                          View viewToKeepVisible =
                              toolTab.connectControls(
                                  fragment, controlBar, controlBarController, drawerLayoutState());
                          bottomBehavior.setViewToKeepVisibleIfPossible(viewToKeepVisible);
                        });
                fragment.onGainedFocus(PanesActivity.this);
                onLosingFocus = () -> fragment.onLosingFocus();
                previousPrimary = position;
              }
            }
          };
      pager.setAdapter(adapter);

      initializeToolPicker(toolPicker, pager, experiment, bottomSheet, experimentPane);
    } else {
      // Either the experiment is archived or we are in claim experiments mode.
      controlBar.setVisibility(View.GONE);
      controlBarSpacer.setVisibility(View.GONE);
      bottomSheet.setVisibility(View.GONE);
      findViewById(R.id.shadow).setVisibility(View.GONE);
      experimentPane.setLayoutParams(
          new CoordinatorLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      // Clear the tabs, which releases all cameras and removes all triggers etc.
      pager.setAdapter(null);
    }
  }

  private void initializeToolPicker(
      TabLayout toolPicker,
      ViewPager pager,
      Experiment experiment,
      View bottomSheet,
      View experimentPane) {
    if (toolPicker.getTabCount() > 0) {
      ViewGroup.LayoutParams layoutParams = experimentPane.getLayoutParams();
      layoutParams.height = bottomSheet.getTop();
      experimentPane.setLayoutParams(layoutParams);
      toolPicker.getTabAt(selectedTabIndex).select();

      // It's already initialized. Don't do it again!
      return;
    }

    bottomBehavior.setPeekHeight(
        getResources().getDimensionPixelSize(R.dimen.panes_toolbar_height));

    for (ToolTab tab : getToolTabs()) {
      TabLayout.Tab layoutTab = toolPicker.newTab();
      layoutTab.setContentDescription(tab.getContentDescriptionId());
      layoutTab.setIcon(tab.getIconId());
      layoutTab.setTag(tab);
      toolPicker.addTab(layoutTab);
    }

    toolPicker.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            selectedTabIndex = tab.getPosition();
            pager.setCurrentItem(selectedTabIndex, true);
            openPaneIfNeeded();
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            if (pager.getCurrentItem() != selectedTabIndex) {
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
    tabsInitialized = false;
    toolPicker.getTabAt(selectedTabIndex).select();

    if (initialDrawerState >= 0) {
      bottomBehavior.setState(initialDrawerState);
    } else if (experiment.getLabelCount() > 0 || experiment.getTrialCount() > 0) {
      bottomBehavior.setState(PanesBottomSheetBehavior.STATE_COLLAPSED);
    } else {
      bottomBehavior.setState(PanesBottomSheetBehavior.STATE_MIDDLE);
    }
    updateGrabberContentDescription();
    tabsInitialized = true;

    bottomSheetState.onNext(getBottomDrawerState());
  }

  private void setupGrabber() {
    if (AccessibilityUtils.isAccessibilityManagerEnabled(this)) {
      grabber.setOnClickListener(
          view -> {
            if (getBottomDrawerState() == PanesBottomSheetBehavior.STATE_COLLAPSED) {
              changeSheetState(
                  PanesBottomSheetBehavior.STATE_COLLAPSED, PanesBottomSheetBehavior.STATE_MIDDLE);
            } else if (getBottomDrawerState() == PanesBottomSheetBehavior.STATE_MIDDLE) {
              changeSheetState(
                  PanesBottomSheetBehavior.STATE_MIDDLE, PanesBottomSheetBehavior.STATE_EXPANDED);
            } else if (getBottomDrawerState() == PanesBottomSheetBehavior.STATE_EXPANDED) {
              changeSheetState(
                  PanesBottomSheetBehavior.STATE_EXPANDED,
                  PanesBottomSheetBehavior.STATE_COLLAPSED);
            }
          });
    }
  }

  private Observable<DrawerLayoutState> drawerLayoutState() {
    // keep an eye on activity height, bottom sheet state, and experiment loading.
    return Observable.combineLatest(
            activityHeight.distinctUntilChanged(),
            bottomSheetState.distinctUntilChanged(),
            activeExperiment.toObservable(),
            this::newDrawerLayoutState)
        .filter(state -> state.getAvailableHeight() >= 0);
  }

  private void setCoordinatorBehavior(View view, BottomDependentBehavior behavior) {
    CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) view.getLayoutParams();
    params.setBehavior(behavior);
    view.setLayoutParams(params);
  }

  private void setExperimentFragmentId(Experiment experiment) {
    FragmentManager fragmentManager = getSupportFragmentManager();

    if (experimentFragment == null) {
      // If we haven't cached the fragment, go looking for it.
      ExperimentDetailsFragment oldFragment =
          (ExperimentDetailsFragment) fragmentManager.findFragmentById(R.id.experiment_pane);
      if (oldFragment != null
          && oldFragment.getExperimentId().equals(experiment.getExperimentId())) {
        experimentFragment = oldFragment;
        return;
      }
    }

    if (experimentFragment == null) {
      boolean createTaskStack = true;
      experimentFragment =
          ExperimentDetailsFragment.newInstance(
              appAccount, experiment.getExperimentId(), createTaskStack, claimExperimentsMode);

      fragmentManager.beginTransaction().replace(R.id.experiment_pane, experimentFragment).commit();
    } else {
      experimentFragment.setExperimentId(experiment.getExperimentId());
    }
  }

  @Override
  protected void onDestroy() {
    destroyed.onHappened();
    super.onDestroy();
  }

  @Override
  public void onResume() {
    super.onResume();
    AppSingleton appSingleton = AppSingleton.getInstance(this);
    appSingleton.setResumedActivity(this);
    paused.happensNext().subscribe(() -> appSingleton.setNoLongerResumedActivity(this));

    if (!isMultiWindowEnabled()) {
      updateRecorderControllerForResume();
    }
    setupGrabber();
    if (appSingleton.getAndClearMostRecentOpenWasImport()) {
      AccessibilityUtils.makeSnackbar(
              findViewById(R.id.bottom_control_bar),
              getResources().getString(R.string.import_failed_recording),
              Snackbar.LENGTH_SHORT)
          .show();
    }
  }

  @Override
  protected void onPause() {
    if (!isMultiWindowEnabled()) {
      updateRecorderControllerForPause();
      logPanesState(TrackerConstants.ACTION_PAUSED);
    }
    paused.happens();
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
      logPanesState(TrackerConstants.ACTION_PAUSED);
    }
    super.onStop();
  }

  private void logPanesState(String action) {
    SparseArray<String> dimensions = new SparseArray<>();
    if (tabsInitialized) {
      dimensions.append(
          TrackerConstants.PANES_DRAWER_STATE, bottomBehavior.getDrawerStateForLogging());
      dimensions.append(
          TrackerConstants.PANES_TOOL_NAME, getToolTabs().get(selectedTabIndex).getLoggingName());
    }
    WhistlePunkApplication.getUsageTracker(this)
        .trackDimensionEvent(TrackerConstants.CATEGORY_PANES, action, dimensions);
  }

  private boolean isMultiWindowEnabled() {
    return MultiWindowUtils.isMultiWindowEnabled(getApplicationContext());
  }

  private void updateRecorderControllerForResume() {
    RecorderController rc = AppSingleton.getInstance(this).getRecorderController(appAccount);
    rc.setRecordActivityInForeground(true);
  }

  private void updateRecorderControllerForPause() {
    RecorderController rc = AppSingleton.getInstance(this).getRecorderController(appAccount);
    rc.setRecordActivityInForeground(false);
  }

  @Override
  public void onBackPressed() {
    if (experimentFragment.handleOnBackPressed()) {
      return;
    }
    super.onBackPressed();
  }

  @Override
  public RecordFragment.UICallbacks getRecordFragmentCallbacks() {
    return new RecordFragment.UICallbacks() {
      @Override
      void onRecordingSaved(String runId, Experiment experiment) {
        logPanesState(TrackerConstants.ACTION_RECORDED);
        experimentFragment.loadExperimentData(experiment);
      }

      @Override
      public void onRecordingRequested(String experimentName, boolean userInitiated) {
        showRecordingBar();
        // We don't call expandSheet until after we've called
        // experimentFragment.onStartRecording (below in onRecordingStart). Otherwise, the
        // ExperimentFragment won't be able to scroll to the bottom because the details
        // lists's height will be zero. Scrolling doesn't work if a View's height is zero.
      }

      @Override
      void onRecordingStart(RecordingStatus recordingStatus) {
        if (recordingStatus.state == RecordingState.STOPPING) {
          // If we call "recording start" when stopping it leads to extra work.
          return;
        }
        String trialId = recordingStatus.getCurrentRunId();
        if (!TextUtils.isEmpty(trialId)) {
          experimentFragment.onStartRecording(trialId);
        }
        // Now that experimentFragment.onStartRecording has been called (and it has
        // scrolled to the bottom), we can call expandSheet.
        if (tabsInitialized && recordingStatus.userInitiated) {
          expandSheet();
        }
      }

      @Override
      void onRecordingStopped() {
        hideRecordingBar();
        experimentFragment.onStopRecording();
        dropToHalfScreenIfNeeded();
      }

      @Override
      void maximizeFragment() {
        expandSheet();
      }
    };
  }

  @Override
  public PanesToolFragment.Env getPanesToolEnv() {
    return () -> drawerLayoutState().map(state -> state.getDrawerState());
  }

  @Override
  public CameraFragment.CameraFragmentListener getCameraFragmentListener() {
    return new CameraFragment.CameraFragmentListener() {
      @Override
      public RxPermissions getPermissions() {
        return permissions;
      }

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
  public GalleryFragment.Listener getGalleryListener() {
    return new GalleryFragment.Listener() {
      @Override
      public Observable<String> getActiveExperimentId() {
        return PanesActivity.this.getActiveExperimentId();
      }

      @Override
      public void onPictureLabelTaken(Label label) {
        addNewLabel(label);
      }

      @Override
      public RxPermissions getPermissions() {
        return permissions;
      }
    };
  }

  @Override
  public TextToolFragment.TextLabelFragmentListener getTextLabelFragmentListener() {
    return result -> addNewLabel(result);
  }

  // Lint doesn't like "subscribe" without doing anything with the return value. But that's not how
  // that works.
  @SuppressLint("CheckResult")
  private void addNewLabel(Label label) {
    // Reload the current experiment in case the ActiveExperiment Object has changed beneath us.
    RxDataController.getExperimentById(getDataController(), experimentId)
        .subscribe(
            e -> {
              // if it is recording, add it to the recorded trial instead!
              String trialId = experimentFragment.getActiveRecordingId();
              if (TextUtils.isEmpty(trialId)) {
                e.addLabel(e, label);
              } else {
                e.getTrial(trialId).addLabel(e, label);
              }
              RxDataController.updateExperiment(getDataController(), e, true)
                  .subscribe(() -> onLabelAdded(trialId), error -> onAddNewLabelFailed());
            },
            error -> {
              if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "addNewLabel failed", error);
              }
              onAddNewLabelFailed();
            });
  }

  private void onAddNewLabelFailed() {
    AccessibilityUtils.makeSnackbar(
            findViewById(R.id.bottom_control_bar),
            getResources().getString(R.string.label_failed_save),
            Snackbar.LENGTH_LONG)
        .show();
  }

  private void dropToHalfScreenIfNeeded() {
    changeSheetState(
        PanesBottomSheetBehavior.STATE_EXPANDED, PanesBottomSheetBehavior.STATE_MIDDLE);
  }

  private void changeSheetState(int fromState, int toState) {
    if (bottomBehavior == null) {
      // Experiment is archived, there's no sheet to change
      return;
    }
    if (getBottomDrawerState() == fromState) {
      bottomBehavior.setState(toState);
    }
  }

  private void expandSheet() {
    if (getBottomDrawerState() != PanesBottomSheetBehavior.STATE_EXPANDED) {
      bottomBehavior.setState(PanesBottomSheetBehavior.STATE_EXPANDED);
    }
  }

  private void onLabelAdded(String trialId) {
    logPanesState(TrackerConstants.ACTION_LABEL_ADDED);
    if (TextUtils.isEmpty(trialId)) {
      // TODO: is this expensive?  Should we trigger a more incremental update?
      experimentFragment.reloadAndScrollToBottom();
    } else {
      experimentFragment.onRecordingTrialUpdated(trialId);
    }
    dropToHalfScreenIfNeeded();
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(PanesActivity.this).getDataController(appAccount);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String permissions[], int[] grantResults) {
    PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void showRecordingBar() {
    if (recordingBar != null) {
      recordingBar.setVisibility(View.VISIBLE);
      if (bottomBehavior != null) {
        bottomBehavior.setPeekHeight(
            recordingBar.getResources().getDimensionPixelSize(R.dimen.panes_toolbar_height)
                + recordingBar
                    .getResources()
                    .getDimensionPixelSize(R.dimen.recording_indicator_height));
      }
    }
  }

  private void hideRecordingBar() {
    if (recordingBar != null) {
      recordingBar.setVisibility(View.GONE);
      if (bottomBehavior != null) {
        // Null if we are in an archived experiment.
        bottomBehavior.setPeekHeight(
            recordingBar.getResources().getDimensionPixelSize(R.dimen.panes_toolbar_height));
      }
    }
  }

  private void openPaneIfNeeded() {
    // Only do the work if it is initialized. This keeps the pane from jumping open and closed
    // when the views are first loaded.
    if (tabsInitialized) {
      // Clicking a tab raises the pane to middle if it was at the bottom.
      changeSheetState(
          PanesBottomSheetBehavior.STATE_COLLAPSED, PanesBottomSheetBehavior.STATE_MIDDLE);
    }
  }

  private Observable<String> getActiveExperimentId() {
    return activeExperiment.map(e -> e.getExperimentId()).toObservable();
  }

  private static class BottomDependentBehavior extends CoordinatorLayout.Behavior {
    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
      if (dependency.getId() == R.id.bottom && dependency.getVisibility() != View.GONE) {
        return true;
      } else {
        return super.layoutDependsOn(parent, child, dependency);
      }
    }
  }

  @Override
  public ExperimentDetailsFragment.Listener getExperimentDetailsFragmentListener() {
    return new ExperimentDetailsFragment.Listener() {
      @Override
      public void onArchivedStateChanged(Experiment changed) {
        PanesActivity.this.onArchivedStateChanged(changed);
      }
    };
  }
}
