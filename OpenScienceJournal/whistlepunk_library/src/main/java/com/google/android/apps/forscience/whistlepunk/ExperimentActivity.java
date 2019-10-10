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
import android.content.res.Resources.Theme;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.ActionBar;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;
import com.google.android.apps.forscience.whistlepunk.MoreObservationsFragment.ObservationOption;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaItem;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView.ActionAreaListener;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.arcore.ARVelocityActivity;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsWithActionAreaFragment;
import com.google.android.apps.forscience.whistlepunk.sensors.VelocitySensor;
import com.google.android.material.snackbar.Snackbar;
import io.reactivex.Observable;
import io.reactivex.subjects.SingleSubject;
import java.util.ArrayList;
import java.util.List;

/** Displays the experiment and the action bar. */
public class ExperimentActivity extends NoteTakingActivity
    implements SensorFragment.CallbacksProvider,
        ExperimentDetailsWithActionAreaFragment.ListenerProvider,
        ActionAreaListener {

  private static final String EXTRA_FROM_SENSOR_FRAGMENT = "fromSensorFragmentKey";

  @NonNull
  public static Intent launchIntent(
      Context context, AppAccount appAccount, String experimentId, boolean claimExperimentsMode) {
    Intent intent = new Intent(context, ExperimentActivity.class);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    intent.putExtra(EXTRA_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
    return intent;
  }

  // SingleSubject remembers the loaded value (if any) and delivers it to any observers.
  private SingleSubject<Experiment> activeExperiment = SingleSubject.create();
  private ExperimentDetailsWithActionAreaFragment experimentFragment = null;
  private boolean claimExperimentsMode;
  private RxEvent destroyed = new RxEvent();
  private boolean isRecording;
  private boolean toolFragmentOpenedFromSensorFragment;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    claimExperimentsMode = getIntent().getBooleanExtra(EXTRA_CLAIM_EXPERIMENTS_MODE, false);
    if (claimExperimentsMode) {
      setTheme(R.style.preview_experiment_details);
    }
    super.onCreate(savedInstanceState);

    PerfTrackerProvider perfTracker = WhistlePunkApplication.getPerfTrackerProvider(this);
    PerfTrackerProvider.TimerToken experimentLoad = perfTracker.startTimer();

    // By adding the subscription to mUntilDestroyed, we make sure that we can disconnect from
    // the experiment stream when this activity is destroyed.
    activeExperiment.subscribe(
        experiment -> {
          setExperimentFragmentId(experiment);
          AppSingleton.getInstance(this)
              .getRecorderController(appAccount)
              .watchRecordingStatus()
              .firstElement()
              .subscribe(
                  status -> {
                    if (status.state == RecordingState.ACTIVE) {
                      experimentFragment.onStartRecording(status.currentRecording.getRunId());
                    }
                    perfTracker.stopTimer(
                        experimentLoad, TrackerConstants.PRIMES_EXPERIMENT_LOADED);
                    perfTracker.onAppInteractive();
                  });
        });
    exp.takeUntil(destroyed.happensNext())
        .subscribe(activeExperiment::onSuccess, error -> finish());

    subscribeToRecordingStatus();

    WhistlePunkApplication.getUsageTracker(this)
        .trackScreenView(TrackerConstants.SCREEN_EXPERIMENT);
  }

  @Override
  public void onClick(ActionAreaItem item) {
    onClick(item, false);
  }

  public void onClick(ActionAreaItem item, boolean fromSensorFragment) {
    toolFragmentOpenedFromSensorFragment = fromSensorFragment;
    super.onClick(item);
  }

  @SuppressLint("CheckResult")
  private void subscribeToRecordingStatus() {
    Observable<RecordingStatus> recordingStatus =
        AppSingleton.getInstance(this).getRecorderController(appAccount).watchRecordingStatus();

    recordingStatus.subscribe(
        status -> {
          ActionBar bar = getSupportActionBar();
          Window window = getWindow();
          if (status.state.shouldShowStopButton()) {
            isRecording = true;
            bar.setBackgroundDrawable(
                new ColorDrawable(getResources().getColor(R.color.app_bar_red)));
            window.setStatusBarColor(getResources().getColor(R.color.status_bar_red));
          } else {
            isRecording = false;
            bar.setBackgroundDrawable(
                new ColorDrawable(getResources().getColor(R.color.app_bar_purple)));
            window.setStatusBarColor(getResources().getColor(R.color.color_primary_dark));
          }
        });
  }

  public void onArchivedStateChanged() {
    findViewById(R.id.experiment_pane).requestLayout();
  }

  private void setExperimentFragmentId(Experiment experiment) {
    if (experimentFragment == null) {
      ExperimentDetailsWithActionAreaFragment oldFragment = lookupExperimentFragment();
      if (oldFragment != null
          && oldFragment.getExperimentId().equals(experiment.getExperimentId())) {
        experimentFragment = oldFragment;
        return;
      }
    }

    if (experimentFragment == null) {
      experimentFragment = createExperimentFragment(experiment.getExperimentId());
    } else {
      experimentFragment.setExperimentId(experiment.getExperimentId());
    }
    setUpFragments();
  }

  @Override
  protected Fragment getDefaultFragment() {
    return experimentFragment;
  }

  @Override
  protected String getDefaultToolFragmentTag() {
    return DEFAULT_ADD_MORE_OBSERVATIONS_TAG;
  }

  private ExperimentDetailsWithActionAreaFragment lookupExperimentFragment() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    return (ExperimentDetailsWithActionAreaFragment)
        fragmentManager.findFragmentByTag(DEFAULT_FRAGMENT_TAG);
  }

  private ExperimentDetailsWithActionAreaFragment createExperimentFragment(String id) {
    return ExperimentDetailsWithActionAreaFragment.newInstance(
        appAccount, id, true /* createTaskStack */, claimExperimentsMode);
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!isMultiWindowEnabled()) {
      updateRecorderControllerForResume();
    }
    AppSingleton appSingleton = AppSingleton.getInstance(this);
    if (appSingleton.getAndClearMostRecentOpenWasImport()) {
      AccessibilityUtils.makeSnackbar(
              findViewById(R.id.tool_pane),
              getResources().getString(R.string.import_failed_recording),
              Snackbar.LENGTH_SHORT)
          .show();
    }
  }

  @Override
  protected void onPause() {
    if (!isMultiWindowEnabled()) {
      updateRecorderControllerForPause();
      logState(TrackerConstants.ACTION_PAUSED);
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
      logState(TrackerConstants.ACTION_PAUSED);
    }
    super.onStop();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(EXTRA_FROM_SENSOR_FRAGMENT, toolFragmentOpenedFromSensorFragment);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    toolFragmentOpenedFromSensorFragment =
        savedInstanceState.getBoolean(EXTRA_FROM_SENSOR_FRAGMENT);
  }

  private void logState(String action) {
    WhistlePunkApplication.getUsageTracker(this)
        .trackEvent(TrackerConstants.CATEGORY_EXPERIMENT, action, null, 0);
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
  public SensorFragment.UICallbacks getRecordFragmentCallbacks() {
    return new SensorFragment.UICallbacks() {
      @Override
      void onRecordingSaved(String runId, Experiment experiment) {
        logState(TrackerConstants.ACTION_RECORDED);
        experimentFragment.loadExperimentData(experiment);
        onNoteSaved();
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
      }

      @Override
      void onRecordingStopped() {
        experimentFragment.onStopRecording();
      }
    };
  }

  private void onNoteSaved() {
    if (!isTwoPane() && SENSOR_TAG.equals(activeToolFragmentTag)) {
      AccessibilityUtils.makeSnackbar(
          findViewById(R.id.tool_pane),
          getResources().getString(R.string.note_saved),
          Snackbar.LENGTH_LONG,
          getResources().getString(R.string.note_saved_action),
          v -> showDefaultFragments())
          .show();
    }
  }

  @Override
  protected void onLabelAdded(String trialId, Label label) {
    if (TextUtils.isEmpty(trialId)) {
      // TODO: is this expensive?  Should we trigger a more incremental update?
      experimentFragment.reloadAndScrollToBottom();
    } else {
      experimentFragment.onRecordingTrialUpdated(trialId);
    }
    onNoteSaved();
    logState(TrackerConstants.ACTION_LABEL_ADDED);
  }

  @Override
  public Theme getActivityTheme() {
    int themeResId = isRecording ? R.style.RedActionAreaIcon : R.style.DefaultActionAreaIcon;
    return new ContextThemeWrapper(this, themeResId).getTheme();
  }

  @Override
  public void closeToolFragment() {
    super.closeToolFragment();
    if (isRecording && toolFragmentOpenedFromSensorFragment) {
      openToolFragment(SENSOR_TAG);
      toolFragmentOpenedFromSensorFragment = false;
    }
  }

  @Override
  protected String getTrialIdForLabel() {
    return experimentFragment.getActiveRecordingId();
  }

  @Override
  protected boolean handleDefaultFragmentOnBackPressed() {
    return experimentFragment.handleOnBackPressed();
  }

  private void openGallery() {
    openToolFragment(GALLERY_TAG);
  }

  private void openDraw() {
    // TODO(b/112632194): start ink activity here
  }

  private void openVelocityTracker() {
    startActivity(ARVelocityActivity.getIntent(
        this, appAccount, getIntent().getStringExtra(EXTRA_EXPERIMENT_ID)));
  }

  protected ObservationOption[] getMoreObservationOptions() {
    List<ObservationOption> options = new ArrayList<>();
    ObservationOption galleryOption =
        new ObservationOption(
            R.string.more_observations_gallery_title,
            R.string.more_observations_gallery_description,
            R.drawable.more_observations_gallery,
            (View view) -> openGallery());
    ObservationOption drawOption =
        new ObservationOption(
            R.string.more_observations_draw_title,
            R.string.more_observations_draw_description,
            R.drawable.more_observations_draw,
            (View view) -> openDraw());
    ObservationOption velocityTrackerOption =
        new ObservationOption(
            R.string.more_observations_velocity_tracker_title,
            R.string.more_observations_velocity_tracker_description,
            R.drawable.more_observations_velocity_tracker,
            (View view) -> openVelocityTracker());

    options.add(galleryOption);
    if (Flags.showDrawOption()) {
      options.add(drawOption);
    }
    if (VelocitySensor.isVelocitySensorAvailable(getApplicationContext())) {
      options.add(velocityTrackerOption);
    }

    return options.toArray(new ObservationOption[options.size()]);
  }

  // Show more option button iff velocity tracker and/or drawing notes are enabled
  private boolean showMoreOptions() {
    return Flags.showDrawOption() || Flags.showVelocityTrackerOption();
  }

  @Override
  public ActionAreaItem[] getActionAreaItems() {
    ActionAreaItem[] actionAreaItems = {
      ActionAreaItem.NOTE, ActionAreaItem.SENSOR, ActionAreaItem.CAMERA, ActionAreaItem.GALLERY
    };
    if (showMoreOptions()) {
      actionAreaItems[3] = ActionAreaItem.MORE;
    }
    return actionAreaItems;
  }

  @Override
  protected Fragment newInstanceAddMoreObservationNotes() {
    return AddMoreObservationNotesFragment.newInstance(false /* isRunReview */);
  }
}
