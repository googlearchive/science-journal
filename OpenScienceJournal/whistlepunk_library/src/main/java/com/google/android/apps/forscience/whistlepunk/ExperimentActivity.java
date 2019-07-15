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

import static com.google.android.apps.forscience.whistlepunk.PictureUtils.REQUEST_TAKE_PHOTO;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.support.design.snackbar.Snackbar;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import com.google.android.apps.forscience.whistlepunk.CameraFragment.ListenerProvider;
import com.google.android.apps.forscience.whistlepunk.MoreObservationsFragment.ObservationOption;
import com.google.android.apps.forscience.whistlepunk.RecordFragment.CallbacksProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaItem;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView.ActionAreaListener;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciPictureLabelValue.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsWithActionAreaFragment;
import com.tbruyelle.rxpermissions2.RxPermissions;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;
import java.util.UUID;

/** Displays the experiment and the action bar. */
public class ExperimentActivity extends AppCompatActivity
    implements CallbacksProvider,
        ListenerProvider,
        TextToolFragment.ListenerProvider,
        GalleryFragment.ListenerProvider,
        ExperimentDetailsWithActionAreaFragment.ListenerProvider,
        ActionAreaListener {
  private static final String TAG = "ExperimentActivity";
  public static final String EXTRA_ACCOUNT_KEY = "accountKey";
  public static final String EXTRA_EXPERIMENT_ID = "experimentId";
  public static final String EXTRA_CLAIM_EXPERIMENTS_MODE = "claimExperimentsMode";

  private static final String EXTRA_PICTURE_UUID = "pictureUUID";
  private static final String EXTRA_PICTURE_PATH = "picturePath";

  private ProgressBar recordingBar;
  private RxPermissions permissions;
  private RxEvent paused = new RxEvent();
  private AppAccount appAccount;
  private boolean claimExperimentsMode;
  private String pictureUUID;
  private String pictureRelativePath;

  @NonNull
  public static Intent launchIntent(
      Context context, AppAccount appAccount, String experimentId, boolean claimExperimentsMode) {
    Intent intent = new Intent(context, ExperimentActivity.class);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    intent.putExtra(EXTRA_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
    return intent;
  }

  private ExperimentDetailsWithActionAreaFragment experimentFragment = null;

  // SingleSubject remembers the loaded value (if any) and delivers it to any observers.
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
    setContentView(R.layout.activity_experiment_layout);

    recordingBar = findViewById(R.id.recording_progress_bar);

    appAccount = WhistlePunkApplication.getAccount(this, getIntent(), EXTRA_ACCOUNT_KEY);
    String experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);

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
    WhistlePunkApplication.getUsageTracker(this).trackScreenView(TrackerConstants.SCREEN_PANES);
  }

  @VisibleForTesting
  public static Single<Experiment> whenSelectedExperiment(
      String experimentId, DataController dataController) {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, "Launching specified experiment id: " + experimentId);
    }
    return RxDataController.getExperimentById(dataController, experimentId);
  }

  public void onArchivedStateChanged() {
    findViewById(R.id.experiment_pane).requestLayout();
  }

  private void setExperimentFragmentId(Experiment experiment) {
    FragmentManager fragmentManager = getSupportFragmentManager();

    if (experimentFragment == null) {
      // If we haven't cached the fragment, go looking for it.
      ExperimentDetailsWithActionAreaFragment oldFragment =
          (ExperimentDetailsWithActionAreaFragment)
              fragmentManager.findFragmentById(R.id.experiment_pane);
      if (oldFragment != null
          && oldFragment.getExperimentId().equals(experiment.getExperimentId())) {
        experimentFragment = oldFragment;
        return;
      }
    }

    if (experimentFragment == null) {
      boolean createTaskStack = true;
      experimentFragment =
          ExperimentDetailsWithActionAreaFragment.newInstance(
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
    if (appSingleton.getAndClearMostRecentOpenWasImport()) {
      AccessibilityUtils.makeSnackbar(
              findViewById(R.id.experiment_pane),
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
      logState(TrackerConstants.ACTION_PAUSED);
    }
    super.onStop();
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
        logState(TrackerConstants.ACTION_RECORDED);
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
      }

      @Override
      void onRecordingStopped() {
        hideRecordingBar();
        experimentFragment.onStopRecording();
      }

      @Override
      void maximizeFragment() {

      }
    };
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
        return ExperimentActivity.this.getActiveExperimentId();
      }
    };
  }

  @Override
  public GalleryFragment.Listener getGalleryListener() {
    return new GalleryFragment.Listener() {
      @Override
      public Observable<String> getActiveExperimentId() {
        return ExperimentActivity.this.getActiveExperimentId();
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

  @SuppressLint("CheckResult")
  private void addNewLabel(Label label) {
    // Get the most recent experiment, or wait if none has been loaded yet.
    activeExperiment.subscribe(
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
        });
  }

  private void onAddNewLabelFailed() {
    AccessibilityUtils.makeSnackbar(
            findViewById(R.id.experiment_pane),
            getResources().getString(R.string.label_failed_save),
            Snackbar.LENGTH_LONG)
        .show();
  }

  private void onLabelAdded(String trialId) {
    logState(TrackerConstants.ACTION_LABEL_ADDED);
    if (TextUtils.isEmpty(trialId)) {
      // TODO: is this expensive?  Should we trigger a more incremental update?
      experimentFragment.reloadAndScrollToBottom();
    } else {
      experimentFragment.onRecordingTrialUpdated(trialId);
    }
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(ExperimentActivity.this).getDataController(appAccount);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void showRecordingBar() {
    if (recordingBar != null) {
      recordingBar.setVisibility(View.VISIBLE);
    }
  }

  private void hideRecordingBar() {
    if (recordingBar != null) {
      recordingBar.setVisibility(View.GONE);
    }
  }

  private Observable<String> getActiveExperimentId() {
    return activeExperiment.map(e -> e.getExperimentId()).toObservable();
  }

  @Override
  public ExperimentDetailsWithActionAreaFragment.Listener getExperimentDetailsFragmentListener() {
    return changed -> ExperimentActivity.this.onArchivedStateChanged();
  }

  @Override
  public void onClick(ActionAreaItem item) {
    if (item.equals(ActionAreaItem.NOTE)) {
      Log.v(TAG, "clicked note");
    } else if (item.equals(ActionAreaItem.SENSOR)) {
      Log.v(TAG, "clicked sensor");
    } else if (item.equals(ActionAreaItem.CAMERA)) {
      Log.v(TAG, "clicked camera");
      takePicture();
    } else if (item.equals(ActionAreaItem.GALLERY)) {
      Log.v(TAG, "clicked gallery");
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(EXTRA_PICTURE_UUID, pictureUUID);
    outState.putString(EXTRA_PICTURE_PATH, pictureRelativePath);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    pictureUUID = savedInstanceState.getString(EXTRA_PICTURE_UUID);
    pictureRelativePath = savedInstanceState.getString(EXTRA_PICTURE_PATH);
  }

  @SuppressLint("CheckResult")
  private void takePicture() {
    if (permissions != null) {
      permissions
          .request(Manifest.permission.CAMERA)
          .subscribe(
              granted -> {
                if (granted) {
                  pictureUUID = UUID.randomUUID().toString();
                  // After a user takes a picture, onActivityResult will be called.
                  pictureRelativePath =
                      PictureUtils.capturePictureLabel(
                          this,
                          appAccount,
                          activeExperiment.getValue().getExperimentId(),
                          pictureUUID);
                } else {
                  AccessibilityUtils.makeSnackbar(
                          findViewById(R.id.experiment_pane),
                          getResources().getString(R.string.input_camera_permission_denied),
                          Snackbar.LENGTH_LONG)
                      .show();
                }
              });
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
      if (pictureUUID != null && pictureRelativePath != null) {
        GoosciPictureLabelValue.PictureLabelValue labelValue = new PictureLabelValue();
        labelValue.filePath = pictureRelativePath;
        Label label =
            Label.fromUuidAndValue(
                AppSingleton.getInstance(this).getSensorEnvironment().getDefaultClock().getNow(),
                pictureUUID,
                GoosciLabel.Label.ValueType.PICTURE,
                labelValue);
        addNewLabel(label);
      }
    }
  }

  private void openGallery() {
    // TODO(b/137214973): open gallery fragment
  }

  private void openDraw() {
    // TODO(b/112632194): start ink activity here
  }

  private void openVelocityTracker() {
    // TODO(b/135678092): start velocity tracker activity here
  }

  ObservationOption[] getMoreObservationOptions() {
    // TODO(b/132651474): make this work depending on what is available (draw, velocity, etc)
    ObservationOption[] moreObservationOptions = {
      new ObservationOption(
          R.string.more_observations_gallery_title,
          R.string.more_observations_gallery_description,
          R.drawable.more_observations_gallery,
          (View view) -> openGallery()),
      new ObservationOption(
          R.string.more_observations_draw_title,
          R.string.more_observations_draw_description,
          R.drawable.more_observations_draw,
          (View view) -> openDraw()),
      new ObservationOption(
          R.string.more_observations_velocity_tracker_title,
          R.string.more_observations_velocity_tracker_description,
          R.drawable.more_observations_velocity_tracker,
          (View view) -> openVelocityTracker())
    };
    return moreObservationOptions;
  }
}
