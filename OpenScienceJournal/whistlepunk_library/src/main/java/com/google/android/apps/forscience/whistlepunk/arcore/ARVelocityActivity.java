/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.arcore;

import static com.google.android.apps.forscience.whistlepunk.ExperimentActivity.EXTRA_ACCOUNT_KEY;
import static com.google.android.apps.forscience.whistlepunk.ExperimentActivity.EXTRA_EXPERIMENT_ID;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.ControlBarController;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.SnackbarManager;
import com.google.android.apps.forscience.whistlepunk.Snapshotter;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.sensors.VelocitySensor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImage.TrackingMethod;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Activity that allows user to measure velocity of a ARCore tracked object. */
public class ARVelocityActivity extends AppCompatActivity {
  private static final String TAG = "ARVelocity";
  private ArFragment arFragment;
  private ImageView fitToScanView;
  private TextView velocityText;
  private static final float INTERVAL_TIME_SECONDS = 1f;
  private static final float TEXT_UPDATE_TIME_SECONDS = 0.25f;
  // TODO(b/139936569): When ARCore SDK is updated to 1.11+, change this constant accordingly
  private static final int INTERVAL_FRAMES = 30;
  private float delTime;
  private float textUpdateTime;
  private float totalDistance;
  private Vector3 lastPos;
  private AppAccount appAccount;
  private AppSingleton singleton;
  private String experimentId;
  private ImageButton snapshotButton;
  private ImageButton recordButton;
  private RecorderController rc;
  private ControlBarController cbc;
  private String observerId;
  private VelocitySensor velocitySensor;
  private List<Vector3> positions;
  private int currIndex; // Index of next empty place in position list

  // Augmented images that are currently being tracked.
  private final Set<AugmentedImage> augmentedImageSet = new HashSet<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_ar);

    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    fitToScanView = findViewById(R.id.image_view_fit_to_scan);
    velocityText = findViewById(R.id.velocity_tracker_velocity_text);
    arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
    snapshotButton = findViewById(R.id.snapshot_button);
    recordButton = findViewById(R.id.record_button);
    appAccount = WhistlePunkApplication.getAccount(this, getIntent(), EXTRA_ACCOUNT_KEY);
    experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);
    singleton = AppSingleton.getInstance(this);
    rc = singleton.getRecorderController(appAccount);
    velocitySensor = singleton.getVelocitySensor();
    cbc = new ControlBarController(appAccount, experimentId, new SnackbarManager());
    positions = new ArrayList<>();

    setUpRecording();

    // Hiding button after previous method call, which sets visibility to true due to
    // ControlBarController's methods. We don't want the
    // button to be shown until the image tracking has started. Once Action Area is completed, we
    // probably won't be using ControlBarController and should revisit this then.
    recordButton.setVisibility(View.GONE);
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (augmentedImageSet.isEmpty()) {
      fitToScanView.setVisibility(View.VISIBLE);
      velocityText.setVisibility(View.INVISIBLE);
    }
  }

  @Override
  protected void onStop() {
    if (rc != null) {
      rc.stopObserving(VelocitySensor.ID, observerId);
    }
    super.onStop();
  }

  public static Intent getIntent(Context context, AppAccount appAccount, String experimentId) {
    Intent intent = new Intent(context, ARVelocityActivity.class);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    return intent;
  }

  private void setUpRecording() {

    // Note: this will probably change when we have an on-screen graph.
    observerId = rc.startObserving(
        VelocitySensor.ID,
        new ArrayList<>(),
        (timestamp, value) -> {},
        null,
        null,
        singleton.getSensorRegistry());

    RxDataController.getExperimentById(singleton.getDataController(appAccount), experimentId)
        .subscribe(
            experiment -> {
              rc.setSelectedExperiment(experiment);
              attachSnapshotButton(snapshotButton);
              cbc.attachRecordButton(recordButton, getSupportFragmentManager());
            },
            error -> {
              if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "setUpRecording in ARVelocityActivity failed", error);
              }
              throw new IllegalStateException("ARVelocityActivity setupRecording failed", error);
            });
    rc.setLayoutSupplier(() -> buildLayouts());
  }

  private void onUpdateFrame(FrameTime frameTime) {
    Frame frame = arFragment.getArSceneView().getArFrame();

    // If there is no frame or ARCore is not tracking yet, just return.
    if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
      return;
    }

    Collection<AugmentedImage> updatedAugmentedImages =
        frame.getUpdatedTrackables(AugmentedImage.class);
    for (AugmentedImage augmentedImage : updatedAugmentedImages) {
      switch (augmentedImage.getTrackingState()) {
        case PAUSED:
          // When an image is in the PAUSED state, it has been detected, but not yet tracked.
          velocityText.setVisibility(View.VISIBLE);
          velocityText.setText(getResources().getString(R.string.ar_detecting_image));
          break;

        case TRACKING:
          if (augmentedImage.getTrackingMethod() == TrackingMethod.FULL_TRACKING) {
            fitToScanView.setVisibility(View.GONE);

            // Create a new anchor for newly found images.
            if (!augmentedImageSet.contains(augmentedImage)) {
              arFragment.getArSceneView().getSession().createAnchor(augmentedImage.getCenterPose());
              augmentedImageSet.add(augmentedImage);
            }
            averageVelocityEveryFrame(augmentedImage.getCenterPose(), frameTime.getDeltaSeconds());
            snapshotButton.setVisibility(View.VISIBLE);
            recordButton.setVisibility(View.VISIBLE);
          } else {
            lastPos = null;
            positions = new ArrayList<>();
            currIndex = 0;
            velocityText.setText(getResources().getString(R.string.ar_not_tracking));
            snapshotButton.setVisibility(View.GONE);
            recordButton.setVisibility(View.GONE);
          }
          break;

        case STOPPED:
          velocityText.setVisibility(View.INVISIBLE);
          augmentedImageSet.remove(augmentedImage);
          break;
      }
    }
  }

  // TODO(b/140589451): pull the math logic out into a class that just knows about positions
  //  vectors and timestamps, in order to write unit tests to demonstrate the differences between
  //  each method of velocity calculation

  // Every interval, calculate the velocity over the past interval, record and report this value.
  // Use this method for the simplest definition of "velocity" with least data points on graph.
  private void calculateVelocity(Pose centerPose, float deltaSeconds) {
    Pose anchorPose = arFragment.getArSceneView().getSession().getAllAnchors().iterator().next()
        .getPose();
    Vector3 currPos =
        new Vector3(
            centerPose.tx() - anchorPose.tx(),
            centerPose.ty() - anchorPose.ty(),
            centerPose.tz() - anchorPose.tz());
    delTime += deltaSeconds;

    if (lastPos == null) {
      lastPos = currPos;
    } else if (delTime >= INTERVAL_TIME_SECONDS) {
      // Calculate velocity in meters per second.
      Vector3 displacement = Vector3.subtract(currPos, lastPos);
      float velocityValue = displacement.length() / delTime;
      velocitySensor.setNextVelocity(velocityValue);
      // TODO(b/135678092): Add a string resource for the following
      velocityText.setText(String.format(Locale.getDefault(), "%.2f m/s", velocityValue));
      delTime = 0;
      lastPos = currPos;
    }
  }

  // Every frame, calculate the velocity over the past frame, record this value. Current FPS is 30.
  // Report this value (update text) based on a text update interval time.
  // Use this method for maximum data points and a calculation closer to instantaneous velocity.
  // Note that this tends to have a less smooth graph.
  private void calculateVelocityEveryFrame(Pose centerPose, float deltaSeconds) {
    Pose anchorPose =
        arFragment.getArSceneView().getSession().getAllAnchors().iterator().next().getPose();
    Vector3 currPos =
        new Vector3(
            centerPose.tx() - anchorPose.tx(),
            centerPose.ty() - anchorPose.ty(),
            centerPose.tz() - anchorPose.tz());
    textUpdateTime += deltaSeconds;

    if (lastPos != null) {
      // Calculate velocity in meters per second.
      Vector3 displacement = Vector3.subtract(currPos, lastPos);
      float velocityValue = displacement.length() / deltaSeconds;
      velocitySensor.setNextVelocity(velocityValue);

      if (textUpdateTime >= TEXT_UPDATE_TIME_SECONDS) {
        // TODO(b/135678092): Add a string resource for the following
        velocityText.setText(String.format(Locale.getDefault(), "%.2f m/s", velocityValue));
        textUpdateTime = 0;
      }
    }
    lastPos = currPos;
  }

  // Every frame, calculate the velocity over the past interval, and record this value.
  // Report this value (update text) based on a text update interval time.
  // Use this method for maximum data points and a calculation closer to average velocity.
  // This method tends to have a smoother graph than calculateVelocityEveryFrame, and more data
  // points on the graph, so this is the currently used method.
  private void averageVelocityEveryFrame(Pose centerPose, float deltaSeconds) {
    Pose anchorPose =
        arFragment.getArSceneView().getSession().getAllAnchors().iterator().next().getPose();
    Vector3 currPos =
        new Vector3(
            centerPose.tx() - anchorPose.tx(),
            centerPose.ty() - anchorPose.ty(),
            centerPose.tz() - anchorPose.tz());
    positions.add(currPos);
    currIndex++;
    textUpdateTime += deltaSeconds;

    if (currIndex >= INTERVAL_FRAMES) {
      // Calculate velocity over the past second.
      Vector3 displacement = Vector3.subtract(currPos, positions.get(currIndex - INTERVAL_FRAMES));
      float velocityValue = displacement.length() / INTERVAL_TIME_SECONDS;
      velocitySensor.setNextVelocity(velocityValue);

      if (textUpdateTime >= TEXT_UPDATE_TIME_SECONDS) {
        // TODO(b/135678092): Add a string resource for the following
        velocityText.setText(String.format(Locale.getDefault(), "%.2f m/s", velocityValue));
        textUpdateTime = 0;
      }
    }
  }

  // Every interval, calculate the speed over the past interval, record and report this value.
  // Speed is calculated by adding the distance gained every frame, and dividing total distance over
  // the interval by interval time.
  // Use this method for the simplest definition of "speed" with least data points on graph.
  // This is the "speed" equivalent of the calculateVelocity method.
  private void calculateSpeed(Pose centerPose, float deltaSeconds) {
    Pose anchorPose =
        arFragment.getArSceneView().getSession().getAllAnchors().iterator().next().getPose();
    Vector3 currPos =
        new Vector3(
            centerPose.tx() - anchorPose.tx(),
            centerPose.ty() - anchorPose.ty(),
            centerPose.tz() - anchorPose.tz());
    delTime += deltaSeconds;

    if (lastPos != null) {
      float distance = Vector3.subtract(currPos, lastPos).length();
      totalDistance += distance;
    }
    lastPos = currPos;

    if (delTime >= INTERVAL_TIME_SECONDS) {
      // Calculate velocity in meters per second.
      float speedValue = totalDistance / delTime;
      velocitySensor.setNextVelocity(speedValue);
      // TODO(b/135678092): Add a string resource for the following
      velocityText.setText(String.format(Locale.getDefault(), "%.2f m/s", speedValue));
      delTime = 0;
      totalDistance = 0;
    }
  }

  @NonNull
  public List<SensorLayoutPojo> buildLayouts() {
    List<SensorLayoutPojo> layouts = new ArrayList<>();
    layouts.add(velocitySensor.buildLayout());
    return layouts;
  }

  public void attachSnapshotButton(View snapshotButton) {
    // Pass in the velocity ID to take a snapshot of just the velocity sensor.
    List<String> ids = new ArrayList<>();
    ids.add(VelocitySensor.ID);

    snapshotButton.setOnClickListener(
        v -> {
          Snapshotter snapshotter =
              new Snapshotter(
                  singleton.getRecorderController(appAccount),
                  singleton.getDataController(appAccount),
                  singleton.getSensorRegistry());
          singleton
              .getRecorderController(appAccount)
              .watchRecordingStatus()
              .firstElement()
              .flatMapSingle(status -> snapshotter.addSnapshotLabel(experimentId, status, ids))
              .subscribe(
                  (Label label) -> singleton.onLabelsAdded().onNext(label),
                  (Throwable e) -> {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                      Log.d(TAG, Throwables.getStackTraceAsString(e));
                    }
                  });
        });
    TooltipCompat.setTooltipText(
        snapshotButton, getResources().getString(R.string.snapshot_button_description));
  }
}
